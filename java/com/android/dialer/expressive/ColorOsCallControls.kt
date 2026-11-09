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
 * limitations under the License.
 */

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.android.dialer.expressive

import android.telecom.CallAudioState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.dialer.R
import com.android.incallui.incall.protocol.InCallButtonIds.*

@Composable
internal fun ColorOsCallButton(
    symbol: DialerSymbol,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    glassBackdrop: DialerGlassBackdrop? = null,
    isRecording: Boolean = false,
    symbolModifier: Modifier = Modifier.size(28.dp)
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Bouncy spring touch response
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 380f
        ),
        label = "coloros_btn_scale"
    )

    // Animated colors for state transitions
    val animatedContentColor by animateColorAsState(
        targetValue = if (selected) Color(0xFF1E1E1E) else Color.White,
        animationSpec = spring(stiffness = 450f),
        label = "coloros_btn_fg"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.38f),
        animationSpec = spring(stiffness = 450f),
        label = "coloros_btn_border"
    )

    // Gentle waveform pulse when recording is in progress
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val waveformPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveform_pulse"
    )

    Column(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .semantics {
                this.selected = selected
                contentDescription = label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val glassModifier = if (glassBackdrop != null && !selected) {
            Modifier.dialerGlass(
                backdrop = glassBackdrop,
                shape = CircleShape,
                blurRadius = 26.dp,
                tint = Color.White.copy(alpha = if (pressed) 0.22f else 0.12f),
                highlight = Color.White.copy(alpha = 0.40f)
            )
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = if (selected) 6.dp else 3.dp,
                    shape = CircleShape,
                    ambientColor = if (selected) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.35f),
                    spotColor = if (selected) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.25f)
                )
                .then(glassModifier)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.98f), Color.White.copy(alpha = 0.92f))
                        )
                    } else if (glassBackdrop != null) {
                        // Subtle specular surface sheen over the live frosted glass
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (pressed) 0.18f else 0.10f),
                                Color.White.copy(alpha = if (pressed) 0.08f else 0.03f)
                            )
                        )
                    } else {
                        // Fallback translucent frosted glass when backdrop is not supplied
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (pressed) 0.30f else 0.20f),
                                Color.White.copy(alpha = if (pressed) 0.20f else 0.12f)
                            )
                        )
                    }
                )
                .border(BorderStroke(1.1.dp, animatedBorderColor), CircleShape)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interaction,
                    indication = LocalIndication.current
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = if (isRecording) {
                    Modifier.graphicsLayer {
                        scaleX = waveformPulse
                        scaleY = waveformPulse
                    }
                } else Modifier,
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(LocalContentColor provides animatedContentColor) {
                    Symbol(symbol, modifier = symbolModifier)
                }
            }

            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-10).dp, y = 10.dp)
                        .background(Color(0xFFFF3B30), CircleShape)
                        .border(1.dp, Color.White, CircleShape)
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.90f),
            maxLines = 1
        )
    }
}

@Composable
internal fun ColorOsEndCallButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    glassBackdrop: DialerGlassBackdrop? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 380f
        ),
        label = "coloros_end_scale"
    )

    val shape = RoundedCornerShape(32.dp)

    Column(
        modifier = modifier.semantics {
            contentDescription = "End"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val glassModifier = if (glassBackdrop != null) {
            Modifier.dialerGlass(
                backdrop = glassBackdrop,
                shape = shape,
                blurRadius = 24.dp,
                tint = Color(0xC8D32F2F),
                highlight = Color.White.copy(alpha = 0.40f)
            )
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .width(136.dp)
                .height(64.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = 6.dp,
                    shape = shape,
                    ambientColor = Color(0x66FF3B30),
                    spotColor = Color(0x88D32F2F)
                )
                .then(glassModifier)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFF5252).copy(alpha = if (pressed) 0.95f else 0.88f),
                            Color(0xFFD32F2F).copy(alpha = if (pressed) 1f else 0.94f)
                        )
                    )
                )
                .border(BorderStroke(1.2.dp, Color.White.copy(alpha = 0.35f)), shape)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interaction,
                    indication = LocalIndication.current
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Symbol(DialerSymbol.EndCall, modifier = Modifier.size(30.dp))
            }
        }

        Text(
            text = stringResource(R.string.expressive_end),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.90f),
            maxLines = 1
        )
    }
}

