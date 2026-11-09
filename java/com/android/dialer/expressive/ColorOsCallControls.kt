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

package com.android.dialer.expressive

import android.telecom.CallAudioState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
    symbolModifier: Modifier = Modifier.size(28.dp)
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (enabled && pressed) 0.92f else 1f,
        spring(0.65f, 500f),
        label = "coloros button scale"
    )

    val activeBg = Color.White.copy(alpha = 0.95f)
    val activeFg = Color.Black
    val idleBg = Color.White.copy(alpha = 0.18f)
    val idleBorder = Color.White.copy(alpha = 0.30f)

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
        Surface(
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interaction,
                    indication = LocalIndication.current
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            shape = CircleShape,
            color = if (selected) activeBg else idleBg,
            contentColor = if (selected) activeFg else Color.White,
            border = BorderStroke(1.2.dp, if (selected) Color.White else idleBorder),
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Symbol(symbol, modifier = symbolModifier)
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
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (enabled && pressed) 0.93f else 1f,
        spring(0.65f, 500f),
        label = "coloros end scale"
    )

    Column(
        modifier = modifier.semantics {
            contentDescription = "End"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(32.dp))
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interaction,
                    indication = LocalIndication.current
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            shape = RoundedCornerShape(32.dp),
            color = EndCallRed,
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
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
    isControlEnabled: (Int) -> Boolean,
    clickControl: (Int) -> Unit,
    endCall: () -> Unit,
    onSpeakerClick: () -> Unit,
    onDialpadClick: () -> Unit,
    onSummarizeClick: () -> Unit,
    onMoreClick: () -> Unit,
    showMore: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Row 1: Mute, Record, Summarise, More
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            ColorOsCallButton(
                symbol = if (audio?.isMuted == true) DialerSymbol.MicOff else DialerSymbol.Mic,
                label = stringResource(R.string.expressive_mute),
                onClick = { clickControl(BUTTON_MUTE) },
                enabled = isControlEnabled(BUTTON_MUTE),
                selected = audio?.isMuted == true
            )
            ColorOsCallButton(
                symbol = DialerSymbol.Waveform,
                label = stringResource(R.string.expressive_record),
                onClick = { clickControl(BUTTON_RECORD_CALL) },
                enabled = isControlEnabled(BUTTON_RECORD_CALL),
                selected = recording
            )
            ColorOsCallButton(
                symbol = DialerSymbol.Summarize,
                label = stringResource(R.string.expressive_summarise),
                onClick = onSummarizeClick,
                enabled = true,
                selected = false
            )
            ColorOsCallButton(
                symbol = DialerSymbol.More,
                label = stringResource(R.string.expressive_more),
                onClick = onMoreClick,
                enabled = true,
                selected = showMore
            )
        }

        // Row 2: Speaker, End (spanning center), Dial pad
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            ColorOsCallButton(
                symbol = DialerSymbol.Speaker,
                label = stringResource(R.string.expressive_speaker),
                onClick = onSpeakerClick,
                enabled = isControlEnabled(BUTTON_AUDIO),
                selected = audio?.route == CallAudioState.ROUTE_SPEAKER
            )
            ColorOsEndCallButton(
                onClick = endCall,
                enabled = endEnabled,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            )
            ColorOsCallButton(
                symbol = DialerSymbol.Keypad,
                label = stringResource(R.string.expressive_dial_pad),
                onClick = onDialpadClick,
                enabled = isControlEnabled(BUTTON_DIALPAD),
                selected = dialpad
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
