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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize

val DIALER_GLASS_BLUR: Dp = 24.dp

@Stable
class DialerGlassBackdrop internal constructor(
    internal val layer: GraphicsLayer,
) {
    internal var sourceCoordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())
}

@Composable
fun rememberDialerGlassBackdrop(): DialerGlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { DialerGlassBackdrop(layer) }
}

fun Modifier.dialerGlassSource(backdrop: DialerGlassBackdrop): Modifier =
    this
        .onGloballyPositioned { if (it.isAttached) backdrop.sourceCoordinates = it }
        .drawWithContent {
            backdrop.layer.record(size.toIntSize()) {
                this@drawWithContent.drawContent()
            }
            drawLayer(backdrop.layer)
        }

fun Modifier.dialerGlass(
    backdrop: DialerGlassBackdrop,
    shape: Shape,
    blurRadius: Dp = DIALER_GLASS_BLUR,
    tint: Color = Color.Unspecified,
    highlight: Color = Color.Unspecified,
): Modifier = composed {
    val effectLayer = rememberGraphicsLayer()
    var selfCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy()) }

    this
        .onGloballyPositioned { if (it.isAttached) selfCoordinates = it }
        .drawBehind {
            val sourceCoords = backdrop.sourceCoordinates ?: return@drawBehind
            val selfCoords = selfCoordinates ?: return@drawBehind
            val sourceLayer = backdrop.layer

            val offset =
                try {
                    sourceCoords.localPositionOf(selfCoords)
                } catch (_: Throwable) {
                    selfCoords.positionInWindow() - sourceCoords.positionInWindow()
                }

            val radiusPx = blurRadius.toPx()
            effectLayer.renderEffect =
                if (radiusPx > 0f) BlurEffect(null, radiusPx, radiusPx, TileMode.Clamp) else null
            effectLayer.record(size.toIntSize()) {
                translate(-offset.x, -offset.y) {
                    drawLayer(sourceLayer)
                }
            }

            val path = Path().apply {
                when (val outline = shape.createOutline(size, layoutDirection, this@drawBehind)) {
                    is Outline.Rectangle -> addRect(outline.rect)
                    is Outline.Rounded -> addRoundRect(outline.roundRect)
                    is Outline.Generic -> addPath(outline.path)
                }
            }
            clipPath(path) {
                drawLayer(effectLayer)
                if (tint.isSpecified) drawRect(color = tint)
            }
            if (highlight.isSpecified) {
                drawPath(path, color = highlight, style = Stroke(width = 1.dp.toPx()))
            }
        }
}
