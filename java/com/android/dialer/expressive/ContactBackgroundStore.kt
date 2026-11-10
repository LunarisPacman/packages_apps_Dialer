/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.dialer.expressive

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class CallBackgroundType {
    VIDEO,
    GIF,
    IMAGE
}

data class ContactBackground(val file: File, val type: CallBackgroundType)

fun detectMediaType(file: File?, mimeType: String? = null): CallBackgroundType {
    if (file == null || !file.exists()) return CallBackgroundType.IMAGE
    if (mimeType != null) {
        if (mimeType.startsWith("video/")) return CallBackgroundType.VIDEO
        if (mimeType == "image/gif") return CallBackgroundType.GIF
    }
    val name = file.name.lowercase(Locale.ROOT)
    if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") ||
        name.endsWith(".3gp") || name.endsWith(".mov") || name.endsWith(".avi")) {
        return CallBackgroundType.VIDEO
    }
    if (name.endsWith(".gif")) {
        return CallBackgroundType.GIF
    }
    // Inspect magic bytes if extension is ambiguous or generic
    runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(12)
            val count = input.read(header)
            if (count >= 3 && header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte()) {
                return CallBackgroundType.GIF
            }
            if (count >= 8 && header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
                header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()) {
                return CallBackgroundType.VIDEO
            }
            if (count >= 4 && header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
                header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()) {
                return CallBackgroundType.VIDEO
            }
        }
    }
    return CallBackgroundType.IMAGE
}

class ContactBackgroundStore(context: Context) {
    private val context = context.applicationContext
    internal val preferences = this.context.getSharedPreferences("contact_backgrounds", Context.MODE_PRIVATE)
    private val directory get() = File(context.filesDir, "contact_backgrounds")

    internal fun key(number: String): String? {
        val network = PhoneNumberUtils.normalizeNumber(PhoneNumberUtils.extractNetworkPortion(number))
        if (network.isBlank()) return null
        val country = context.getSystemService(TelephonyManager::class.java)?.simCountryIso
            ?.takeIf { it.isNotBlank() } ?: Locale.getDefault().country
        val canonical = PhoneNumberUtils.formatNumberToE164(network, country.uppercase(Locale.ROOT)) ?: network
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    suspend fun save(number: String, uri: Uri, mimeType: String?) = withContext(Dispatchers.IO) {
        val key = requireNotNull(key(number))
        directory.mkdirs()
        val tempFile = File(directory, UUID.randomUUID().toString() + ".tmp")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not open input stream")

        val detectedType = detectMediaType(tempFile, mimeType)
        val ext = when (detectedType) {
            CallBackgroundType.VIDEO -> "mp4"
            CallBackgroundType.GIF -> "gif"
            CallBackgroundType.IMAGE -> "jpg"
        }
        val finalFile = File(directory, UUID.randomUUID().toString() + "." + ext)
        if (!tempFile.renameTo(finalFile)) {
            tempFile.copyTo(finalFile, overwrite = true)
            tempFile.delete()
        }

        val oldFileName = preferences.getString("${key}_file", null)
        preferences.edit()
            .putString("${key}_file", finalFile.name)
            .putString("${key}_type", detectedType.name)
            .putLong("${key}_timestamp", System.currentTimeMillis())
            .commit()

        if (oldFileName != null) {
            File(directory, oldFileName).delete()
        }
    }

    suspend fun remove(number: String) = withContext(Dispatchers.IO) {
        key(number)?.let { key ->
            val oldFileName = preferences.getString("${key}_file", null)
            preferences.edit()
                .remove("${key}_file")
                .remove("${key}_type")
                .remove("${key}_timestamp")
                .commit()
            if (oldFileName != null) {
                File(directory, oldFileName).delete()
            }
        }
    }

    fun get(number: String): ContactBackground? {
        val key = key(number) ?: return null
        val fileName = preferences.getString("${key}_file", null) ?: return null
        val typeName = preferences.getString("${key}_type", null)
        val file = File(directory, fileName)
        if (!file.exists() || file.length() == 0L) return null
        val type = typeName?.let {
            runCatching { CallBackgroundType.valueOf(it) }.getOrNull()
        } ?: detectMediaType(file)
        return ContactBackground(file, type)
    }
}

@Composable
internal fun rememberContactBackground(number: String): ContactBackground? {
    val context = LocalContext.current
    val store = remember(context) { ContactBackgroundStore(context) }
    val key = remember(store, number) { store.key(number) }
    var timestamp by remember(key) {
        mutableLongStateOf(key?.let { store.preferences.getLong("${it}_timestamp", 0L) } ?: 0L)
    }

    DisposableEffect(key) {
        if (key == null) return@DisposableEffect onDispose {}
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, prefKey ->
            if (prefKey == "${key}_timestamp" || prefKey == "${key}_file") {
                timestamp = store.preferences.getLong("${key}_timestamp", 0L)
            }
        }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return remember(key, timestamp) {
        if (key == null) null else store.get(number)
    }
}
