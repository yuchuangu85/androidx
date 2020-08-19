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

package androidx.compose.ui.platform

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.selection.Selectable
import androidx.compose.ui.selection.SelectionRegistrar

// based on androidx.compose.ui.selection.SelectionRegistrarImpl
internal class DesktopSelectionRegistrar : SelectionRegistrar {
    internal var sorted: Boolean = false

    private val _selectables = mutableListOf<Selectable>()
    internal val selectables: List<Selectable>
        get() = _selectables

    internal var onPositionChangeCallback: (() -> Unit)? = null

    override fun subscribe(selectable: Selectable): Selectable {
        _selectables.add(selectable)
        sorted = false
        return selectable
    }

    override fun unsubscribe(selectable: Selectable) {
        _selectables.remove(selectable)
    }

    fun sort(containerLayoutCoordinates: LayoutCoordinates): List<Selectable> {
        if (!sorted) {
            _selectables.sortWith(Comparator { a: Selectable, b: Selectable ->
                val layoutCoordinatesA = a.getLayoutCoordinates()
                val layoutCoordinatesB = b.getLayoutCoordinates()

                val positionA =
                    if (layoutCoordinatesA != null) containerLayoutCoordinates.childToLocal(
                        layoutCoordinatesA,
                        Offset.Zero
                    )
                    else Offset.Zero
                val positionB =
                    if (layoutCoordinatesB != null) containerLayoutCoordinates.childToLocal(
                        layoutCoordinatesB,
                        Offset.Zero
                    )
                    else Offset.Zero

                if (positionA.y == positionB.y) compareValues(positionA.x, positionB.x)
                else compareValues(positionA.y, positionB.y)
            })
            sorted = true
        }
        return selectables
    }

    override fun onPositionChange() {
        sorted = false
        onPositionChangeCallback?.invoke()
    }
}