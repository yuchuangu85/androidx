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

package androidx.camera.core.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link UseCaseConfigFactory} that uses {@link ConfigProvider}s to provide configurations.
 */

public final class ExtendableUseCaseConfigFactory implements UseCaseConfigFactory {
    private final Map<Class<?>, ConfigProvider<?>> mDefaultProviders = new HashMap<>();

    /** Inserts or overrides the {@link ConfigProvider} for the given config type. */
    public <C extends Config> void installDefaultProvider(
            @NonNull Class<C> configType, @NonNull ConfigProvider<C> defaultProvider) {
        mDefaultProviders.put(configType, defaultProvider);
    }

    @Nullable
    @Override
    public <C extends UseCaseConfig<?>> C getConfig(@NonNull Class<C> configType) {
        @SuppressWarnings("unchecked") // Providers only could have been inserted with
                // installDefaultProvider(), so the class should return the correct type.
                ConfigProvider<C> provider = (ConfigProvider<C>) mDefaultProviders.get(configType);
        if (provider != null) {
            return provider.getConfig();
        }
        return null;
    }
}
