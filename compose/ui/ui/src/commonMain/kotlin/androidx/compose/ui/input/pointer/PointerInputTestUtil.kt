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

package androidx.compose.ui.input.pointer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Duration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Uptime

// TODO(shepshapard): Document.

internal fun down(
    id: Long,
    duration: Duration = Duration.Zero,
    x: Float = 0f,
    y: Float = 0f
): PointerInputChange =
    PointerInputChange(
        PointerId(id),
        PointerInputData(
            Uptime.Boot + duration,
            Offset(x, y),
            true
        ),
        PointerInputData(null, null, false),
        ConsumedData(Offset.Zero, false)
    )

internal fun PointerInputChange.moveTo(duration: Duration, x: Float = 0f, y: Float = 0f) =
    copy(
        previous = current,
        current = PointerInputData(
            Uptime.Boot + duration,
            Offset(x, y),
            true
        ),
        consumed = ConsumedData()
    )

internal fun PointerInputChange.moveBy(duration: Duration, dx: Float = 0f, dy: Float = 0f) =
    copy(
        previous = current,
        current = PointerInputData(
            current.uptime!! + duration,
            Offset(current.position!!.x + dx, current.position.y + dy),
            true
        ),
        consumed = ConsumedData()
    )

internal fun PointerInputChange.up(duration: Duration) =
    copy(
        previous = current,
        current = PointerInputData(
            Uptime.Boot + duration,
            null,
            false
        ),
        consumed = ConsumedData()
    )

internal fun PointerInputChange.consume(
    dx: Float = 0f,
    dy: Float = 0f,
    downChange: Boolean = false
) =
    copy(
        consumed = consumed.copy(
            positionChange = Offset(
                consumed.positionChange.x + dx,
                consumed.positionChange.y + dy
            ), downChange = consumed.downChange || downChange
        )
    )

internal fun PointerInputHandler.invokeOverAllPasses(
    pointerInputChanges: PointerInputChange,
    size: IntSize = IntSize(Int.MAX_VALUE, Int.MAX_VALUE)
) = invokeOverPasses(
    listOf(pointerInputChanges),
    listOf(
        PointerEventPass.Initial,
        PointerEventPass.Main,
        PointerEventPass.Final
    ),
    size = size
).first()

internal fun PointerInputHandler.invokeOverAllPasses(
    vararg pointerInputChanges: PointerInputChange,
    size: IntSize = IntSize(Int.MAX_VALUE, Int.MAX_VALUE)
) = invokeOverPasses(
    pointerInputChanges.toList(),
    listOf(
        PointerEventPass.Initial,
        PointerEventPass.Main,
        PointerEventPass.Final
    ),
    size = size
)

// TODO(shepshapard): Rename to invokeOverPass
internal fun PointerInputHandler.invokeOverPasses(
    pointerInputChange: PointerInputChange,
    pointerEventPass: PointerEventPass,
    size: IntSize = IntSize(Int.MAX_VALUE, Int.MAX_VALUE)
) = invokeOverPasses(listOf(pointerInputChange), listOf(pointerEventPass), size).first()

// TODO(shepshapard): Rename to invokeOverPass
internal fun PointerInputHandler.invokeOverPasses(
    vararg pointerInputChanges: PointerInputChange,
    pointerEventPass: PointerEventPass,
    size: IntSize = IntSize(Int.MAX_VALUE, Int.MAX_VALUE)
) = invokeOverPasses(pointerInputChanges.toList(), listOf(pointerEventPass), size)

internal fun PointerInputHandler.invokeOverPasses(
    pointerInputChange: PointerInputChange,
    vararg pointerEventPasses: PointerEventPass,
    size: IntSize = IntSize(Int.MAX_VALUE, Int.MAX_VALUE)
) = invokeOverPasses(listOf(pointerInputChange), pointerEventPasses.toList(), size).first()

internal fun PointerInputHandler.invokeOverPasses(
    pointerInputChanges: List<PointerInputChange>,
    pointerEventPasses: List<PointerEventPass>,
    size: IntSize = IntSize(Int.MAX_VALUE, Int.MAX_VALUE)
): List<PointerInputChange> {
    require(pointerInputChanges.isNotEmpty())
    require(pointerEventPasses.isNotEmpty())
    var localPointerInputChanges = pointerInputChanges
    pointerEventPasses.forEach {
        localPointerInputChanges = this.invoke(localPointerInputChanges, it, size)
    }
    return localPointerInputChanges
}

/**
 * Simulates the dispatching of [event] to [this] on all [PointerEventPass]es in their standard
 * order.
 *
 * @param event The event to dispatch.
 */
internal fun ((CustomEvent, PointerEventPass) -> Unit).invokeOverAllPasses(
    event: CustomEvent
) {
    invokeOverPasses(
        event,
        listOf(
            PointerEventPass.Initial,
            PointerEventPass.Main,
            PointerEventPass.Final
        )
    )
}

/**
 * Simulates the dispatching of [event] to [this] on all [PointerEventPass]es in their standard
 * order.
 *
 * @param event The event to dispatch.
 * @param pointerEventPasses The [PointerEventPass]es to pass to each call to [this].
 */
internal fun ((CustomEvent, PointerEventPass) -> Unit).invokeOverPasses(
    event: CustomEvent,
    pointerEventPasses: List<PointerEventPass>
) {
    pointerEventPasses.forEach { pass ->
        this.invoke(event, pass)
    }
}