@Composable
internal fun ColorOsCallControls(
    audio: CallAudioState?,
    recording: Boolean,
    dialpad: Boolean,
    endEnabled: Boolean,
    glassBackdrop: DialerGlassBackdrop? = null,
    isControlEnabled: (Int) -> Boolean,
    clickControl: (Int) -> Unit,
    endCall: () -> Unit,
    onSpeakerClick: () -> Unit,
    onDialpadClick: () -> Unit,
    onMoreClick: () -> Unit,
    showMore: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // Row 1: Mute (left), Record (center), More (right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            ColorOsCallButton(
                symbol = if (audio?.isMuted == true) DialerSymbol.MicOff else DialerSymbol.Mic,
                label = stringResource(R.string.expressive_mute),
                onClick = { clickControl(BUTTON_MUTE) },
                enabled = isControlEnabled(BUTTON_MUTE),
                selected = audio?.isMuted == true,
                glassBackdrop = glassBackdrop,
                modifier = Modifier.weight(1f)
            )
            ColorOsCallButton(
                symbol = DialerSymbol.Waveform,
                label = stringResource(R.string.expressive_record),
                onClick = { clickControl(BUTTON_RECORD_CALL) },
                enabled = isControlEnabled(BUTTON_RECORD_CALL),
                selected = recording,
                glassBackdrop = glassBackdrop,
                isRecording = recording,
                modifier = Modifier.weight(1f)
            )
            ColorOsCallButton(
                symbol = DialerSymbol.More,
                label = stringResource(R.string.expressive_more),
                onClick = onMoreClick,
                enabled = true,
                selected = showMore,
                glassBackdrop = glassBackdrop,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Speaker (left), End (center capsule), Dial pad (right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            ColorOsCallButton(
                symbol = DialerSymbol.Speaker,
                label = stringResource(R.string.expressive_speaker),
                onClick = onSpeakerClick,
                enabled = isControlEnabled(BUTTON_AUDIO),
                selected = audio?.route == CallAudioState.ROUTE_SPEAKER,
                glassBackdrop = glassBackdrop,
                modifier = Modifier.weight(1f)
            )
            ColorOsEndCallButton(
                onClick = endCall,
                enabled = endEnabled,
                glassBackdrop = glassBackdrop,
                modifier = Modifier.weight(1f)
            )
            ColorOsCallButton(
                symbol = DialerSymbol.Keypad,
                label = stringResource(R.string.expressive_dial_pad),
                onClick = onDialpadClick,
                enabled = isControlEnabled(BUTTON_DIALPAD),
                selected = dialpad,
                glassBackdrop = glassBackdrop,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun ColorOsMoreSheet(
    onDismiss: () -> Unit,
    onActionClick: (Int) -> Unit,
    isControlEnabled: (Int) -> Boolean,
    audio: CallAudioState?
) {
    DialerModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.expressive_more),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val moreButtons = listOf(
                BUTTON_ADD_CALL to (DialerSymbol.Add to R.string.expressive_add_call),
                BUTTON_HOLD to (DialerSymbol.Pause to R.string.expressive_hold),
                BUTTON_UPGRADE_TO_VIDEO to (DialerSymbol.Video to R.string.expressive_video),
                BUTTON_MERGE to (DialerSymbol.People to R.string.expressive_merge),
                BUTTON_SWAP to (DialerSymbol.Swap to R.string.expressive_swap),
                BUTTON_UPGRADE_TO_RTT to (DialerSymbol.Message to R.string.expressive_rtt),
                BUTTON_SWAP_SIM to (DialerSymbol.Sim to R.string.expressive_swap_sim),
                BUTTON_MANAGE_VOICE_CONFERENCE to (DialerSymbol.People to R.string.expressive_manage_conference)
            )
            moreButtons.forEach { (id, pair) ->
                val (symbol, labelRes) = pair
                if (isControlEnabled(id)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                onDismiss()
                                onActionClick(id)
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Symbol(symbol, modifier = Modifier.size(24.dp))
                            Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
