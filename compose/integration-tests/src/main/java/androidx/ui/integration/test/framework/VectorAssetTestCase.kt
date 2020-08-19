/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.ui.integration.test.framework

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.paint
import androidx.compose.foundation.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.VectorAsset
import androidx.compose.ui.graphics.vector.VectorAssetBuilder
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.ui.res.vectorResource
import androidx.ui.test.ComposeTestCase
import androidx.compose.ui.unit.dp

/**
 * Generic test case for drawing a [VectorAsset].
 *
 * Subclasses are responsible for providing the vector asset, so we can test and benchmark different
 * methods of loading / creating this asset.
 */
sealed class VectorAssetTestCase : ComposeTestCase {

    @Composable
    override fun emitContent() {
        Box {
            Box(Modifier.testTag(testTag)
                .preferredSize(24.dp)
                .paint(VectorPainter(getVectorAsset())))
        }
    }

    @Composable
    abstract fun getVectorAsset(): VectorAsset

    abstract val testTag: String
}

/**
 * Test case that loads and parses a vector asset from an XML file.
 */
class XmlVectorTestCase : VectorAssetTestCase() {
    // TODO: should switch to async loading here, and force that to be run synchronously
    @Composable
    override fun getVectorAsset() = vectorResource(
        androidx.ui.integration.test.R.drawable.ic_baseline_menu_24
    )

    override val testTag = "Xml"
}

/**
 * Test case that creates a vector asset purely from code.
 */
class ProgrammaticVectorTestCase : VectorAssetTestCase() {

    /**
     * Returns a clone of ic_baseline_menu_24 built purely in code
     */
    @Composable
    override fun getVectorAsset() = VectorAssetBuilder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            PathData {
                moveTo(3f, 18f)
                horizontalLineToRelative(18f)
                verticalLineToRelative(-2f)
                lineTo(3f, 16f)
                verticalLineToRelative(2f)
                close()
                moveTo(3f, 13f)
                horizontalLineToRelative(18f)
                verticalLineToRelative(-2f)
                lineTo(3f, 11f)
                verticalLineToRelative(2f)
                close()
                moveTo(3f, 6f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(18f)
                lineTo(21f, 6f)
                lineTo(3f, 6f)
                close()
            },
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f
        )
    }.build()

    override val testTag = "Vector"
}
