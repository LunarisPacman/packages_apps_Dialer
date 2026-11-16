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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.dialer.R
import com.android.incallui.incall.protocol.InCallButtonIds.*

@Composable
internal fun NosRedesignCallButton(
    symbol: DialerSymbol,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: Dp = 66.dp,
    glassBackdrop: DialerGlassBackdrop? = null
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "nos_button_scale"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> Color.White
            !enabled -> Color(0xFF1E1E1E).copy(alpha = 0.40f)
            else -> Color(0xFF242424).copy(alpha = 0.85f)
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "nos_button_container"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            selected -> Color.Black
            !enabled -> Color.White.copy(alpha = 0.35f)
            else -> Color.White
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "nos_button_content"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                this.contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .then(
                    if (glassBackdrop != null && !selected && enabled) {
                        Modifier.dialerGlass(glassBackdrop, shape = CircleShape)
                    } else Modifier
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                ),
            shape = CircleShape,
            color = containerColor,
            border = if (!selected) BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)) else null
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    Symbol(symbol, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
internal fun NosRedesignEndCallButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 146.dp,
    height: Dp = 66.dp,
    color: Color = EndCallRed,
    enabled: Boolean = true,
    glassBackdrop: DialerGlassBackdrop? = null
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "nos_end_scale"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> color.copy(alpha = 0.40f)
            pressed -> Color(0xFFA01515)
            else -> color
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "nos_end_container"
    )

    val endLabel = stringResource(R.string.expressive_end_call)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                this.contentDescription = endLabel
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = (if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .then(
                    if (glassBackdrop != null && enabled) {
                        Modifier.dialerGlass(glassBackdrop, shape = RoundedCornerShape(height / 2))
                    } else Modifier
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                ),
            shape = RoundedCornerShape(height / 2),
            color = containerColor,
            border = null
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    Symbol(DialerSymbol.EndCall, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

@Composable
internal fun NosRedesignCallControls(
    audio: CallAudioState?,
    recording: Boolean,
    dialpad: Boolean,
    held: Boolean,
    endEnabled: Boolean,
    glassBackdrop: DialerGlassBackdrop? = null,
    statusContent: (@Composable () -> Unit)? = null,
    isControlEnabled: (Int) -> Boolean,
    clickControl: (Int) -> Unit,
    endCall: () -> Unit,
    onSpeakerClick: () -> Unit,
    onDialpadClick: () -> Unit,
    onMoreClick: () -> Unit = {},
    showMore: Boolean = false,
    modifier: Modifier = Modifier
) {
    val buttonSize = 66.dp
    val buttonSpacing = 14.dp
    val endButtonWidth = buttonSize * 2 + buttonSpacing // 146.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(buttonSpacing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (statusContent != null) {
            statusContent()
            Spacer(Modifier.height(8.dp))
        }

        // Row 1: More, Hold, Record, Speaker (4 circular buttons, centered, 14.dp gap)
        Row(
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NosRedesignCallButton(
                symbol = DialerSymbol.More,
                label = stringResource(R.string.expressive_more),
                onClick = onMoreClick,
                enabled = true,
                selected = showMore,
                size = buttonSize,
                glassBackdrop = glassBackdrop
            )
            NosRedesignCallButton(
                symbol = DialerSymbol.Pause,
                label = stringResource(R.string.expressive_hold),
                onClick = { clickControl(BUTTON_HOLD) },
                enabled = isControlEnabled(BUTTON_HOLD),
                selected = held,
                size = buttonSize,
                glassBackdrop = glassBackdrop
            )
            NosRedesignCallButton(
                symbol = if (recording) DialerSymbol.Stop else DialerSymbol.Record,
                label = stringResource(R.string.expressive_record),
                onClick = { clickControl(BUTTON_RECORD_CALL) },
                enabled = isControlEnabled(BUTTON_RECORD_CALL),
                selected = recording,
                size = buttonSize,
                glassBackdrop = glassBackdrop
            )
            NosRedesignCallButton(
                symbol = DialerSymbol.Speaker,
                label = stringResource(R.string.expressive_speaker),
                onClick = onSpeakerClick,
                enabled = isControlEnabled(BUTTON_AUDIO),
                selected = audio?.route == CallAudioState.ROUTE_SPEAKER,
                size = buttonSize,
                glassBackdrop = glassBackdrop
            )
        }

        // Row 2: Mute (circle), End Call (pill), Dial pad (circle)
        Row(
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NosRedesignCallButton(
                symbol = if (audio?.isMuted == true) DialerSymbol.MicOff else DialerSymbol.Mic,
                label = stringResource(R.string.expressive_mute),
                onClick = { clickControl(BUTTON_MUTE) },
                enabled = isControlEnabled(BUTTON_MUTE),
                selected = audio?.isMuted == true,
                size = buttonSize,
                glassBackdrop = glassBackdrop
            )
            NosRedesignEndCallButton(
                onClick = endCall,
                enabled = endEnabled,
                glassBackdrop = glassBackdrop,
                width = endButtonWidth,
                height = buttonSize
            )
            NosRedesignCallButton(
                symbol = DialerSymbol.Keypad,
                label = stringResource(R.string.expressive_dial_pad),
                onClick = onDialpadClick,
                enabled = isControlEnabled(BUTTON_DIALPAD),
                selected = dialpad,
                size = buttonSize,
                glassBackdrop = glassBackdrop
            )
        }
    }
}

@Composable
internal fun NosRedesignMoreSheet(
    onDismiss: () -> Unit,
    onActionClick: (Int) -> Unit,
    isControlEnabled: (Int) -> Boolean
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
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val moreButtons = listOf(
                BUTTON_ADD_CALL to (DialerSymbol.Add to R.string.expressive_add_call),
                BUTTON_HOLD to (DialerSymbol.Pause to R.string.expressive_hold),
                BUTTON_SWAP to (DialerSymbol.Swap to R.string.expressive_swap),
                BUTTON_MERGE to (DialerSymbol.People to R.string.expressive_merge),
                BUTTON_SWAP_SIM to (DialerSymbol.Sim to R.string.expressive_swap_sim),
                BUTTON_UPGRADE_TO_VIDEO to (DialerSymbol.Video to R.string.expressive_video),
                BUTTON_UPGRADE_TO_RTT to (DialerSymbol.Message to R.string.expressive_rtt),
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
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = Color(0xFF262626),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Symbol(symbol, modifier = Modifier.size(20.dp))
                                }
                            }
                            Text(
                                stringResource(labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
