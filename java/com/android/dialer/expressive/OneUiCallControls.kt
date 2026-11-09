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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    glassBackdrop: DialerGlassBackdrop? = null,
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(32.dp)

    val glassModifier = if (glassBackdrop != null) {
        Modifier.dialerGlass(
            backdrop = glassBackdrop,
            shape = shape,
            blurRadius = 32.dp,
            tint = if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.35f),
            highlight = if (dark) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.65f)
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.15f)
            )
            .then(glassModifier)
            .clip(shape)
            .background(
                if (glassBackdrop != null) {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.14f else 0.28f),
                            Color.White.copy(alpha = if (dark) 0.05f else 0.14f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.22f else 0.48f),
                            Color.White.copy(alpha = if (dark) 0.14f else 0.32f)
                        )
                    )
                }
            )
            .border(
                BorderStroke(
                    1.2.dp,
                    if (dark) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.60f)
                ),
                shape
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
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
    selected: Boolean = false,
    symbolModifier: Modifier = Modifier.size(26.dp)
) {
    val dark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Bouncy spring animation on touch
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 380f
        ),
        label = "oneui_button_scale"
    )

    // Smooth animated color transitions
    val animatedBg by animateColorAsState(
        targetValue = if (selected) {
            if (dark) Color.White.copy(alpha = 0.95f) else Color.White
        } else if (pressed) {
            Color.White.copy(alpha = 0.18f)
        } else {
            Color.White.copy(alpha = 0.08f)
        },
        animationSpec = spring(stiffness = 450f),
        label = "oneui_btn_bg"
    )

    val animatedFg by animateColorAsState(
        targetValue = if (selected) {
            Color(0xFF1C1C1E)
        } else {
            Color.White
        },
        animationSpec = spring(stiffness = 450f),
        label = "oneui_btn_fg"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (selected) {
            Color.White
        } else if (pressed) {
            Color.White.copy(alpha = 0.35f)
        } else {
            Color.White.copy(alpha = 0.15f)
        },
        animationSpec = spring(stiffness = 450f),
        label = "oneui_btn_border"
    )

    val itemShape = RoundedCornerShape(20.dp)

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
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .shadow(
                    elevation = if (selected) 4.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = Color.White.copy(alpha = 0.3f),
                    spotColor = Color.White.copy(alpha = 0.2f)
                ),
            shape = CircleShape,
            color = animatedBg,
            contentColor = animatedFg,
            border = BorderStroke(1.dp, animatedBorderColor)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides animatedFg) {
                    Symbol(symbol, modifier = symbolModifier)
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = if (selected) 1f else 0.90f),
            maxLines = 1
        )
    }
}

@Composable
internal fun OneUiEndCallButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    glassBackdrop: DialerGlassBackdrop? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 380f
        ),
        label = "oneui_end_call_scale"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val glassModifier = if (glassBackdrop != null) {
            Modifier.dialerGlass(
                backdrop = glassBackdrop,
                shape = CircleShape,
                blurRadius = 24.dp,
                tint = Color(0xC8D32F2F),
                highlight = Color.White.copy(alpha = 0.40f)
            )
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = Color(0x88FF3B30),
                    spotColor = Color(0xAAD32F2F)
                )
                .then(glassModifier)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFF453A).copy(alpha = if (pressed) 0.95f else 0.88f),
                            Color(0xFFD32F2F).copy(alpha = if (pressed) 1f else 0.94f)
                        )
                    )
                )
                .border(BorderStroke(1.2.dp, Color.White.copy(alpha = 0.35f)), CircleShape)
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
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Symbol(DialerSymbol.EndCall, modifier = Modifier.size(34.dp))
            }
        }
    }
}
