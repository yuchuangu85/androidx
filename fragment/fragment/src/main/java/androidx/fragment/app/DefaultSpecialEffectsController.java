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

package androidx.fragment.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.core.app.SharedElementCallback;
import androidx.core.os.CancellationSignal;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewGroupCompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A SpecialEffectsController that hooks into the existing Fragment APIs to run
 * animations and transitions.
 */
class DefaultSpecialEffectsController extends SpecialEffectsController {

    private final HashMap<Operation, HashSet<CancellationSignal>>
            mRunningOperations = new HashMap<>();

    DefaultSpecialEffectsController(@NonNull ViewGroup container) {
        super(container);
    }

    /**
     * Add new {@link CancellationSignal} for special effects
     */
    private void addCancellationSignal(@NonNull Operation operation,
            @NonNull CancellationSignal signal) {
        if (mRunningOperations.get(operation) == null) {
            mRunningOperations.put(operation, new HashSet<CancellationSignal>());
        }
        mRunningOperations.get(operation).add(signal);
    }

    /**
     * Remove a {@link CancellationSignal} that was previously added with
     * {@link #addCancellationSignal(Operation, CancellationSignal)}.
     *
     * This calls through to {@link Operation#complete()} when the last special effect is complete.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void removeCancellationSignal(@NonNull Operation operation,
            @NonNull CancellationSignal signal) {
        HashSet<CancellationSignal> signals = mRunningOperations.get(operation);
        if (signals != null && signals.remove(signal) && signals.isEmpty()) {
            mRunningOperations.remove(operation);
            operation.complete();
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void cancelAllSpecialEffects(@NonNull Operation operation) {
        HashSet<CancellationSignal> signals = mRunningOperations.remove(operation);
        if (signals != null) {
            for (CancellationSignal signal : signals) {
                signal.cancel();
            }
        }
    }

    @Override
    void executeOperations(@NonNull List<Operation> operations, boolean isPop) {
        // Shared element transitions are done between the first fragment leaving and
        // the last fragment coming in. Finding these operations is the first priority
        Operation firstOut = null;
        Operation lastIn = null;
        for (final Operation operation : operations) {
            Operation.State currentState = Operation.State.from(operation.getFragment().mView);
            switch (operation.getFinalState()) {
                case GONE:
                case INVISIBLE:
                case REMOVED:
                    if (currentState == Operation.State.VISIBLE && firstOut == null) {
                        // The firstOut Operation is the first Operation moving from VISIBLE
                        firstOut = operation;
                    }
                    break;
                case VISIBLE:
                    if (currentState != Operation.State.VISIBLE) {
                        // The last Operation that moves to VISIBLE is the lastIn Operation
                        lastIn = operation;
                    }
                    break;
            }
        }

        // Now iterate through the operations, collecting the set of animations
        // and transitions that need to be executed
        List<AnimationInfo> animations = new ArrayList<>();
        List<TransitionInfo> transitions = new ArrayList<>();
        final List<Operation> awaitingContainerChanges = new ArrayList<>(operations);

        for (final Operation operation : operations) {
            // Create the animation CancellationSignal
            CancellationSignal animCancellationSignal = new CancellationSignal();
            addCancellationSignal(operation, animCancellationSignal);
            // Add the animation special effect
            animations.add(new AnimationInfo(operation, animCancellationSignal));

            // Create the transition CancellationSignal
            CancellationSignal transitionCancellationSignal = new CancellationSignal();
            addCancellationSignal(operation, transitionCancellationSignal);
            // Add the transition special effect
            transitions.add(new TransitionInfo(operation, transitionCancellationSignal, isPop,
                    isPop ? operation == firstOut : operation == lastIn));

            // When the operation completes, make sure that the view that had requested
            // focus before the operation started has its focus requested again
            operation.addCompletionListener(new Runnable() {
                @Override
                public void run() {
                    if (operation.getFinalState() == Operation.State.VISIBLE) {
                        View focusedView = operation.getFragment().getFocusedView();
                        if (focusedView != null) {
                            focusedView.requestFocus();
                            operation.getFragment().setFocusedView(null);
                        }
                    }
                }
            });
            // Ensure that if the Operation is synchronously complete, we still
            // apply the container changes before the Operation completes
            operation.addCompletionListener(new Runnable() {
                @Override
                public void run() {
                    if (awaitingContainerChanges.contains(operation)) {
                        awaitingContainerChanges.remove(operation);
                        applyContainerChanges(operation);
                    }
                }
            });
            // Ensure that when the Operation is cancelled, we cancel all special effects
            operation.getCancellationSignal().setOnCancelListener(
                    new CancellationSignal.OnCancelListener() {
                        @Override
                        public void onCancel() {
                            cancelAllSpecialEffects(operation);
                        }
                    });
        }

        // Start transition special effects
        Map<Operation, Boolean> startedTransitions = startTransitions(transitions, isPop,
                firstOut, lastIn);
        boolean startedAnyTransition = startedTransitions.containsValue(true);

        // Start animation special effects
        for (AnimationInfo animationInfo : animations) {
            Operation operation = animationInfo.getOperation();
            boolean startedTransition = startedTransitions.containsKey(operation)
                    ? startedTransitions.get(operation)
                    : false;
            startAnimation(operation, animationInfo.getSignal(),
                    startedAnyTransition, startedTransition);
        }

        for (final Operation operation : awaitingContainerChanges) {
            applyContainerChanges(operation);
        }
        awaitingContainerChanges.clear();
    }

    private void startAnimation(final @NonNull Operation operation,
            final @NonNull CancellationSignal signal, boolean startedAnyTransition,
            boolean startedTransitions) {
        final ViewGroup container = getContainer();
        final Context context = container.getContext();
        final Fragment fragment = operation.getFragment();
        final View viewToAnimate = fragment.mView;
        Operation.State currentState = Operation.State.from(viewToAnimate);
        Operation.State finalState = operation.getFinalState();
        if (currentState == finalState || (currentState != Operation.State.VISIBLE
                && finalState != Operation.State.VISIBLE)) {
            // No change in visibility, so we can immediately remove the CancellationSignal
            removeCancellationSignal(operation, signal);
            return;
        }
        FragmentAnim.AnimationOrAnimator anim = FragmentAnim.loadAnimation(context,
                fragment, finalState == Operation.State.VISIBLE);
        if (anim == null) {
            // No animation, so we can immediately remove the CancellationSignal
            removeCancellationSignal(operation, signal);
            return;
        } else if (startedAnyTransition && anim.animation != null) {
            if (FragmentManager.isLoggingEnabled(Log.VERBOSE)) {
                Log.v(FragmentManager.TAG, "Ignoring Animation set on "
                        + fragment + " as Animations cannot run alongside Transitions.");
            }
            removeCancellationSignal(operation, signal);
            return;
        } else if (startedTransitions) {
            if (FragmentManager.isLoggingEnabled(Log.VERBOSE)) {
                Log.v(FragmentManager.TAG, "Ignoring Animator set on "
                        + fragment + " as this Fragment was involved in a Transition.");
            }
            removeCancellationSignal(operation, signal);
            return;
        }
        // We have an animation to run!
        container.startViewTransition(viewToAnimate);
        // Kick off the respective type of animation
        if (anim.animation != null) {
            final Animation animation = operation.getFinalState() == Operation.State.VISIBLE
                    ? new FragmentAnim.EnterViewTransitionAnimation(anim.animation)
                    : new FragmentAnim.EndViewTransitionAnimation(anim.animation, container,
                            viewToAnimate);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    // onAnimationEnd() comes during draw(), so there can still be some
                    // draw events happening after this call. We don't want to remove the
                    // CancellationSignal until after the onAnimationEnd()
                    container.post(new Runnable() {
                        @Override
                        public void run() {
                            container.endViewTransition(viewToAnimate);
                            removeCancellationSignal(operation, signal);
                        }
                    });
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            viewToAnimate.startAnimation(animation);
        } else { // anim.animator != null
            anim.animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator anim) {
                    container.endViewTransition(viewToAnimate);
                    removeCancellationSignal(operation, signal);
                }
            });
            anim.animator.setTarget(viewToAnimate);
            anim.animator.start();
        }

        // Listen for cancellation and use that to cancel any running animations
        signal.setOnCancelListener(new CancellationSignal.OnCancelListener() {
            @Override
            public void onCancel() {
                viewToAnimate.clearAnimation();
            }
        });
    }

    @NonNull
    private Map<Operation, Boolean> startTransitions(@NonNull List<TransitionInfo> transitionInfos,
            final boolean isPop, @Nullable final Operation firstOut,
            @Nullable final Operation lastIn) {
        Map<Operation, Boolean> startedTransitions = new HashMap<>();
        // First verify that we can run all transitions together
        FragmentTransitionImpl transitionImpl = null;
        for (TransitionInfo transitionInfo : transitionInfos) {
            if (transitionInfo.isVisibilityUnchanged()) {
                // No change in visibility, so we can skip this TransitionInfo
                continue;
            }
            FragmentTransitionImpl handlingImpl = transitionInfo.getHandlingImpl();
            if (transitionImpl == null) {
                transitionImpl = handlingImpl;
            } else if (handlingImpl != null && transitionImpl != handlingImpl) {
                throw new IllegalArgumentException("Mixing framework transitions and "
                        + "AndroidX transitions is not allowed. Fragment "
                        + transitionInfo.getOperation().getFragment() + " returned Transition "
                        + transitionInfo.getTransition() + " which uses a different Transition "
                        + " type than other Fragments.");
            }
        }
        if (transitionImpl == null) {
            // There were no transitions at all so we can just cancel all of them
            for (TransitionInfo transitionInfo : transitionInfos) {
                startedTransitions.put(transitionInfo.getOperation(), false);
                removeCancellationSignal(transitionInfo.getOperation(),
                        transitionInfo.getSignal());
            }
            return startedTransitions;
        }

        // Every transition needs to target at least one View so that they
        // don't interfere with one another. This is the view we use
        // in cases where there are no real views to target
        final View nonExistentView = new View(getContainer().getContext());

        // Now find the shared element transition if it exists
        Object sharedElementTransition = null;
        View firstOutEpicenterView = null;
        boolean hasLastInEpicenter = false;
        final Rect lastInEpicenterRect = new Rect();
        ArrayList<View> sharedElementFirstOutViews = new ArrayList<>();
        ArrayList<View> sharedElementLastInViews = new ArrayList<>();
        ArrayMap<String, String> sharedElementNameMapping = new ArrayMap<>();
        for (final TransitionInfo transitionInfo : transitionInfos) {
            boolean hasSharedElementTransition = transitionInfo.hasSharedElementTransition();
            // Compute the shared element transition between the firstOut and lastIn Fragments
            if (hasSharedElementTransition && firstOut != null && lastIn != null) {
                // swapSharedElementTargets requires wrapping this in a TransitionSet
                sharedElementTransition = transitionImpl.wrapTransitionInSet(
                        transitionImpl.cloneTransition(
                                transitionInfo.getSharedElementTransition()));
                // The exiting shared elements default to the source names from the
                // last in fragment
                ArrayList<String> exitingNames = lastIn.getFragment()
                        .getSharedElementSourceNames();
                // But if we're doing multiple transactions, we may need to re-map
                // the names from the first out fragment
                ArrayList<String> firstOutSourceNames = firstOut.getFragment()
                        .getSharedElementSourceNames();
                ArrayList<String> firstOutTargetNames = firstOut.getFragment()
                        .getSharedElementTargetNames();
                // We do this by iterating through each first out target,
                // seeing if there is a match from the last in sources
                for (int index = 0; index < firstOutTargetNames.size(); index++) {
                    int nameIndex = exitingNames.indexOf(firstOutTargetNames.get(index));
                    if (nameIndex != -1) {
                        // If we found a match, replace the last in source name
                        // with the first out source name
                        exitingNames.set(nameIndex, firstOutSourceNames.get(index));
                    }
                }
                ArrayList<String> enteringNames = lastIn.getFragment()
                        .getSharedElementTargetNames();
                SharedElementCallback exitingCallback;
                SharedElementCallback enteringCallback;
                if (!isPop) {
                    // Forward transitions have firstOut fragment exiting and the
                    // lastIn fragment entering
                    exitingCallback = firstOut.getFragment().getExitTransitionCallback();
                    enteringCallback = lastIn.getFragment().getEnterTransitionCallback();
                } else {
                    // A pop is the reverse: the firstOut fragment is entering and the
                    // lastIn fragment is exiting
                    exitingCallback = firstOut.getFragment().getEnterTransitionCallback();
                    enteringCallback = lastIn.getFragment().getExitTransitionCallback();
                }
                int numSharedElements = exitingNames.size();
                for (int i = 0; i < numSharedElements; i++) {
                    String exitingName = exitingNames.get(i);
                    String enteringName = enteringNames.get(i);
                    sharedElementNameMapping.put(exitingName, enteringName);
                }

                // Find all of the Views from the firstOut fragment that are
                // part of the shared element transition
                final ArrayMap<String, View> firstOutViews = new ArrayMap<>();
                findNamedViews(firstOutViews, firstOut.getFragment().mView);
                firstOutViews.retainAll(exitingNames);
                if (exitingCallback != null) {
                    // Give the SharedElementCallback a chance to override the default mapping
                    exitingCallback.onMapSharedElements(exitingNames, firstOutViews);
                    for (int i = exitingNames.size() - 1; i >= 0; i--) {
                        String name = exitingNames.get(i);
                        View view = firstOutViews.get(name);
                        if (view == null) {
                            sharedElementNameMapping.remove(name);
                        } else if (!name.equals(ViewCompat.getTransitionName(view))) {
                            String targetValue = sharedElementNameMapping.remove(name);
                            sharedElementNameMapping.put(ViewCompat.getTransitionName(view),
                                    targetValue);
                        }
                    }
                } else {
                    // Only keep the mapping of elements that were found in the firstOut Fragment
                    sharedElementNameMapping.retainAll(firstOutViews.keySet());
                }

                // Find all of the Views from the lastIn fragment that are
                // part of the shared element transition
                final ArrayMap<String, View> lastInViews = new ArrayMap<>();
                findNamedViews(lastInViews, lastIn.getFragment().mView);
                lastInViews.retainAll(enteringNames);
                lastInViews.retainAll(sharedElementNameMapping.values());
                if (enteringCallback != null) {
                    // Give the SharedElementCallback a chance to override the default mapping
                    enteringCallback.onMapSharedElements(enteringNames, lastInViews);
                    for (int i = enteringNames.size() - 1; i >= 0; i--) {
                        String name = enteringNames.get(i);
                        View view = lastInViews.get(name);
                        if (view == null) {
                            String key = FragmentTransition.findKeyForValue(
                                    sharedElementNameMapping, name);
                            if (key != null) {
                                sharedElementNameMapping.remove(key);
                            }
                        } else if (!name.equals(ViewCompat.getTransitionName(view))) {
                            String key = FragmentTransition.findKeyForValue(
                                    sharedElementNameMapping, name);
                            if (key != null) {
                                sharedElementNameMapping.put(key,
                                        ViewCompat.getTransitionName(view));
                            }
                        }
                    }
                } else {
                    // Only keep the mapping of elements that were found in the lastIn Fragment
                    FragmentTransition.retainValues(sharedElementNameMapping, lastInViews);
                }

                // Now make a final pass through the Views list to ensure they
                // don't still have elements that were removed from the mapping
                retainMatchingViews(firstOutViews, sharedElementNameMapping.keySet());
                retainMatchingViews(lastInViews, sharedElementNameMapping.values());

                if (sharedElementNameMapping.isEmpty()) {
                    // We couldn't find any valid shared element mappings, so clear out
                    // the shared element transition information entirely
                    sharedElementTransition = null;
                    sharedElementFirstOutViews.clear();
                    sharedElementLastInViews.clear();
                } else {
                    // Call through to onSharedElementStart() before capturing the
                    // starting values for the shared element transition
                    FragmentTransition.callSharedElementStartEnd(
                            lastIn.getFragment(), firstOut.getFragment(), isPop,
                            firstOutViews, true);
                    // Trigger the onSharedElementEnd callback in the next frame after
                    // the starting values are captured and before capturing the end states
                    OneShotPreDrawListener.add(getContainer(), new Runnable() {
                        @Override
                        public void run() {
                            FragmentTransition.callSharedElementStartEnd(
                                    lastIn.getFragment(), firstOut.getFragment(), isPop,
                                    lastInViews, false);
                        }
                    });

                    // Capture all views from the firstOut Fragment under the shared element views
                    for (View sharedElementView : firstOutViews.values()) {
                        captureTransitioningViews(sharedElementFirstOutViews,
                                sharedElementView);
                    }

                    // Compute the epicenter of the firstOut transition
                    if (!exitingNames.isEmpty()) {
                        String epicenterViewName = exitingNames.get(0);
                        firstOutEpicenterView = firstOutViews.get(epicenterViewName);
                        transitionImpl.setEpicenter(sharedElementTransition,
                                firstOutEpicenterView);
                    }

                    // Capture all views from the lastIn Fragment under the shared element views
                    for (View sharedElementView : lastInViews.values()) {
                        captureTransitioningViews(sharedElementLastInViews,
                                sharedElementView);
                    }

                    // Compute the epicenter of the lastIn transition
                    if (!enteringNames.isEmpty()) {
                        String epicenterViewName = enteringNames.get(0);
                        final View lastInEpicenterView = lastInViews.get(epicenterViewName);
                        if (lastInEpicenterView != null) {
                            hasLastInEpicenter = true;
                            // We can't set the epicenter here directly since the View might
                            // not have been laid out as of yet, so instead we set a Rect as
                            // the epicenter and compute the bounds one frame later
                            final FragmentTransitionImpl impl = transitionImpl;
                            OneShotPreDrawListener.add(getContainer(), new Runnable() {
                                @Override
                                public void run() {
                                    impl.getBoundsOnScreen(lastInEpicenterView,
                                            lastInEpicenterRect);
                                }
                            });
                        }
                    }

                    // Now set the transition's targets to only the firstOut Fragment's views
                    // It'll be swapped to the lastIn Fragment's views after the
                    // transition is started
                    transitionImpl.setSharedElementTargets(sharedElementTransition,
                            nonExistentView, sharedElementFirstOutViews);
                    // After the swap to the lastIn Fragment's view (done below), we
                    // need to clean up those targets. We schedule this here so that it
                    // runs directly after the swap
                    transitionImpl.scheduleRemoveTargets(sharedElementTransition,
                            null, null, null, null,
                            sharedElementTransition, sharedElementLastInViews);
                    // Both the firstOut and lastIn Operations are now associated
                    // with a Transition
                    startedTransitions.put(firstOut, true);
                    startedTransitions.put(lastIn, true);
                }
            }
        }
        ArrayList<View> enteringViews = new ArrayList<>();
        // These transitions run together, overlapping one another
        Object mergedTransition = null;
        // These transitions run only after all of the other transitions complete
        Object mergedNonOverlappingTransition = null;
        // Now iterate through the set of transitions and merge them together
        for (final TransitionInfo transitionInfo : transitionInfos) {
            if (transitionInfo.isVisibilityUnchanged()) {
                // No change in visibility, so we can immediately remove the CancellationSignal
                startedTransitions.put(transitionInfo.getOperation(), false);
                removeCancellationSignal(transitionInfo.getOperation(),
                        transitionInfo.getSignal());
                continue;
            }
            Object transition = transitionImpl.cloneTransition(transitionInfo.getTransition());
            Operation operation = transitionInfo.getOperation();
            boolean involvedInSharedElementTransition = sharedElementTransition != null
                    && (operation == firstOut || operation == lastIn);
            if (transition == null) {
                // Nothing more to do if the transition is null
                if (!involvedInSharedElementTransition) {
                    // Only remove the cancellation signal if this fragment isn't involved
                    // in the shared element transition (as otherwise we need to wait
                    // for that to finish)
                    startedTransitions.put(operation, false);
                    removeCancellationSignal(operation, transitionInfo.getSignal());
                }
            } else {
                // Target the Transition to *only* the set of transitioning views
                final ArrayList<View> transitioningViews = new ArrayList<>();
                captureTransitioningViews(transitioningViews,
                        operation.getFragment().mView);
                if (involvedInSharedElementTransition) {
                    // Remove all of the shared element views from the transition
                    if (operation == firstOut) {
                        transitioningViews.removeAll(sharedElementFirstOutViews);
                    } else {
                        transitioningViews.removeAll(sharedElementLastInViews);
                    }
                }
                if (transitioningViews.isEmpty()) {
                    transitionImpl.addTarget(transition, nonExistentView);
                } else {
                    transitionImpl.addTargets(transition, transitioningViews);
                    transitionImpl.scheduleRemoveTargets(transition,
                            transition, transitioningViews,
                            null, null, null, null);
                    if (operation.getFinalState() == Operation.State.GONE) {
                        // We're hiding the Fragment. This requires a bit of extra work
                        transitionImpl.scheduleHideFragmentView(transition,
                                operation.getFragment().mView,
                                transitioningViews);
                        // This OneShotPreDrawListener gets fired before the delayed start of
                        // the Transition and changes the visibility of any exiting child views
                        // that *ARE NOT* shared element transitions. The TransitionManager then
                        // properly considers exiting views and marks them as disappearing,
                        // applying a transition and a listener to take proper actions once the
                        // transition is complete.
                        OneShotPreDrawListener.add(getContainer(), new Runnable() {
                            @Override
                            public void run() {
                                FragmentTransition.setViewVisibility(transitioningViews,
                                        View.INVISIBLE);
                            }
                        });
                    }
                }
                if (operation.getFinalState() == Operation.State.VISIBLE) {
                    enteringViews.addAll(transitioningViews);
                    if (hasLastInEpicenter) {
                        transitionImpl.setEpicenter(transition, lastInEpicenterRect);
                    }
                } else {
                    transitionImpl.setEpicenter(transition, firstOutEpicenterView);
                }
                startedTransitions.put(operation, true);
                // Now determine how this transition should be merged together
                if (transitionInfo.isOverlapAllowed()) {
                    // Overlap is allowed, so add them to the mergeTransition set
                    mergedTransition = transitionImpl.mergeTransitionsTogether(
                            mergedTransition, transition, null);
                } else {
                    // Overlap is not allowed, add them to the mergedNonOverlappingTransition
                    mergedNonOverlappingTransition = transitionImpl.mergeTransitionsTogether(
                            mergedNonOverlappingTransition, transition, null);
                }
            }
        }

        // Make sure that the mergedNonOverlappingTransition set
        // runs after the mergedTransition set is complete
        mergedTransition = transitionImpl.mergeTransitionsInSequence(mergedTransition,
                mergedNonOverlappingTransition, sharedElementTransition);

        // Now set up our cancellation and completion signal on the completely
        // merged transition set
        for (final TransitionInfo transitionInfo : transitionInfos) {
            if (transitionInfo.isVisibilityUnchanged()) {
                // No change in visibility, so we've already removed the CancellationSignal
                continue;
            }
            Object transition = transitionInfo.getTransition();
            if (transition != null) {
                transitionImpl.setListenerForTransitionEnd(
                        transitionInfo.getOperation().getFragment(),
                        mergedTransition,
                        transitionInfo.getSignal(),
                        new Runnable() {
                            @Override
                            public void run() {
                                removeCancellationSignal(transitionInfo.getOperation(),
                                        transitionInfo.getSignal());
                            }
                        });
            }
        }
        // First, hide all of the entering views so they're in
        // the correct initial state
        FragmentTransition.setViewVisibility(enteringViews, View.INVISIBLE);
        ArrayList<String> inNames =
                transitionImpl.prepareSetNameOverridesReordered(sharedElementLastInViews);
        // Now actually start the transition
        transitionImpl.beginDelayedTransition(getContainer(), mergedTransition);
        transitionImpl.setNameOverridesReordered(getContainer(), sharedElementFirstOutViews,
                sharedElementLastInViews, inNames, sharedElementNameMapping);
        // Then, show all of the entering views, putting them into
        // the correct final state
        FragmentTransition.setViewVisibility(enteringViews, View.VISIBLE);
        transitionImpl.swapSharedElementTargets(sharedElementTransition,
                sharedElementFirstOutViews, sharedElementLastInViews);
        return startedTransitions;
    }

    /**
     * Retain only the shared element views that have a transition name that is in
     * the set of transition names.
     *
     * @param sharedElementViews The map of shared element transitions that should be filtered.
     * @param transitionNames The set of transition names to be retained.
     */
    void retainMatchingViews(@NonNull ArrayMap<String, View> sharedElementViews,
            @NonNull Collection<String> transitionNames) {
        Iterator<Map.Entry<String, View>> iterator = sharedElementViews.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, View> entry = iterator.next();
            if (!transitionNames.contains(ViewCompat.getTransitionName(entry.getValue()))) {
                iterator.remove();
            }
        }
    }

    /**
     * Gets the Views in the hierarchy affected by entering and exiting transitions.
     *
     * @param transitioningViews This View will be added to transitioningViews if it is VISIBLE and
     *                           a normal View or a ViewGroup with
     *                           {@link android.view.ViewGroup#isTransitionGroup()} true.
     * @param view               The base of the view hierarchy to look in.
     */
    void captureTransitioningViews(ArrayList<View> transitioningViews, View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            if (ViewGroupCompat.isTransitionGroup(viewGroup)) {
                transitioningViews.add(viewGroup);
            } else {
                int count = viewGroup.getChildCount();
                for (int i = 0; i < count; i++) {
                    View child = viewGroup.getChildAt(i);
                    if (child.getVisibility() == View.VISIBLE) {
                        captureTransitioningViews(transitioningViews, child);
                    }
                }
            }
        } else {
            transitioningViews.add(view);
        }
    }

    /**
     * Finds all views that have transition names in the hierarchy under the given view and
     * stores them in {@code namedViews} map with the name as the key.
     */
    void findNamedViews(Map<String, View> namedViews, @NonNull View view) {
        String transitionName = ViewCompat.getTransitionName(view);
        if (transitionName != null) {
            namedViews.put(transitionName, view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            int count = viewGroup.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = viewGroup.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE) {
                    findNamedViews(namedViews, child);
                }
            }
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void applyContainerChanges(@NonNull Operation operation) {
        View view = operation.getFragment().mView;
        operation.getFinalState().applyState(view);
    }

    private static class AnimationInfo {
        @NonNull
        private final Operation mOperation;
        @NonNull
        private final CancellationSignal mSignal;

        AnimationInfo(@NonNull Operation operation, @NonNull CancellationSignal signal) {
            mOperation = operation;
            mSignal = signal;
        }

        @NonNull
        Operation getOperation() {
            return mOperation;
        }

        @NonNull
        CancellationSignal getSignal() {
            return mSignal;
        }
    }

    private static class TransitionInfo {
        @NonNull
        private final Operation mOperation;
        @NonNull
        private final CancellationSignal mSignal;
        @Nullable
        private final Object mTransition;
        private final boolean mOverlapAllowed;
        @Nullable
        private final Object mSharedElementTransition;

        TransitionInfo(@NonNull Operation operation,
                @NonNull CancellationSignal signal, boolean isPop,
                boolean providesSharedElementTransition) {
            mOperation = operation;
            mSignal = signal;
            if (operation.getFinalState() == Operation.State.VISIBLE) {
                mTransition = isPop
                        ? operation.getFragment().getReenterTransition()
                        : operation.getFragment().getEnterTransition();
                // Entering transitions can choose to run after all exit
                // transitions complete, rather than overlapping with them
                mOverlapAllowed = isPop
                        ? operation.getFragment().getAllowReturnTransitionOverlap()
                        : operation.getFragment().getAllowEnterTransitionOverlap();
            } else {
                mTransition = isPop
                        ? operation.getFragment().getReturnTransition()
                        : operation.getFragment().getExitTransition();
                // Removing Fragments always overlap other transitions
                mOverlapAllowed = true;
            }
            if (providesSharedElementTransition) {
                if (isPop) {
                    mSharedElementTransition =
                            operation.getFragment().getSharedElementReturnTransition();
                } else {
                    mSharedElementTransition =
                            operation.getFragment().getSharedElementEnterTransition();
                }
            } else {
                mSharedElementTransition = null;
            }
        }

        @NonNull
        Operation getOperation() {
            return mOperation;
        }

        @NonNull
        CancellationSignal getSignal() {
            return mSignal;
        }

        boolean isVisibilityUnchanged() {
            Operation.State currentState = Operation.State.from(mOperation.getFragment().mView);
            Operation.State finalState = mOperation.getFinalState();
            return currentState == finalState || (currentState != Operation.State.VISIBLE
                    && finalState != Operation.State.VISIBLE);
        }

        @Nullable
        Object getTransition() {
            return mTransition;
        }

        boolean isOverlapAllowed() {
            return mOverlapAllowed;
        }

        public boolean hasSharedElementTransition() {
            return mSharedElementTransition != null;
        }

        @Nullable
        public Object getSharedElementTransition() {
            return mSharedElementTransition;
        }

        @Nullable
        FragmentTransitionImpl getHandlingImpl() {
            FragmentTransitionImpl transitionImpl = getHandlingImpl(mTransition);
            FragmentTransitionImpl sharedElementTransitionImpl =
                    getHandlingImpl(mSharedElementTransition);
            if (transitionImpl != null && sharedElementTransitionImpl != null
                    && transitionImpl != sharedElementTransitionImpl) {
                throw new IllegalArgumentException("Mixing framework transitions and "
                        + "AndroidX transitions is not allowed. Fragment "
                        + mOperation.getFragment() + " returned Transition "
                        + mTransition + " which uses a different Transition "
                        + " type than its shared element transition "
                        + mSharedElementTransition);
            }
            return transitionImpl != null ? transitionImpl : sharedElementTransitionImpl;
        }

        @Nullable
        private FragmentTransitionImpl getHandlingImpl(Object transition) {
            if (transition == null) {
                return null;
            }
            if (FragmentTransition.PLATFORM_IMPL != null
                    && FragmentTransition.PLATFORM_IMPL.canHandle(transition)) {
                return FragmentTransition.PLATFORM_IMPL;
            }
            if (FragmentTransition.SUPPORT_IMPL != null
                    && FragmentTransition.SUPPORT_IMPL.canHandle(transition)) {
                return FragmentTransition.SUPPORT_IMPL;
            }
            throw new IllegalArgumentException("Transition " + transition + " for fragment "
                    + mOperation.getFragment() + " is not a valid framework Transition or "
                    + "AndroidX Transition");
        }
    }
}
