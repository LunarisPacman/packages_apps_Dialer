/* Copyright (C) 2026 Lunaris AOSP
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.android.dialer.voip

import android.Manifest
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.android.dialer.R
import com.android.dialer.expressive.*
import kotlinx.coroutines.delay

class VoipCallActivity : FragmentActivity() {
    private var recordingEnabled by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        if (!VoipCalls.enabled(this)) { finish(); return }
        recordingEnabled = VoipCalls.recordingEnabled(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent.getStringExtra(VoipNotificationListener.EXTRA_TOKEN)
        if (token == null || !VoipCalls.enabled(this)) { finish(); return }
        setShowWhenLocked(true)
        setTurnScreenOn(VoipCalls.find(token)?.incoming == true)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            DialerExpressiveTheme { CallScreen(token) }
        }
    }

    @Composable
    private fun CallScreen(token: String) {
        val calls by VoipCalls.calls.collectAsState()
        val call = calls.firstOrNull { it.token == token }
        val recording by VoipRecordingService.state.collectAsState()
        val ownRecording = recording.takeIf { it.token == token }
        val permissionRequest = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.isNotEmpty() && granted.values.all { it }) VoipRecordingService.start(this, token)
        }

        val audioManager = remember { getSystemService(AudioManager::class.java) }
        var isMuted by remember { mutableStateOf(audioManager?.isMicrophoneMute == true) }
        var isSpeakerOn by remember { mutableStateOf(isSpeakerActive(audioManager)) }

        DisposableEffect(audioManager) {
            if (audioManager == null) return@DisposableEffect onDispose {}
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    isMuted = audioManager.isMicrophoneMute
                    isSpeakerOn = isSpeakerActive(audioManager)
                }
            }
            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_MICROPHONE_MUTE_CHANGED)
                addAction(AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED)
                addAction(AudioManager.ACTION_HEADSET_PLUG)
            }
            ContextCompat.registerReceiver(this@VoipCallActivity, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

            val deviceListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val listener = AudioManager.OnCommunicationDeviceChangedListener { _ ->
                    isSpeakerOn = isSpeakerActive(audioManager)
                }
                audioManager.addOnCommunicationDeviceChangedListener(
                    ContextCompat.getMainExecutor(this@VoipCallActivity), listener)
                listener
            } else null

            onDispose {
                try { unregisterReceiver(receiver) } catch (_: Throwable) {}
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && deviceListener != null) {
                    try { audioManager.removeOnCommunicationDeviceChangedListener(deviceListener) } catch (_: Throwable) {}
                }
            }
        }

        LaunchedEffect(call == null) {
            if (call == null) {
                resetAudioState(audioManager)
            }
        }

        var elapsed by remember { mutableLongStateOf(0L) }
        LaunchedEffect(ownRecording?.busy, ownRecording?.started, ownRecording?.saving) {
            while (ownRecording?.busy == true && ownRecording.started > 0 && !ownRecording.saving) {
                elapsed = (SystemClock.elapsedRealtime() - ownRecording.started) / 1000
                delay(1000)
            }
        }

        var callElapsed by remember { mutableLongStateOf(0L) }
        LaunchedEffect(call?.ongoing, call?.date) {
            while (call?.ongoing == true && call.date > 0) {
                callElapsed = ((System.currentTimeMillis() - call.date) / 1000).coerceAtLeast(0)
                delay(1000)
            }
        }

        val callerStyle = rememberCallerScreenStyle()
        val hyperOs = callerStyle == CALLER_STYLE_HYPEROS
        val nosRedesign = callerStyle == CALLER_STYLE_NOS_REDESIGN
        val fullScreenCaller = callerStyle == CALLER_STYLE_FULLSCREEN || hyperOs || nosRedesign

        // 1. Resolve Contact Photo (DP): WhatsApp DP from notification -> ContactsContract -> CallerPhotoStore
        val number = remember(call?.contactUri) {
            call?.contactUri?.takeIf { it.startsWith("tel:") }?.removePrefix("tel:").orEmpty()
        }
        val contactPhoto = rememberCallerPhoto(number, name = call?.caller.orEmpty())
        val contentPhoto = remember(call?.contactUri) {
            if (call?.contactUri?.startsWith("content:") == true) {
                try {
                    val stream = contentResolver.openInputStream(Uri.parse(call.contactUri))
                    stream?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                } catch (_: Throwable) { null }
            } else null
        }
        val photo: ImageBitmap? = remember(call?.avatar, contentPhoto, contactPhoto) {
            call?.avatar?.asImageBitmap() ?: contentPhoto ?: contactPhoto
        }

        // 2. Resolve App Icon of where the call is coming from
        val appIconDrawable = remember(call?.packageName) {
            call?.packageName?.let { pkg ->
                try {
                    packageManager.getApplicationIcon(pkg)
                } catch (_: Throwable) { null }
            }
        }
        val appIconBitmap = remember(appIconDrawable) {
            drawableToBitmapSafe(appIconDrawable)?.asImageBitmap()
        }

        val callerForeground = if (nosRedesign) Color.White
            else if (hyperOs) rememberHyperOsForeground(photo, false)
            else MaterialTheme.colorScheme.onSurface

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = callerForeground
        ) {
            Box(Modifier.fillMaxSize()) {
                if (nosRedesign) {
                    NosRedesignCallerBackground(photo, Modifier.fillMaxSize())
                } else if (hyperOs) {
                    HyperOsCallerBackground(photo, Modifier.fillMaxSize())
                } else if (fullScreenCaller) {
                    CallerPortrait(photo, Modifier.fillMaxSize())
                }

                BoxWithConstraints(
                    Modifier.fillMaxSize().safeDrawingPadding()
                ) {
                    val availableHeight = maxHeight
                    val compactIdentity = availableHeight < 560.dp
                    val topInset = if ((hyperOs || nosRedesign) && !compactIdentity)
                        (availableHeight * .12f).coerceIn(0.dp, 80.dp) else 0.dp
                    val avatarSize = minOf(if (compactIdentity) 96.dp else 144.dp, maxWidth / 2.6f)

                    CallerContentLayout(
                        viewportHeight = (availableHeight - 24.dp - topInset).coerceAtLeast(0.dp),
                        centerOffset = (-20).dp,
                        identityAtTop = fullScreenCaller || compactIdentity,
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .padding(top = topInset),
                        header = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                FilledTonalIconButton(onClick = { finish() }) {
                                    Symbol(DialerSymbol.Back, stringResource(R.string.expressive_back))
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = if (nosRedesign) Color(0xFF1E1E1E).copy(alpha = 0.85f)
                                           else if (fullScreenCaller) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f)
                                           else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                                    contentColor = if (nosRedesign) Color.White else MaterialTheme.colorScheme.onSurface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (appIconBitmap != null) {
                                            Image(
                                                bitmap = appIconBitmap,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Text(
                                            text = call?.appName ?: stringResource(R.string.voip_calls_title),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                                Spacer(Modifier.size(48.dp))
                            }
                        },
                        identity = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (nosRedesign) {
                                    // Caller Name at top in Serif font
                                    Text(
                                        text = call?.caller ?: stringResource(R.string.voip_call_ended),
                                        style = if (compactIdentity) MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal)
                                               else MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 32.sp),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        color = Color.White
                                    )

                                    Spacer(Modifier.height(if (compactIdentity) 16.dp else 36.dp))

                                    // Avatar (DP) with Badged App Icon (Clean, no breathing effects)
                                    Box(
                                        contentAlignment = Alignment.BottomEnd,
                                        modifier = Modifier.size(if (compactIdentity) 100.dp else 160.dp)
                                    ) {
                                        NosRedesignCallerAvatar(photo, Modifier.fillMaxSize())

                                        // Small App Icon Badge
                                        if (appIconBitmap != null) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFF1E1E1E),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                                modifier = Modifier
                                                    .size(if (compactIdentity) 32.dp else 40.dp)
                                                    .offset(x = 2.dp, y = 2.dp)
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.padding(5.dp)
                                                ) {
                                                    Image(
                                                        bitmap = appIconBitmap,
                                                        contentDescription = call?.appName,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (call == null || call.incoming) {
                                        Spacer(Modifier.height(if (compactIdentity) 14.dp else 24.dp))
                                        val statusText = if (call == null) stringResource(R.string.voip_call_ended) else stringResource(R.string.voip_incoming)
                                        Text(
                                            statusText,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontFamily = NdotFontFamily,
                                                fontSize = 20.sp,
                                                letterSpacing = 2.5.sp
                                            ),
                                            color = Color.White.copy(alpha = 0.90f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    // Avatar (DP) with Badged App Icon
                                    Box(
                                        contentAlignment = Alignment.BottomEnd,
                                        modifier = Modifier.size(avatarSize)
                                    ) {
                                        if (hyperOs) {
                                            HyperOsCallerAvatar(photo, Modifier.fillMaxSize())
                                        } else {
                                            RoundedCallerAvatar(photo, Modifier.fillMaxSize())
                                        }

                                        // Small App Icon Badge
                                        if (appIconBitmap != null) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surface,
                                                tonalElevation = 6.dp,
                                                shadowElevation = 4.dp,
                                                modifier = Modifier
                                                    .size(if (compactIdentity) 34.dp else 42.dp)
                                                    .offset(x = 4.dp, y = 4.dp)
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.padding(5.dp)
                                                ) {
                                                    Image(
                                                        bitmap = appIconBitmap,
                                                        contentDescription = call?.appName,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(if (compactIdentity) 14.dp else 22.dp))

                                    // Caller Name
                                    Text(
                                        text = call?.caller ?: stringResource(R.string.voip_call_ended),
                                        style = if (compactIdentity) MaterialTheme.typography.headlineSmallEmphasized
                                               else MaterialTheme.typography.displaySmallEmphasized,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                    )

                                    Spacer(Modifier.height(12.dp))

                                    // Status Pill
                                    Surface(
                                        shape = CircleShape,
                                        color = if (ownRecording?.busy == true) MaterialTheme.colorScheme.errorContainer
                                               else MaterialTheme.colorScheme.primary,
                                        contentColor = if (ownRecording?.busy == true) MaterialTheme.colorScheme.onErrorContainer
                                               else MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (ownRecording?.busy == true) {
                                                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                                                Text(
                                                    stringResource(R.string.voip_recording_duration, DateUtils.formatElapsedTime(elapsed)),
                                                    style = MaterialTheme.typography.labelLargeEmphasized
                                                )
                                            } else {
                                                val statusText = when {
                                                    call == null -> stringResource(R.string.voip_call_ended)
                                                    call.incoming -> stringResource(R.string.voip_incoming)
                                                    callElapsed > 0 -> "${stringResource(R.string.voip_app_call)} · ${DateUtils.formatElapsedTime(callElapsed)}"
                                                    else -> stringResource(R.string.voip_app_call)
                                                }
                                                Text(statusText, style = MaterialTheme.typography.labelLargeEmphasized)
                                            }
                                        }
                                    }
                                }

                                if (ownRecording?.busy == true && !ownRecording.remoteAudioDetected && !ownRecording.saving) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.voip_waiting_audio),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                if (ownRecording != null && ownRecording.message != 0) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(ownRecording.message),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        },
                        actions = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (call == null) {
                                    // Call Ended Controls
                                    ExpressiveTile(
                                        onClick = { finish() },
                                        modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth().heightIn(min = 60.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ) {
                                        Symbol(DialerSymbol.Back)
                                        Text(
                                            stringResource(R.string.expressive_back),
                                            modifier = Modifier.padding(start = 10.dp),
                                            style = MaterialTheme.typography.titleMediumEmphasized
                                        )
                                    }
                                    TextButton(onClick = {
                                        startActivity(Intent(this@VoipCallActivity, VoipRecordingsActivity::class.java))
                                    }) {
                                        Symbol(DialerSymbol.History)
                                        Text(stringResource(R.string.voip_recordings_title), Modifier.padding(start = 8.dp))
                                    }
                                } else if (call.incoming) {
                                    // Incoming Call Controls: Decline and Answer
                                    IncomingVoipCallButtons(
                                        call = call,
                                        onAction = { action -> sendAction(call.token, action) }
                                    )
                                    OutlinedButton(
                                        onClick = { openApp(call) },
                                        modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().heightIn(min = 48.dp),
                                        shape = CircleShape
                                    ) {
                                        if (appIconBitmap != null) {
                                            Image(bitmap = appIconBitmap, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(stringResource(R.string.voip_open_app, call.appName))
                                    }
                                } else if (nosRedesign) {
                                    val buttonSize = 66.dp
                                    val buttonSpacing = 14.dp
                                    val endButtonWidth = buttonSize * 2 + buttonSpacing // 146.dp

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(buttonSpacing),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Timer / duration in Nothing dot-matrix font right above buttons
                                        val statusText = when {
                                            call == null -> stringResource(R.string.voip_call_ended)
                                            call.incoming -> stringResource(R.string.voip_incoming)
                                            callElapsed > 0 -> DateUtils.formatElapsedTime(callElapsed)
                                            else -> stringResource(R.string.voip_app_call)
                                        }
                                        Text(
                                            statusText,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontFamily = NdotFontFamily,
                                                fontSize = 20.sp,
                                                letterSpacing = 2.5.sp
                                            ),
                                            color = Color.White.copy(alpha = 0.92f),
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(8.dp))

                                        // Row 1: Open App, Record Call, Speaker (centered, 14.dp gap)
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(buttonSpacing, Alignment.CenterHorizontally),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            NosRedesignCallButton(
                                                symbol = DialerSymbol.Phone,
                                                label = stringResource(R.string.voip_open_app, call.appName),
                                                onClick = { openApp(call) },
                                                size = buttonSize
                                            )
                                            if (recordingEnabled) {
                                                val isRecording = ownRecording?.busy == true
                                                NosRedesignCallButton(
                                                    symbol = if (isRecording) DialerSymbol.Stop else DialerSymbol.Record,
                                                    label = stringResource(if (isRecording) R.string.voip_stop_recording else R.string.voip_start_recording),
                                                    onClick = {
                                                        if (isRecording) VoipRecordingService.stop(this@VoipCallActivity)
                                                        else {
                                                            val missing = arrayOf(
                                                                Manifest.permission.RECORD_AUDIO,
                                                                Manifest.permission.POST_NOTIFICATIONS
                                                            ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                                                            if (missing.isEmpty()) VoipRecordingService.start(this@VoipCallActivity, token)
                                                            else permissionRequest.launch(missing.toTypedArray())
                                                        }
                                                    },
                                                    selected = isRecording,
                                                    enabled = ownRecording?.saving != true && (isRecording || (!recording.busy && calls.size == 1)),
                                                    size = buttonSize
                                                )
                                            }
                                            NosRedesignCallButton(
                                                symbol = DialerSymbol.Speaker,
                                                label = stringResource(R.string.expressive_speaker),
                                                onClick = {
                                                    val newSpeaker = !isSpeakerOn
                                                    setSpeakerActive(audioManager, newSpeaker)
                                                    isSpeakerOn = isSpeakerActive(audioManager)
                                                    call.actions.firstOrNull {
                                                        it.title.contains("speaker", true)
                                                    }?.let { sendAction(call.token, it) }
                                                },
                                                selected = isSpeakerOn,
                                                size = buttonSize
                                            )
                                        }

                                        // Row 2: Mute (circle), End Call (capsule)
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(buttonSpacing, Alignment.CenterHorizontally),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            NosRedesignCallButton(
                                                symbol = if (isMuted) DialerSymbol.MicOff else DialerSymbol.Mic,
                                                label = stringResource(R.string.expressive_mute),
                                                onClick = {
                                                    val newMute = !isMuted
                                                    setMicMuted(audioManager, newMute)
                                                    isMuted = audioManager?.isMicrophoneMute == true
                                                    call.actions.firstOrNull {
                                                        it.title.contains("mute", true) || it.title.contains("mic", true)
                                                    }?.let { sendAction(call.token, it) }
                                                },
                                                selected = isMuted,
                                                size = buttonSize
                                            )
                                            val endAction = call.actions.firstOrNull {
                                                it.title.contains("end", true) || it.title.contains("hang", true) ||
                                                it.title.contains("decline", true) || it.title.contains("cancel", true)
                                            } ?: call.actions.firstOrNull()
                                            NosRedesignEndCallButton(
                                                onClick = {
                                                    if (endAction != null) sendAction(call.token, endAction)
                                                    else finish()
                                                },
                                                width = endButtonWidth,
                                                height = buttonSize
                                            )
                                        }
                                    }
                                } else {
                                    // Ongoing Call Controls: Expressive Control Tiles Grid + End Call Button

                                    // Row 1: Primary Audio Controls (Mute & Speaker)
                                    Row(
                                        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Tile 1: Mute
                                        ExpressiveTile(
                                            onClick = {
                                                val newMute = !isMuted
                                                setMicMuted(audioManager, newMute)
                                                isMuted = audioManager?.isMicrophoneMute == true
                                                call.actions.firstOrNull {
                                                    it.title.contains("mute", true) || it.title.contains("mic", true)
                                                }?.let { sendAction(call.token, it) }
                                            },
                                            selected = isMuted,
                                            modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                                            color = if (isMuted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = if (isMuted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Symbol(if (isMuted) DialerSymbol.MicOff else DialerSymbol.Mic)
                                                Text(
                                                    stringResource(R.string.expressive_mute),
                                                    style = if (isMuted) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        // Tile 2: Speaker
                                        ExpressiveTile(
                                            onClick = {
                                                val newSpeaker = !isSpeakerOn
                                                setSpeakerActive(audioManager, newSpeaker)
                                                isSpeakerOn = isSpeakerActive(audioManager)
                                                call.actions.firstOrNull {
                                                    it.title.contains("speaker", true)
                                                }?.let { sendAction(call.token, it) }
                                            },
                                            selected = isSpeakerOn,
                                            modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                                            color = if (isSpeakerOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = if (isSpeakerOn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Symbol(DialerSymbol.Speaker)
                                                Text(
                                                    stringResource(R.string.expressive_speaker),
                                                    style = if (isSpeakerOn) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    // Row 2: Open App, Record Call, Recordings
                                    Row(
                                        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Tile 3: Open App
                                        ExpressiveTile(
                                            onClick = { openApp(call) },
                                            modifier = Modifier.weight(1f).heightIn(min = 72.dp)
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (appIconBitmap != null) {
                                                    Image(bitmap = appIconBitmap, contentDescription = null, modifier = Modifier.size(24.dp))
                                                } else {
                                                    Symbol(DialerSymbol.Message)
                                                }
                                                Text(
                                                    stringResource(R.string.voip_open_app, call.appName),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        // Tile 4: Record Call (if recording enabled)
                                        if (recordingEnabled) {
                                            val isRecording = ownRecording?.busy == true
                                            ExpressiveTile(
                                                onClick = {
                                                    if (isRecording) VoipRecordingService.stop(this@VoipCallActivity)
                                                    else {
                                                        val missing = arrayOf(
                                                            Manifest.permission.RECORD_AUDIO,
                                                            Manifest.permission.POST_NOTIFICATIONS
                                                        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                                                        if (missing.isEmpty()) VoipRecordingService.start(this@VoipCallActivity, token)
                                                        else permissionRequest.launch(missing.toTypedArray())
                                                    }
                                                },
                                                selected = isRecording,
                                                enabled = ownRecording?.saving != true && (isRecording || (!recording.busy && calls.size == 1)),
                                                modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                                                color = if (isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                contentColor = if (isRecording) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Symbol(if (isRecording) DialerSymbol.Stop else DialerSymbol.Record)
                                                    Text(
                                                        stringResource(when {
                                                            ownRecording?.saving == true -> R.string.voip_recording_saving
                                                            isRecording -> R.string.voip_stop_recording
                                                            else -> R.string.voip_start_recording
                                                        }),
                                                        style = if (isRecording) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
                                                        textAlign = TextAlign.Center,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        // Tile 5: Recordings
                                        ExpressiveTile(
                                            onClick = {
                                                startActivity(Intent(this@VoipCallActivity, VoipRecordingsActivity::class.java))
                                            },
                                            modifier = Modifier.weight(1f).heightIn(min = 72.dp)
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Symbol(DialerSymbol.History)
                                                Text(
                                                    stringResource(R.string.voip_recordings_title),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    // Additional app actions if any
                                    val extraActions = call.actions.filter { action ->
                                        !action.title.contains("end", true) && !action.title.contains("hang", true) &&
                                        !action.title.contains("decline", true) && !action.title.contains("answer", true) &&
                                        !action.title.contains("mute", true) && !action.title.contains("mic", true) &&
                                        !action.title.contains("speaker", true)
                                    }
                                    if (extraActions.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            extraActions.take(3).forEach { action ->
                                                ExpressiveTile(
                                                    onClick = { sendAction(call.token, action) },
                                                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                                                ) {
                                                    Text(
                                                        action.title,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        textAlign = TextAlign.Center,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Primary End Call Button
                                    val endAction = call.actions.firstOrNull {
                                        it.title.contains("end", true) || it.title.contains("hang", true) ||
                                        it.title.contains("decline", true) || it.title.contains("cancel", true)
                                    } ?: call.actions.firstOrNull()

                                    ExpressiveTile(
                                        onClick = {
                                            if (endAction != null) sendAction(call.token, endAction)
                                            else finish()
                                        },
                                        modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth().heightIn(min = 64.dp),
                                        color = EndCallRed,
                                        contentColor = Color.White
                                    ) {
                                        Symbol(DialerSymbol.EndCall)
                                        Text(
                                            stringResource(R.string.expressive_end_call),
                                            modifier = Modifier.padding(start = 12.dp),
                                            style = MaterialTheme.typography.titleMediumEmphasized
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun IncomingVoipCallButtons(
        call: VoipCall,
        onAction: (VoipAction) -> Unit
    ) {
        val declineAction = call.actions.firstOrNull {
            it.title.contains("decline", true) || it.title.contains("reject", true)
        } ?: call.actions.firstOrNull()

        val answerAction = call.actions.firstOrNull {
            it.title.contains("answer", true) || it.title.contains("accept", true)
        } ?: call.actions.getOrNull(1)

        Row(
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = { declineAction?.let(onAction) },
                    enabled = declineAction != null,
                    modifier = Modifier.size(76.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EndCallRed,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Symbol(DialerSymbol.EndCall, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.expressive_decline), style = MaterialTheme.typography.labelMedium)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = { answerAction?.let(onAction) },
                    enabled = answerAction != null,
                    modifier = Modifier.size(76.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnswerGreen,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Symbol(DialerSymbol.Phone, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.expressive_answer), style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    private fun drawableToBitmapSafe(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        return try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return drawable.bitmap
            }
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
            val bitmap = Bitmap.createBitmap(width.coerceIn(1, 512), height.coerceIn(1, 512), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    private fun sendAction(token: String, action: VoipAction) {
        val current = VoipCalls.find(token) ?: return
        if (!current.actions.contains(action)) return
        try { sendIntent(action.intent) }
        catch (_: PendingIntent.CanceledException) {
            Toast.makeText(this, R.string.voip_action_failed, Toast.LENGTH_LONG).show()
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.voip_action_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun openApp(call: VoipCall) {
        try {
            if (VoipCalls.find(call.token) == null) return
            if (call.contentIntent != null) sendIntent(call.contentIntent)
            else packageManager.getLaunchIntentForPackage(call.packageName)?.let { startActivity(it) }
        } catch (_: Exception) {
            Toast.makeText(this, R.string.voip_action_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun sendIntent(intent: PendingIntent) {
        val options = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        intent.send(this, 0, null, null, null, null, options.toBundle())
    }

    override fun onDestroy() {
        super.onDestroy()
        val token = intent.getStringExtra(VoipNotificationListener.EXTRA_TOKEN)
        if (token == null || VoipCalls.find(token) == null) {
            resetAudioState(getSystemService(AudioManager::class.java))
        }
    }

    private fun isSpeakerActive(audioManager: AudioManager?): Boolean {
        if (audioManager == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                audioManager.isSpeakerphoneOn
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn
        }
    }

    private fun setSpeakerActive(audioManager: AudioManager?, on: Boolean) {
        if (audioManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (on) {
                    val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        audioManager.setCommunicationDevice(speakerDevice)
                    }
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                } else {
                    audioManager.clearCommunicationDevice()
                    val nonSpeaker = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                    if (nonSpeaker != null) {
                        audioManager.setCommunicationDevice(nonSpeaker)
                    }
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = false
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = on
            }
        } catch (e: Throwable) {
            Log.w("VoipCallActivity", "Failed to set speakerphone", e)
        }
    }

    private fun setMicMuted(audioManager: AudioManager?, muted: Boolean) {
        if (audioManager == null) return
        try {
            audioManager.isMicrophoneMute = muted
        } catch (e: Throwable) {
            Log.w("VoipCallActivity", "Failed to set microphone mute", e)
        }
    }

    private fun resetAudioState(audioManager: AudioManager?) {
        if (audioManager == null) return
        try {
            if (audioManager.isMicrophoneMute) {
                audioManager.isMicrophoneMute = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        } catch (_: Throwable) {}
    }
}
