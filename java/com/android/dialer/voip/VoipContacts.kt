/* Copyright (C) 2026 Lunaris AOSP
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 */
package com.android.dialer.voip

import android.app.Notification
import android.app.Person
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import com.android.dialer.R
import com.android.dialer.expressive.RecentCall
import java.util.Locale

internal object VoipContacts {
    private val whatsApp = setOf("com.whatsapp", "com.whatsapp.w4b")
    private val telegram = setOf("org.telegram.messenger", "org.telegram.messenger.web")
    private val phoneCharacters = Regex("[+\\p{Nd}() .-]+")
    private val phoneJid = Regex("([1-9][0-9]{6,14})@(s\\.whatsapp\\.net|c\\.us)")

    fun supportsContact(packageName: String) = packageName in whatsApp || packageName in telegram

    @Suppress("DEPRECATION")
    fun notificationContact(context: Context, packageName: String, notification: Notification): String? {
        if (!supportsContact(packageName)) return null
        fun fromPerson(person: Person?): String? =
            address(context, packageName, person?.uri) ?: jidAddress(packageName, person?.key)
        return try {
            val extras = notification.extras
            fromPerson(extras.getParcelable(Notification.EXTRA_CALL_PERSON, Person::class.java))?.let { return it }
            val people = extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, Person::class.java)
            val addresses = if (!people.isNullOrEmpty()) people.map { fromPerson(it) }
                else extras.getStringArray(Notification.EXTRA_PEOPLE)?.map { address(context, packageName, it) }
            addresses?.takeIf { it.isNotEmpty() && null !in it }?.distinct()?.singleOrNull()
        } catch (_: RuntimeException) { null }
    }

    private fun jidAddress(packageName: String, value: String?): String? {
        if (packageName !in whatsApp || value == null) return null
        val number = phoneJid.matchEntire(value)?.groupValues?.get(1) ?: return null
        return "tel:+$number"
    }

    private fun address(context: Context, packageName: String, value: String?): String? {
        if (value.isNullOrBlank() || value.length > 2048) return null
        jidAddress(packageName, value)?.let { return it }
        val uri = Uri.parse(value)
        if (uri.scheme == "tel") return phoneAddress(context, uri.schemeSpecificPart)
        if (uri.scheme == "content" && uri.authority == ContactsContract.AUTHORITY &&
            uri.query == null && uri.fragment == null) {
            val path = uri.pathSegments
            if (path.firstOrNull() == "contacts" &&
                (path.size == 2 && path[1].toLongOrNull() != null ||
                    path.size in 3..4 && path[1] == "lookup")) return uri.toString()
        }
        return null
    }

    private fun phoneAddress(context: Context, number: String?): String? {
        val value = number?.trim()?.takeIf { phoneCharacters.matches(it) } ?: return null
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val country = try {
            telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
                ?: telephony?.simCountryIso?.takeIf { it.isNotBlank() }
        } catch (_: RuntimeException) { null }
        val normalized = PhoneNumberUtils.formatNumberToE164(value,
            (country ?: Locale.getDefault().country).uppercase(Locale.ROOT)) ?: return null
        return "tel:$normalized"
    }

    fun resolve(context: Context, call: RecentCall): String? {
        val packageName = call.voipPackage ?: return null
        if (!supportsContact(packageName)) return null
        address(context, packageName, call.voipContactUri)?.let { saved ->
            if (saved.startsWith("tel:")) return saved
            return contactNumber(context, Uri.parse(saved))
        }
        phoneAddress(context, call.name)?.let { return it }
        if (call.name.isBlank() || call.name in VoipCalls.apps.values) return null
        return uniquePhone(context, Phone.CONTENT_URI,
            "${Phone.DISPLAY_NAME_PRIMARY} = ? COLLATE NOCASE OR " +
                "${Phone.DISPLAY_NAME_ALTERNATIVE} = ? COLLATE NOCASE",
            arrayOf(call.name.trim(), call.name.trim()))
    }

    private fun contactNumber(context: Context, uri: Uri): String? = try {
        val id = context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID),
            null, null, null)?.use { cursor ->
            if (cursor.count == 1 && cursor.moveToFirst()) cursor.getLong(0) else null
        }
        id?.let { uniquePhone(context, Phone.CONTENT_URI, "${Phone.CONTACT_ID} = ?", arrayOf(it.toString())) }
    } catch (_: RuntimeException) { null }

    fun pickedContact(context: Context, uri: Uri): String? {
        if (uri.scheme != "content" || uri.authority != ContactsContract.AUTHORITY) return null
        return uniquePhone(context, uri, "${Phone.MIMETYPE} = ?", arrayOf(Phone.CONTENT_ITEM_TYPE))
    }

    private fun uniquePhone(context: Context, uri: Uri, selection: String, args: Array<String>): String? = try {
        context.contentResolver.query(uri, arrayOf(Phone.CONTACT_ID, Phone.NUMBER, Phone.NORMALIZED_NUMBER),
            selection, args, null)?.use { cursor ->
            val contacts = mutableSetOf<Long>()
            val numbers = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                contacts += cursor.getLong(0)
                val number = phoneAddress(context, cursor.getString(2))
                    ?: phoneAddress(context, cursor.getString(1)) ?: return null
                numbers += number
                if (contacts.size > 1 || numbers.size > 1) return null
            }
            numbers.singleOrNull()
        }
    } catch (_: RuntimeException) { null }

    fun open(context: Context, packageName: String, address: String) {
        if (!VoipCalls.enabled(context) || !supportsContact(packageName)) return
        val phone = Uri.parse(address).takeIf { it.scheme == "tel" }?.schemeSpecificPart
        if (phone == null || !Regex("\\+[1-9][0-9]{6,14}").matches(phone)) {
            failed(context)
            return
        }
        val uri = if (packageName in whatsApp) Uri.Builder().scheme("whatsapp").authority("send")
            .appendQueryParameter("phone", phone.removePrefix("+")).build()
        else Uri.Builder().scheme("tg").authority("resolve").appendQueryParameter("phone", phone).build()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(packageName).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (_: RuntimeException) { failed(context) }
    }

    fun call(context: Context, packageName: String, address: String, video: Boolean = false) {
        if (!VoipCalls.enabled(context)) return
        val phone = Uri.parse(address).takeIf { it.scheme == "tel" }?.schemeSpecificPart ?: address.trim()
        val clean = PhoneNumberUtils.normalizeNumber(PhoneNumberUtils.extractNetworkPortion(phone)).removePrefix("+")
        if (clean.isBlank()) {
            failed(context)
            return
        }

        if (packageName in whatsApp) {
            callWhatsApp(context, clean, video, packageName)
            return
        }

        if (packageName in telegram) {
            val uri = Uri.Builder().scheme("tg").authority("resolve").appendQueryParameter("phone", clean).build()
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(packageName).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (_: RuntimeException) { failed(context) }
            return
        }

        VoipCalls.openApp(context, packageName)
    }

    fun callWhatsApp(context: Context, number: String, video: Boolean, packageName: String = "com.whatsapp"): Boolean {
        val clean = PhoneNumberUtils.normalizeNumber(PhoneNumberUtils.extractNetworkPortion(number)).removePrefix("+")
        if (clean.isBlank()) {
            Toast.makeText(context, R.string.expressive_whatsapp_invalid_number, Toast.LENGTH_LONG).show()
            return false
        }

        val packages = if (packageName == "com.whatsapp.w4b") listOf("com.whatsapp.w4b", "com.whatsapp")
            else listOf("com.whatsapp", "com.whatsapp.w4b")

        // 1. Try ContactsContract.Data
        for (pkg in packages) {
            val dataInfo = findWhatsAppCallDataId(context, clean, video, pkg)
            if (dataInfo != null) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataInfo.first), dataInfo.second)
                        setPackage(pkg)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    Log.w("VoipContacts", "Failed to start WhatsApp call via Data URI", e)
                }
            }
        }

        // 2. Try TelecomManager placeCall with WhatsApp's PhoneAccountHandle
        try {
            val telecom = context.getSystemService(TelecomManager::class.java)
            val handle = telecom?.callCapablePhoneAccounts?.firstOrNull {
                it.componentName.packageName in packages
            }
            if (handle != null) {
                val uri = Uri.fromParts("tel", "+$clean", null)
                val extras = Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                    if (video) {
                        putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL)
                    }
                }
                telecom.placeCall(uri, extras)
                return true
            }
        } catch (e: Exception) {
            Log.w("VoipContacts", "Failed to place call via TelecomManager", e)
        }

        // 3. Fallback: Open WhatsApp conversation directly
        for (pkg in packages) {
            try {
                val uri = Uri.Builder().scheme("whatsapp").authority("send")
                    .appendQueryParameter("phone", clean).build()
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                return true
            } catch (_: Exception) {}
        }

        Toast.makeText(context, R.string.expressive_whatsapp_unavailable, Toast.LENGTH_LONG).show()
        return false
    }

    private fun findWhatsAppCallDataId(context: Context, cleanNumber: String, video: Boolean, packageName: String): Pair<Long, String>? {
        val isBusiness = packageName == "com.whatsapp.w4b"
        val targetMime = when {
            isBusiness && video -> "vnd.android.cursor.item/vnd.com.whatsapp.w4b.video.call"
            isBusiness -> "vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call"
            video -> "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
            else -> "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        }

        val projection = arrayOf(ContactsContract.Data._ID, ContactsContract.Data.DATA1)
        val selection = "${ContactsContract.Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(targetMime)

        var result: Pair<Long, String>? = null
        try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data._ID)
                val dataIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA1)
                while (cursor.moveToNext()) {
                    val data1 = cursor.getString(dataIndex) ?: continue
                    val dataNumber = data1.substringBefore("@").removePrefix("+")
                    if (dataNumber == cleanNumber || cleanNumber.endsWith(dataNumber) || dataNumber.endsWith(cleanNumber)) {
                        result = Pair(cursor.getLong(idIndex), targetMime)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("VoipContacts", "Failed to query whatsapp call data id", e)
        }
        return result
    }

    fun failed(context: Context) {
        Toast.makeText(context, R.string.voip_contact_failed, Toast.LENGTH_LONG).show()
    }
}
