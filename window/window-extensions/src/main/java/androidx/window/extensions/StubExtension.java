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

package androidx.window.extensions;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Basic implementation of the {@link ExtensionInterface}. An OEM can choose to use it as the base
 * class for their implementation.
 */
abstract class StubExtension implements ExtensionInterface {
    private ExtensionCallback mExtensionCallback;
    private final Set<Context> mWindowLayoutChangeListenerContexts = new HashSet<>();
    private boolean mDeviceStateChangeListenerRegistered;

    @Override
    public void setExtensionCallback(@NonNull ExtensionCallback callback) {
        mExtensionCallback = callback;
    }

    @Override
    public void onWindowLayoutChangeListenerAdded(@NonNull Context context) {
        mWindowLayoutChangeListenerContexts.add(context);
        onListenersChanged();
    }

    @Override
    public void onWindowLayoutChangeListenerRemoved(@NonNull Context context) {
        mWindowLayoutChangeListenerContexts.remove(context);
        onListenersChanged();
    }

    @Override
    public void onDeviceStateListenersChanged(boolean isEmpty) {
        mDeviceStateChangeListenerRegistered = !isEmpty;
        onListenersChanged();
    }

    protected void updateDeviceState(@NonNull ExtensionDeviceState newState) {
        if (mExtensionCallback != null) {
            mExtensionCallback.onDeviceStateChanged(newState);
        }
    }

    protected void updateWindowLayout(@NonNull Context context,
            @NonNull ExtensionWindowLayoutInfo newLayout) {
        if (mExtensionCallback != null) {
            mExtensionCallback.onWindowLayoutChanged(context, newLayout);
        }
    }

    @NonNull
    protected Set<Context> getWindowsListeningForLayoutChanges() {
        return mWindowLayoutChangeListenerContexts;
    }

    protected boolean hasListeners() {
        return !mWindowLayoutChangeListenerContexts.isEmpty()
                || mDeviceStateChangeListenerRegistered;
    }

    /** Notification to the OEM level that the registered listeners changed. */
    protected abstract void onListenersChanged();
}
