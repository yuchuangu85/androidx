/*
 * Copyright (C) 2019 The Android Open Source Project
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

package androidx.camera.testing.fakes;

import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.UseCase;
import androidx.camera.core.impl.SessionConfig;
import androidx.camera.core.impl.UseCaseConfig;

/**
 * A fake {@link UseCase}.
 */
public class FakeUseCase extends UseCase {
    private volatile boolean mIsDetached = false;

    /**
     * Creates a new instance of a {@link FakeUseCase} with a given configuration.
     */
    public FakeUseCase(@NonNull FakeUseCaseConfig config) {
        super(config);
    }

    /**
     * Creates a new instance of a {@link FakeUseCase} with a default configuration.
     */
    public FakeUseCase() {
        this(new FakeUseCaseConfig.Builder().getUseCaseConfig());
    }

    @Override
    @Nullable
    public UseCaseConfig.Builder<?, ?, ?> getDefaultBuilder() {
        return new FakeUseCaseConfig.Builder()
                .setSessionOptionUnpacker(new SessionConfig.OptionUnpacker() {
                    @Override
                    public void unpack(@NonNull UseCaseConfig<?> useCaseConfig,
                            @NonNull SessionConfig.Builder sessionConfigBuilder) {
                    }
                });
    }

    @Override
    public void onDetached() {
        super.onDetached();
        mIsDetached = true;
    }

    @NonNull
    @Override
    public UseCaseConfig.Builder<?, ?, ?> getUseCaseConfigBuilder() {
        return null;
    }

    @Override
    @NonNull
    protected Size onSuggestedResolutionUpdated(@NonNull Size suggestedResolution) {
        return suggestedResolution;
    }

    /**
     * Returns true if {@link #onDetached()} has been called previously.
     */
    public boolean isDetached() {
        return mIsDetached;
    }
}
