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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.incallui.incall.protocol.InCallButtonIds.*

internal const val ONEUI_BUTTON_BLUETOOTH = -2001
internal const val ONEUI_BUTTON_SPEAKER = -2002

internal val ONEUI_CONTROL_IDS = listOf(
    BUTTON_ADD_CALL,
    BUTTON_UPGRADE_TO_VIDEO,
    ONEUI_BUTTON_BLUETOOTH,
    ONEUI_BUTTON_SPEAKER,
    BUTTON_MUTE,
    BUTTON_DIALPAD,
    BUTTON_HOLD,
    BUTTON_RECORD_CALL,
    BUTTON_MERGE,
    BUTTON_UPGRADE_TO_RTT,
    BUTTON_SWAP_SIM,
    BUTTON_MANAGE_VOICE_CONFERENCE
)

@Composable
internal fun OneUiCallCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val containerColor = if (dark) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.45f)
    }
    val borderColor = if (dark) {
        Color.White.copy(alpha = 0.18f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(36.dp)),
        shape = RoundedCornerShape(36.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            content()
        }
    }
}

@Composable
internal fun OneUiCallButton(
    symbol: DialerSymbol,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    val dark = isSystemInDarkTheme()
    val foreground = LocalContentColor.current
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (enabled && pressed) 0.93f else 1f,
        spring(0.65f, 500f),
        label = "oneui button scale"
    )

    val itemShape = RoundedCornerShape(20.dp)
    val activeBg = if (dark) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.primary
    val activeFg = if (dark) Color.Black else MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(itemShape)
            .semantics {
                this.selected = selected
                contentDescription = label
            }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interaction,
                indication = LocalIndication.current
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = if (selected) activeBg else Color.Transparent,
            contentColor = if (selected) activeFg else foreground
        ) {
            Box(contentAlignment = Alignment.Center) {
                Symbol(symbol, modifier = Modifier.size(28.dp))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = if (selected && !dark) activeBg else foreground,
            maxLines = 1
        )
    }
}

@Composable
internal fun OneUiEndCallButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (enabled && pressed) 0.92f else 1f,
        spring(0.65f, 500f),
        label = "oneui end call scale"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .semantics {
                    contentDescription = "End call"
                }
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
            color = EndCallRed,
            contentColor = Color.White,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Symbol(DialerSymbol.EndCall, modifier = Modifier.size(34.dp))
            }
        }
    }
}
