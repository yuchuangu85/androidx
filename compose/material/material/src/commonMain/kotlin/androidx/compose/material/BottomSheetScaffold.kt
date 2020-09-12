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

package androidx.compose.material

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.asDisposableClock
import androidx.compose.animation.core.AnimationClockObservable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Stack
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.savedinstancestate.Saver
import androidx.compose.runtime.savedinstancestate.rememberSavedInstanceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.WithConstraints
import androidx.compose.ui.gesture.scrollorientationlocking.Orientation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.onPositioned
import androidx.compose.ui.platform.AnimationClockAmbient
import androidx.compose.ui.platform.DensityAmbient
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Possible values of [BottomSheetState].
 */
@ExperimentalMaterialApi
enum class BottomSheetValue {
    /**
     * The bottom sheet is visible, but only showing its peek height.
     */
    Collapsed,

    /**
     * The bottom sheet is visible at its maximum height.
     */
    Expanded
}

/**
 * State of the persistent bottom sheet in [BottomSheetScaffold].
 *
 * @param initialValue The initial value of the state.
 * @param clock The animation clock that will be used to drive the animations.
 * @param animationSpec The default animation that will be used to animate to a new state.
 * @param confirmStateChange Optional callback invoked to confirm or veto a pending state change.
 */
@ExperimentalMaterialApi
class BottomSheetState(
    initialValue: BottomSheetValue,
    clock: AnimationClockObservable,
    animationSpec: AnimationSpec<Float> = SwipeableConstants.DefaultAnimationSpec,
    confirmStateChange: (BottomSheetValue) -> Boolean = { true }
) : SwipeableState<BottomSheetValue>(
    initialValue = initialValue,
    clock = clock,
    animationSpec = animationSpec,
    confirmStateChange = confirmStateChange
) {
    /**
     * Whether the bottom sheet is expanded.
     */
    val isExpanded: Boolean
        get() = value == BottomSheetValue.Expanded

    /**
     * Whether the bottom sheet is collapsed.
     */
    val isCollapsed: Boolean
        get() = value == BottomSheetValue.Collapsed

    /**
     * Expand the bottom sheet, with an animation.
     *
     * @param onExpanded Optional callback invoked when the bottom sheet has been expanded.
     */
    fun expand(onExpanded: (() -> Unit)? = null) {
        animateTo(BottomSheetValue.Expanded, onEnd = { endReason, _ ->
            if (endReason == AnimationEndReason.TargetReached) {
                onExpanded?.invoke()
            }
        })
    }

    /**
     * Collapse the bottom sheet, with an animation.
     *
     * @param onCollapsed Optional callback invoked when the bottom sheet has been collapsed.
     */
    fun collapse(onCollapsed: (() -> Unit)? = null) {
        animateTo(BottomSheetValue.Collapsed, onEnd = { endReason, _ ->
            if (endReason == AnimationEndReason.TargetReached) {
                onCollapsed?.invoke()
            }
        })
    }

    companion object {
        /**
         * The default [Saver] implementation for [BottomSheetState].
         */
        fun Saver(
            clock: AnimationClockObservable,
            animationSpec: AnimationSpec<Float>,
            confirmStateChange: (BottomSheetValue) -> Boolean
        ): Saver<BottomSheetState, *> = Saver(
            save = { it.value },
            restore = {
                BottomSheetState(
                    initialValue = it,
                    clock = clock,
                    animationSpec = animationSpec,
                    confirmStateChange = confirmStateChange
                )
            }
        )
    }
}

/**
 * Create a [BottomSheetState] and [remember] it against the [clock]. If a clock is not
 * specified, the default animation clock will be used, as provided by [AnimationClockAmbient].
 *
 * @param initialValue The initial value of the state.
 * @param clock The animation clock that will be used to drive the animations.
 * @param animationSpec The default animation that will be used to animate to a new state.
 * @param confirmStateChange Optional callback invoked to confirm or veto a pending state change.
 */
@Composable
@ExperimentalMaterialApi
fun rememberBottomSheetState(
    initialValue: BottomSheetValue,
    clock: AnimationClockObservable = AnimationClockAmbient.current,
    animationSpec: AnimationSpec<Float> = SwipeableConstants.DefaultAnimationSpec,
    confirmStateChange: (BottomSheetValue) -> Boolean = { true }
): BottomSheetState {
    val disposableClock = clock.asDisposableClock()
    return rememberSavedInstanceState(
        disposableClock,
        saver = BottomSheetState.Saver(
            clock = disposableClock,
            animationSpec = animationSpec,
            confirmStateChange = confirmStateChange
        )
    ) {
        BottomSheetState(
            initialValue = initialValue,
            clock = disposableClock,
            animationSpec = animationSpec,
            confirmStateChange = confirmStateChange
        )
    }
}

