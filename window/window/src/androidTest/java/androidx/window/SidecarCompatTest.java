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

package androidx.window;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.graphics.Rect;
import android.os.IBinder;
import android.view.Window;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.window.sidecar.SidecarDeviceState;
import androidx.window.sidecar.SidecarDisplayFeature;
import androidx.window.sidecar.SidecarInterface;
import androidx.window.sidecar.SidecarWindowLayoutInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link SidecarCompat} implementation of {@link ExtensionInterfaceCompat}. This class
 * uses a mocked Sidecar to verify the behavior of the implementation on any hardware.
 * <p>Because this class extends {@link SidecarCompatDeviceTest}, it will also run the mocked
 * versions of methods defined in {@link CompatDeviceTestInterface}.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public final class SidecarCompatTest extends SidecarCompatDeviceTest
        implements CompatTestInterface {
    private static final Rect WINDOW_BOUNDS = new Rect(1, 1, 50, 100);

    private Activity mActivity;

    @Before
    public void setUp() {
        mActivity = mock(Activity.class);
        mSidecarCompat = new SidecarCompat(mock(SidecarInterface.class));

        when(mActivity.getResources())
                .thenReturn(ApplicationProvider.getApplicationContext().getResources());

        Window window = spy(new TestWindow(mActivity));
        window.getAttributes().token = mock(IBinder.class);
        when(mActivity.getWindow()).thenReturn(window);

        TestWindowBoundsHelper mWindowBoundsHelper = new TestWindowBoundsHelper();
        mWindowBoundsHelper.setCurrentBounds(WINDOW_BOUNDS);
        WindowBoundsHelper.setForTesting(mWindowBoundsHelper);

        // Setup mocked sidecar responses
        SidecarDeviceState defaultDeviceState = new SidecarDeviceState();
        defaultDeviceState.posture = SidecarDeviceState.POSTURE_HALF_OPENED;
        when(mSidecarCompat.mSidecar.getDeviceState()).thenReturn(defaultDeviceState);

        SidecarDisplayFeature sidecarDisplayFeature = newDisplayFeature(
                new Rect(0, 1, WINDOW_BOUNDS.width(), 1), SidecarDisplayFeature.TYPE_HINGE);
        SidecarWindowLayoutInfo sidecarWindowLayoutInfo = new SidecarWindowLayoutInfo();
        sidecarWindowLayoutInfo.displayFeatures = new ArrayList<>();
        sidecarWindowLayoutInfo.displayFeatures.add(sidecarDisplayFeature);
        when(mSidecarCompat.mSidecar.getWindowLayoutInfo(any()))
                .thenReturn(sidecarWindowLayoutInfo);
    }

    @After
    public void tearDown() {
        WindowBoundsHelper.setForTesting(null);
    }

    @Test
    public void testGetWindowLayout_featureWithEmptyBounds() {
        // Add a feature with an empty bounds to the reported list
        SidecarWindowLayoutInfo originalWindowLayoutInfo =
                mSidecarCompat.mSidecar.getWindowLayoutInfo(getActivityWindowToken(mActivity));
        List<SidecarDisplayFeature> sidecarDisplayFeatures =
                originalWindowLayoutInfo.displayFeatures;
        SidecarDisplayFeature newFeature = new SidecarDisplayFeature();
        newFeature.setRect(new Rect());
        sidecarDisplayFeatures.add(newFeature);

        // Verify that this feature is skipped.
        WindowLayoutInfo windowLayoutInfo = mSidecarCompat.getWindowLayoutInfo(mActivity);

        assertEquals(sidecarDisplayFeatures.size() - 1,
                windowLayoutInfo.getDisplayFeatures().size());
    }

    @Test
    public void testGetWindowLayout_foldWithNonZeroArea() {
        SidecarWindowLayoutInfo originalWindowLayoutInfo =
                mSidecarCompat.mSidecar.getWindowLayoutInfo(mock(IBinder.class));
        List<SidecarDisplayFeature> sidecarDisplayFeatures =
                originalWindowLayoutInfo.displayFeatures;
        // Horizontal fold.
        sidecarDisplayFeatures.add(
                newDisplayFeature(new Rect(0, 1, WINDOW_BOUNDS.width(), 2),
                        SidecarDisplayFeature.TYPE_FOLD));
        // Vertical fold.
        sidecarDisplayFeatures.add(
                newDisplayFeature(new Rect(1, 0, 2, WINDOW_BOUNDS.height()),
                        SidecarDisplayFeature.TYPE_FOLD));

        // Verify that these features are skipped.
        WindowLayoutInfo windowLayoutInfo =
                mSidecarCompat.getWindowLayoutInfo(mActivity);

        assertEquals(sidecarDisplayFeatures.size() - 2,
                windowLayoutInfo.getDisplayFeatures().size());
    }

    @Test
    public void testGetWindowLayout_hingeNotSpanningEntireWindow() {
        SidecarWindowLayoutInfo originalWindowLayoutInfo =
                mSidecarCompat.mSidecar.getWindowLayoutInfo(mock(IBinder.class));
        List<SidecarDisplayFeature> sidecarDisplayFeatures =
                originalWindowLayoutInfo.displayFeatures;
        // Horizontal hinge.
        sidecarDisplayFeatures.add(
                newDisplayFeature(new Rect(0, 1, WINDOW_BOUNDS.width() - 1, 2),
                        SidecarDisplayFeature.TYPE_FOLD));
        // Vertical hinge.
        sidecarDisplayFeatures.add(
                newDisplayFeature(new Rect(1, 0, 2, WINDOW_BOUNDS.height() - 1),
                        SidecarDisplayFeature.TYPE_FOLD));

        // Verify that these features are skipped.
        WindowLayoutInfo windowLayoutInfo =
                mSidecarCompat.getWindowLayoutInfo(mActivity);

        assertEquals(sidecarDisplayFeatures.size() - 2,
                windowLayoutInfo.getDisplayFeatures().size());
    }

    @Test
    public void testGetWindowLayout_foldNotSpanningEntireWindow() {
        SidecarWindowLayoutInfo originalWindowLayoutInfo =
                mSidecarCompat.mSidecar.getWindowLayoutInfo(mock(IBinder.class));
        List<SidecarDisplayFeature> sidecarDisplayFeatures =
                originalWindowLayoutInfo.displayFeatures;
        // Horizontal fold.
        sidecarDisplayFeatures.add(
                newDisplayFeature(new Rect(0, 1, WINDOW_BOUNDS.width() - 1, 2),
                        SidecarDisplayFeature.TYPE_FOLD));
        // Vertical fold.
        sidecarDisplayFeatures.add(
                newDisplayFeature(new Rect(1, 0, 2, WINDOW_BOUNDS.height() - 1),
                        SidecarDisplayFeature.TYPE_FOLD));

        // Verify that these features are skipped.
        WindowLayoutInfo windowLayoutInfo =
                mSidecarCompat.getWindowLayoutInfo(mActivity);

        assertEquals(sidecarDisplayFeatures.size() - 2,
                windowLayoutInfo.getDisplayFeatures().size());
    }

    @Test
    @Override
    public void testSetExtensionCallback() {
        ArgumentCaptor<SidecarInterface.SidecarCallback> sidecarCallbackCaptor =
                ArgumentCaptor.forClass(SidecarInterface.SidecarCallback.class);

        // Verify that the sidecar got the callback set
        ExtensionInterfaceCompat.ExtensionCallbackInterface callback =
                mock(ExtensionInterfaceCompat.ExtensionCallbackInterface.class);
        mSidecarCompat.setExtensionCallback(callback);

        verify(mSidecarCompat.mSidecar).setSidecarCallback(sidecarCallbackCaptor.capture());

        // Verify that the callback set for sidecar propagates the device state callback
        SidecarDeviceState sidecarDeviceState = new SidecarDeviceState();
        sidecarDeviceState.posture = SidecarDeviceState.POSTURE_HALF_OPENED;

        sidecarCallbackCaptor.getValue().onDeviceStateChanged(sidecarDeviceState);
        ArgumentCaptor<DeviceState> deviceStateCaptor = ArgumentCaptor.forClass(DeviceState.class);
        verify(callback).onDeviceStateChanged(deviceStateCaptor.capture());
        assertEquals(DeviceState.POSTURE_HALF_OPENED, deviceStateCaptor.getValue().getPosture());

        // Verify that the callback set for sidecar propagates the window layout callback when a
        // window layout changed listener has been added.
        mSidecarCompat.onWindowLayoutChangeListenerAdded(mActivity);
        Rect bounds = new Rect(0, 1, WINDOW_BOUNDS.width(), 1);
        SidecarDisplayFeature sidecarDisplayFeature = newDisplayFeature(bounds,
                SidecarDisplayFeature.TYPE_HINGE);
        SidecarWindowLayoutInfo sidecarWindowLayoutInfo = new SidecarWindowLayoutInfo();
        sidecarWindowLayoutInfo.displayFeatures = new ArrayList<>();
        sidecarWindowLayoutInfo.displayFeatures.add(sidecarDisplayFeature);

        sidecarCallbackCaptor.getValue().onWindowLayoutChanged(getActivityWindowToken(mActivity),
                sidecarWindowLayoutInfo);
        ArgumentCaptor<WindowLayoutInfo> windowLayoutInfoCaptor =
                ArgumentCaptor.forClass(WindowLayoutInfo.class);
        verify(callback).onWindowLayoutChanged(eq(mActivity), windowLayoutInfoCaptor.capture());

        WindowLayoutInfo capturedLayout = windowLayoutInfoCaptor.getValue();
        assertEquals(1, capturedLayout.getDisplayFeatures().size());
        DisplayFeature capturedDisplayFeature = capturedLayout.getDisplayFeatures().get(0);
        assertEquals(DisplayFeature.TYPE_HINGE, capturedDisplayFeature.getType());
        assertEquals(bounds, capturedDisplayFeature.getBounds());
    }

    @Test
    public void testMissingCallToOnWindowLayoutChangedListenerAdded() {
        ArgumentCaptor<SidecarInterface.SidecarCallback> sidecarCallbackCaptor =
                ArgumentCaptor.forClass(SidecarInterface.SidecarCallback.class);

        // Verify that the sidecar got the callback set
        ExtensionInterfaceCompat.ExtensionCallbackInterface callback =
                mock(ExtensionInterfaceCompat.ExtensionCallbackInterface.class);
        mSidecarCompat.setExtensionCallback(callback);

        verify(mSidecarCompat.mSidecar).setSidecarCallback(sidecarCallbackCaptor.capture());

        // Verify that the callback set for sidecar propagates the window layout callback when a
        // window layout changed listener has been added.
        SidecarDisplayFeature sidecarDisplayFeature = new SidecarDisplayFeature();
        sidecarDisplayFeature.setType(SidecarDisplayFeature.TYPE_HINGE);
        Rect bounds = new Rect(1, 2, 3, 4);
        sidecarDisplayFeature.setRect(bounds);
        SidecarWindowLayoutInfo sidecarWindowLayoutInfo = new SidecarWindowLayoutInfo();
        sidecarWindowLayoutInfo.displayFeatures = new ArrayList<>();
        sidecarWindowLayoutInfo.displayFeatures.add(sidecarDisplayFeature);

        IBinder windowToken = mock(IBinder.class);
        sidecarCallbackCaptor.getValue().onWindowLayoutChanged(windowToken,
                sidecarWindowLayoutInfo);
        verifyZeroInteractions(callback);
    }

    @Test
    @Override
    public void testOnWindowLayoutChangeListenerAdded() {
        IBinder windowToken = getActivityWindowToken(mActivity);
        mSidecarCompat.onWindowLayoutChangeListenerAdded(mActivity);
        verify(mSidecarCompat.mSidecar).onWindowLayoutChangeListenerAdded(eq(windowToken));
    }

    @Test
    @Override
    public void testOnWindowLayoutChangeListenerRemoved() {
        IBinder windowToken = getActivityWindowToken(mActivity);
        mSidecarCompat.onWindowLayoutChangeListenerRemoved(mActivity);
        verify(mSidecarCompat.mSidecar).onWindowLayoutChangeListenerRemoved(eq(windowToken));
    }

    @Test
    @Override
    public void testOnDeviceStateListenersChanged() {
        mSidecarCompat.onDeviceStateListenersChanged(true);
        verify(mSidecarCompat.mSidecar).onDeviceStateListenersChanged(eq(true));
    }

    private static SidecarDisplayFeature newDisplayFeature(Rect rect, int type) {
        SidecarDisplayFeature feature = new SidecarDisplayFeature();
        feature.setRect(rect);
        feature.setType(type);
        return feature;
    }
}
