/*
 * Copyright 2020 The Android Open Source Project
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
package androidx.ui.text.platform

import androidx.ui.text.font.Font
import androidx.ui.text.AnnotatedString
import androidx.ui.text.Paragraph
import androidx.ui.text.ParagraphIntrinsics
import androidx.ui.text.ParagraphConstraints
import androidx.ui.text.Placeholder
import androidx.ui.text.SpanStyle
import androidx.ui.text.TextStyle
import androidx.ui.unit.Density

// TODO(b/157854677): remove after fixing.
internal expect fun ActualParagraph(
    text: String,
    style: TextStyle,
    spanStyles: List<AnnotatedString.Range<SpanStyle>>,
    placeholders: List<AnnotatedString.Range<Placeholder>>,
    maxLines: Int,
    ellipsis: Boolean,
    constraints: ParagraphConstraints,
    density: Density,
    resourceLoader: Font.ResourceLoader
): Paragraph

// TODO(b/157854677): remove after fixing.
internal expect fun ActualParagraph(
    paragraphIntrinsics: ParagraphIntrinsics,
    maxLines: Int,
    ellipsis: Boolean,
    constraints: ParagraphConstraints
): Paragraph

// TODO(b/157854677): remove after fixing.
internal expect fun ActualParagraphIntrinsics(
    text: String,
    style: TextStyle,
    spanStyles: List<AnnotatedString.Range<SpanStyle>>,
    placeholders: List<AnnotatedString.Range<Placeholder>>,
    density: Density,
    resourceLoader: Font.ResourceLoader
): ParagraphIntrinsics