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

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class CallerPhotoState(val image: ImageBitmap? = null, val hasOverride: Boolean = false)

class CallerPhotoStore(context: Context) {
    private val context = context.applicationContext
    internal val preferences = this.context.getSharedPreferences("caller_photos", Context.MODE_PRIVATE)
    private val directory get() = File(context.filesDir, "caller_photos")

    internal fun key(number: String, name: String = ""): String? {
        val network = PhoneNumberUtils.normalizeNumber(PhoneNumberUtils.extractNetworkPortion(number))
        if (network.isNotBlank()) {
            val country = context.getSystemService(TelephonyManager::class.java)?.simCountryIso
                ?.takeIf { it.isNotBlank() } ?: Locale.getDefault().country
            val canonical = PhoneNumberUtils.formatNumberToE164(network, country.uppercase(Locale.ROOT)) ?: network
            return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
        if (name.isNotBlank()) {
            return MessageDigest.getInstance("SHA-256").digest(("caller_name:" + name.trim()).toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
        return null
    }

    fun saveVoipAvatar(caller: String, bitmap: Bitmap) {
        if (caller.isBlank()) return
        try {
            val dir = File(context.filesDir, "voip_avatars")
            dir.mkdirs()
            val hash = MessageDigest.getInstance("SHA-256").digest(("caller_name:" + caller.trim()).toByteArray())
                .joinToString("") { "%02x".format(it) }
            val file = File(dir, "$hash.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        } catch (_: Exception) {}
    }

    fun loadCachedVoipAvatar(caller: String): ImageBitmap? = readPhoto {
        if (caller.isBlank()) return@readPhoto null
        val dir = File(context.filesDir, "voip_avatars")
        val hash = MessageDigest.getInstance("SHA-256").digest(("caller_name:" + caller.trim()).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = File(dir, "$hash.jpg")
        if (file.exists()) {
            BitmapFactory.decodeFile(file.path)?.asImageBitmap()
        } else null
    }

    suspend fun save(number: String, uri: Uri) = withContext(Dispatchers.IO) {
        val key = requireNotNull(key(number))
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val scale = minOf(1f, 1600f / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        directory.mkdirs()
        val file = File(directory, UUID.randomUUID().toString() + ".jpg")
        val old = preferences.getString(key, null)
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
            check(preferences.edit().putString(key, file.name).commit())
            old?.let { File(directory, it).delete() }
        } catch (error: Exception) {
            file.delete()
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun remove(number: String) = withContext(Dispatchers.IO) {
        key(number)?.let { key ->
            val old = preferences.getString(key, null)
            check(preferences.edit().remove(key).commit())
            old?.let { File(directory, it).delete() }
        }
    }

    internal fun canReadContacts(): Boolean = ContextCompat.checkSelfPermission(context,
        Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    internal suspend fun load(number: String, key: String, maxSize: Int, name: String = ""): CallerPhotoState = withContext(Dispatchers.IO) {
        val customName = preferences.getString(key, null)
        val custom = customName?.let { readPhoto {
            val path = File(directory, customName).path
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > maxSize * 2)
                options.inSampleSize *= 2
            BitmapFactory.decodeFile(path, options)?.asImageBitmap()
        } }
        CallerPhotoState(custom ?: loadContactPhoto(number, maxSize, name), hasOverride = customName != null)
    }

    private fun loadContactPhoto(number: String, maxSize: Int, name: String = ""): ImageBitmap? = readPhoto {
        if (!canReadContacts()) return@readPhoto loadCachedVoipAvatar(name)
        val network = PhoneNumberUtils.extractNetworkPortion(number)
        if (network.isNotBlank()) {
            val lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(network))
            val columns = if (maxSize <= 256) arrayOf(
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI, ContactsContract.PhoneLookup.PHOTO_URI)
            else arrayOf(ContactsContract.PhoneLookup.PHOTO_URI, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
            context.contentResolver.query(lookup, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    for (index in columns.indices) {
                        val uri = cursor.getString(index)?.takeIf { it.isNotBlank() } ?: continue
                        val bitmap = readPhoto {
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))) { decoder, info, _ ->
                                val scale = minOf(1f, maxSize.toFloat() / maxOf(info.size.width, info.size.height))
                                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                                    (info.size.height * scale).toInt().coerceAtLeast(1))
                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            }.asImageBitmap()
                        }
                        if (bitmap != null) return@readPhoto bitmap
                    }
                }
            }
        }
        // If not found by phone number, check cached VoIP avatar
        loadCachedVoipAvatar(name)?.let { return@readPhoto it }

        // Also check ContactsContract.Contacts by name if number was blank or had no photo
        if (name.isNotBlank()) {
            val filter = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(name.trim()))
            val columns = if (maxSize <= 256) arrayOf(
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI, ContactsContract.Contacts.PHOTO_URI)
            else arrayOf(ContactsContract.Contacts.PHOTO_URI, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            context.contentResolver.query(filter, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    for (index in columns.indices) {
                        val uri = cursor.getString(index)?.takeIf { it.isNotBlank() } ?: continue
                        val bitmap = readPhoto {
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))) { decoder, info, _ ->
                                val scale = minOf(1f, maxSize.toFloat() / maxOf(info.size.width, info.size.height))
                                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                                    (info.size.height * scale).toInt().coerceAtLeast(1))
                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            }.asImageBitmap()
                        }
                        if (bitmap != null) return@readPhoto bitmap
                    }
                }
            }
        }
        null
    }

    private inline fun readPhoto(block: () -> ImageBitmap?): ImageBitmap? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // Missing, unreadable, or corrupt photos fall back to the next source / initials.
        null
    }
}

@Composable
internal fun rememberCallerPhoto(number: String, maxSize: Int = 1600, name: String = ""): ImageBitmap? =
    rememberCallerPhotoState(number, maxSize, name).image

@Composable
internal fun rememberCallerPhotoState(number: String, maxSize: Int = 1600, name: String = ""): CallerPhotoState {
    val context = LocalContext.current
    val store = remember(context) { CallerPhotoStore(context) }
    val key = remember(store, number, name) { store.key(number, name) }
    var revision by remember { mutableIntStateOf(0) }
    var contactsAllowed by remember(store) { mutableStateOf(store.canReadContacts()) }
    val lifecycleOwner = LocalView.current.findViewTreeLifecycleOwner()
    DisposableEffect(store, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                contactsAllowed = store.canReadContacts()
                revision++
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    DisposableEffect(store, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key) revision++
        }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    DisposableEffect(context, key, contactsAllowed) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { revision++ }
        }
        val registered = if (key != null && contactsAllowed) try {
            context.contentResolver.registerContentObserver(ContactsContract.AUTHORITY_URI, true, observer)
            true
        } catch (_: SecurityException) { false } else false
        onDispose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }
    var photo by remember(store, key, maxSize, contactsAllowed) { mutableStateOf(CallerPhotoState()) }
    LaunchedEffect(store, number, name, key, revision, maxSize, contactsAllowed) {
        photo = key?.let { store.load(number, it, maxSize, name) } ?: CallerPhotoState()
    }
    return photo
}