/**
 * State of the [BottomSheetScaffold] composable.
 *
 * @param drawerState The state of the navigation drawer.
 * @param bottomSheetState The state of the persistent bottom sheet.
 * @param snackbarHostState The [SnackbarHostState] used to show snackbars inside the scaffold.
 */
@ExperimentalMaterialApi
class BottomSheetScaffoldState(
    val drawerState: DrawerState,
    val bottomSheetState: BottomSheetState,
    val snackbarHostState: SnackbarHostState
)

/**
 * Create and [remember] a [BottomSheetScaffoldState].
 *
 * @param drawerState The state of the navigation drawer.
 * @param bottomSheetState The state of the persistent bottom sheet.
 * @param snackbarHostState The [SnackbarHostState] used to show snackbars inside the scaffold.
 */
@Composable
@ExperimentalMaterialApi
fun rememberBottomSheetScaffoldState(
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    bottomSheetState: BottomSheetState = rememberBottomSheetState(BottomSheetValue.Collapsed),
    snackbarHostState: SnackbarHostState = SnackbarHostState()
): BottomSheetScaffoldState {
    return remember(drawerState, bottomSheetState, snackbarHostState) {
        BottomSheetScaffoldState(
            drawerState = drawerState,
            bottomSheetState = bottomSheetState,
            snackbarHostState = snackbarHostState
        )
    }
}

/**
 * Standard bottom sheets co-exist with the screen’s main UI region and allow for simultaneously
 * viewing and interacting with both regions. They are commonly used to keep a feature or
 * secondary content visible on screen when content in main UI region is frequently scrolled or
 * panned.
 *
 * This component provides an API to put together several material components to construct your
 * screen. For a similar component which implements the basic material design layout strategy
 * with app bars, floating action buttons and navigation drawers, use the standard [Scaffold].
 * For similar component that uses a backdrop as the centerpiece of the screen, use
 * [BackdropScaffold].
 *
 * A simple example of a bottom sheet scaffold looks like this:
 *
 * @sample androidx.compose.material.samples.BottomSheetScaffoldSample
 *
 * @param sheetContent The content of the bottom sheet.
 * @param modifier An optional [Modifier] for the root of the scaffold.
 * @param scaffoldState The state of the scaffold.
 * @param topBar An optional top app bar.
 * @param snackbarHost The composable hosting the snackbars shown inside the scaffold.
 * @param floatingActionButton An optional floating action button.
 * @param floatingActionButtonPosition The position of the floating action button.
 * @param sheetGesturesEnabled Whether the bottom sheet can be interacted with by gestures.
 * @param sheetShape The shape of the bottom sheet.
 * @param sheetElevation The elevation of the bottom sheet.
 * @param sheetBackgroundColor The background color of the bottom sheet.
 * @param sheetContentColor The preferred content color provided by the bottom sheet to its
 * children. Defaults to the matching `onFoo` color for [sheetBackgroundColor], or if that is
 * not a color from the theme, this will keep the same content color set above the bottom sheet.
 * @param sheetPeekHeight The height of the bottom sheet when it is collapsed.
 * @param drawerContent The content of the drawer sheet.
 * @param drawerGesturesEnabled Whether the drawer sheet can be interacted with by gestures.
 * @param drawerShape The shape of the drawer sheet.
 * @param drawerElevation The elevation of the drawer sheet.
 * @param drawerBackgroundColor The background color of the drawer sheet.
 * @param drawerContentColor The preferred content color provided by the drawer sheet to its
 * children. Defaults to the matching `onFoo` color for [drawerBackgroundColor], or if that is
 * not a color from the theme, this will keep the same content color set above the drawer sheet.
 * @param drawerScrimColor The color of the scrim that is applied when the drawer is open.
 * @param bodyContent The main content of the screen. You should use the provided [PaddingValues]
 * to properly offset the content, so that it is not obstructed by the bottom sheet when collapsed.
 */
