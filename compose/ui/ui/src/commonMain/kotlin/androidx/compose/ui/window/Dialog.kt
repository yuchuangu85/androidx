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

package androidx.compose.ui.window

import androidx.compose.runtime.Composable

/**
 * Opens a dialog with the given content.
 *
 * The dialog is visible as long as it is part of the composition hierarchy.
 * In order to let the user dismiss the Dialog, the implementation of [onDismissRequest] should
 * contain a way to remove to remove the dialog from the composition hierarchy.
 *
 * Example usage:
 *
 * @sample androidx.compose.ui.samples.DialogSample
 *
 * @param onDismissRequest Executes when the user tries to dismiss the Dialog.
 * @param children The content to be displayed inside the dialog.
 */
@Composable
expect fun Dialog(onDismissRequest: () -> Unit, children: @Composable () -> Unit)