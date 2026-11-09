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

import android.content.SharedPreferences
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import java.io.File

internal const val PREF_CUSTOM_CALL_BACKGROUND_SET = "custom_call_background_set"
internal const val PREF_CUSTOM_CALL_BACKGROUND_TIMESTAMP = "custom_call_background_timestamp"
internal const val CUSTOM_CALL_BACKGROUND_FILE = "custom_call_background"

@Composable
internal fun rememberCustomCallBackgroundFile(): File? {
    val context = LocalContext.current
    val preferences = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var timestamp by remember(preferences) {
        mutableLongStateOf(preferences.getLong(PREF_CUSTOM_CALL_BACKGROUND_TIMESTAMP, 0L))
    }
    var isSet by remember(preferences) {
        mutableStateOf(preferences.getBoolean(PREF_CUSTOM_CALL_BACKGROUND_SET, false))
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == PREF_CUSTOM_CALL_BACKGROUND_SET || key == PREF_CUSTOM_CALL_BACKGROUND_TIMESTAMP) {
                isSet = preferences.getBoolean(PREF_CUSTOM_CALL_BACKGROUND_SET, false)
                timestamp = preferences.getLong(PREF_CUSTOM_CALL_BACKGROUND_TIMESTAMP, 0L)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return remember(isSet, timestamp) {
        if (!isSet) return@remember null
        val file = File(context.filesDir, CUSTOM_CALL_BACKGROUND_FILE)
        if (file.exists() && file.length() > 0) file else null
    }
}

@Composable
internal fun CustomCallBackground(
    file: File,
    modifier: Modifier = Modifier,
    applyBlur: Boolean = false,
    blurRadius: Dp = 48.dp
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { blurRadius.toPx() }
    val blurEffect = remember(radiusPx, applyBlur) {
        if (applyBlur && radiusPx > 0f) BlurEffect(radiusPx, radiusPx, TileMode.Clamp) else null
    }

    val drawable = remember(file.lastModified(), file.length()) {
        runCatching {
            val source = ImageDecoder.createSource(file)
            ImageDecoder.decodeDrawable(source)
        }.getOrNull()
    }

    DisposableEffect(drawable) {
        onDispose {
            if (drawable is Animatable && drawable.isRunning) {
                drawable.stop()
            }
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        if (drawable != null) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageDrawable(drawable)
                        if (drawable is Animatable) {
                            if (drawable is AnimatedImageDrawable) {
                                drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                            }
                            drawable.start()
                        }
                    }
                },
                update = { view ->
                    view.setImageDrawable(drawable)
                    if (drawable is Animatable) {
                        if (drawable is AnimatedImageDrawable) {
                            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                        }
                        if (!drawable.isRunning) {
                            drawable.start()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (blurEffect != null) {
                            renderEffect = blurEffect
                            scaleX = 1.08f
                            scaleY = 1.08f
                        }
                    }
            )
        }

        // Vignette gradient for legibility of header text and bottom controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            0.0f to Color.Black.copy(alpha = 0.35f),
                            0.30f to Color.Transparent,
                            0.70f to Color.Black.copy(alpha = 0.18f),
                            1.0f to Color.Black.copy(alpha = 0.35f)
                        )
                    )
                )
        )
    }
}