@Composable
@ExperimentalMaterialApi
@OptIn(ExperimentalAnimationApi::class)
fun BottomSheetScaffold(
    sheetContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    scaffoldState: BottomSheetScaffoldState = rememberBottomSheetScaffoldState(),
    topBar: (@Composable () -> Unit)? = null,
    snackbarHost: @Composable (SnackbarHostState) -> Unit = { SnackbarHost(it) },
    floatingActionButton: (@Composable () -> Unit)? = null,
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    sheetGesturesEnabled: Boolean = true,
    sheetShape: Shape = MaterialTheme.shapes.large,
    sheetElevation: Dp = BottomSheetScaffoldConstants.DefaultSheetElevation,
    sheetBackgroundColor: Color = MaterialTheme.colors.surface,
    sheetContentColor: Color = contentColorFor(sheetBackgroundColor),
    sheetPeekHeight: Dp = BottomSheetScaffoldConstants.DefaultSheetPeekHeight,
    drawerContent: @Composable (ColumnScope.() -> Unit)? = null,
    drawerGesturesEnabled: Boolean = true,
    drawerShape: Shape = MaterialTheme.shapes.large,
    drawerElevation: Dp = DrawerConstants.DefaultElevation,
    drawerBackgroundColor: Color = MaterialTheme.colors.surface,
    drawerContentColor: Color = contentColorFor(drawerBackgroundColor),
    drawerScrimColor: Color = DrawerConstants.defaultScrimColor,
    backgroundColor: Color = MaterialTheme.colors.background,
    contentColor: Color = contentColorFor(backgroundColor),
    bodyContent: @Composable (PaddingValues) -> Unit
) {
    WithConstraints(modifier) {
        val fullHeight = constraints.maxHeight.toFloat()
        val peekHeightPx = with(DensityAmbient.current) { sheetPeekHeight.toPx() }
        var bottomSheetHeight by remember { mutableStateOf(fullHeight) }

        val swipeable = Modifier.swipeable(
            state = scaffoldState.bottomSheetState,
            anchors = mapOf(
                fullHeight - peekHeightPx to BottomSheetValue.Collapsed,
                fullHeight - bottomSheetHeight to BottomSheetValue.Expanded
            ),
            thresholds = { _, _ -> FixedThreshold(56.dp) },
            orientation = Orientation.Vertical,
            enabled = sheetGesturesEnabled,
            resistance = null
        )

        val child = @Composable {
            BottomSheetScaffoldStack(
                body = {
                    Surface(
                        color = backgroundColor,
                        contentColor = contentColor
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            topBar?.invoke()
                            bodyContent(PaddingValues(bottom = sheetPeekHeight))
                        }
                    }
                },
                bottomSheet = {
                    Surface(
                        swipeable
                            .fillMaxWidth()
                            .heightIn(min = sheetPeekHeight)
                            .onPositioned { bottomSheetHeight = it.size.height.toFloat() },
                        shape = sheetShape,
                        elevation = sheetElevation,
                        color = sheetBackgroundColor,
                        contentColor = sheetContentColor,
                        content = { Column(children = sheetContent) }
                    )
                },
                floatingActionButton = {
                    Stack(Modifier.zIndex(FabZIndex)) {
                        floatingActionButton?.invoke()
                    }
                },
                snackbarHost = {
                    Stack(Modifier.zIndex(SnackbarZIndex)) {
                        snackbarHost(scaffoldState.snackbarHostState)
                    }
                },
                bottomSheetOffset = scaffoldState.bottomSheetState.offset,
                floatingActionButtonPosition = floatingActionButtonPosition
            )
        }
        if (drawerContent == null) {
            child()
        } else {
            ModalDrawerLayout(
                drawerContent = drawerContent,
                drawerState = scaffoldState.drawerState,
                gesturesEnabled = drawerGesturesEnabled,
                drawerShape = drawerShape,
                drawerElevation = drawerElevation,
                drawerBackgroundColor = drawerBackgroundColor,
                drawerContentColor = drawerContentColor,
                scrimColor = drawerScrimColor,
                bodyContent = child
            )
        }
    }
}

@Composable
private fun BottomSheetScaffoldStack(
    body: @Composable () -> Unit,
    bottomSheet: @Composable () -> Unit,
    floatingActionButton: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
    bottomSheetOffset: State<Float>,
    floatingActionButtonPosition: FabPosition
) {
    Layout(children = {
        body()
        bottomSheet()
        floatingActionButton()
        snackbarHost()
    }) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints)

        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)

            val (sheetPlaceable, fabPlaceable, snackbarPlaceable) =
                measurables.drop(1).map {
                    it.measure(constraints.copy(minWidth = 0, minHeight = 0))
                }

            val sheetOffsetY = bottomSheetOffset.value.roundToInt()

            sheetPlaceable.placeRelative(0, sheetOffsetY)

            val fabOffsetX = when (floatingActionButtonPosition) {
                FabPosition.Center -> (placeable.width - fabPlaceable.width) / 2
                FabPosition.End -> placeable.width - fabPlaceable.width - FabEndSpacing.toIntPx()
            }
            val fabOffsetY = sheetOffsetY - fabPlaceable.height / 2

            fabPlaceable.placeRelative(fabOffsetX, fabOffsetY)

            val snackbarOffsetX = (placeable.width - snackbarPlaceable.width) / 2
            val snackbarOffsetY = placeable.height - snackbarPlaceable.height

            snackbarPlaceable.placeRelative(snackbarOffsetX, snackbarOffsetY)
        }
    }
}

private val FabEndSpacing = 16.dp
private val FabZIndex = 8f
private val SnackbarZIndex = Float.POSITIVE_INFINITY

/**
 * Contains useful constants for [BottomSheetScaffold].
 */
object BottomSheetScaffoldConstants {

    /**
     * The default elevation used by [BottomSheetScaffold].
     */
    val DefaultSheetElevation = 8.dp

    /**
     * The default peek height used by [BottomSheetScaffold].
     */
    val DefaultSheetPeekHeight = 56.dp
}