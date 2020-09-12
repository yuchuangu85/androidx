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

package androidx.mediarouter.media;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.media.MediaRoute2ProviderService;
import android.media.RoutingSessionInfo;
import android.os.Build;
import android.support.mediacompat.testlib.util.PollingCheck;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.mediarouter.media.MediaRouter.RouteInfo;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Tests features related to {@link android.media.MediaRouter2}.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
public class MediaRouter2Test {
    private static final String TAG = "MR2Test";
    private static final int TIMEOUT_MS = 5000;

    Context mContext;
    MediaRouter mRouter;
    StubMediaRouteProviderService.StubMediaRouteProvider mProvider;
    MediaRoute2ProviderServiceAdapter mMr2ProviderServiceAdapter;

    List<MediaRouter.Callback> mCallbacks;
    MediaRouteSelector mSelector;

    // Maps descriptor ID to RouteInfo for convenience.
    Map<String, RouteInfo> mRoutes;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        getInstrumentation().runOnMainSync(() -> mRouter = MediaRouter.getInstance(mContext));

        mCallbacks = new ArrayList<>();
        // Set a default selector.
        mSelector = new MediaRouteSelector.Builder()
                .addControlCategory(StubMediaRouteProviderService.CATEGORY_TEST)
                .build();

        new PollingCheck(TIMEOUT_MS) {
            @Override
            protected boolean check() {
                StubMediaRouteProviderService.StubMediaRouteProvider provider =
                        StubMediaRouteProviderService.getProvider();
                if (provider != null) {
                    mProvider = provider;
                    mMr2ProviderServiceAdapter =
                            StubMediaRouteProviderService.getMr2ProviderServiceAdapter();
                    return true;
                }
                return false;
            }
        }.run();
        getInstrumentation().runOnMainSync(() -> {
            mProvider.initializeRoutes();
            mProvider.publishRoutes();
        });
    }

    @After
    public void tearDown() {
        getInstrumentation().runOnMainSync(() -> {
            for (MediaRouter.Callback callback : mCallbacks) {
                mRouter.removeCallback(callback);
            }
            mCallbacks.clear();
        });
    }

    @Test
    @SmallTest
    public void selectFromMr1AndStopFromSystem_unselect() throws Exception {
        CountDownLatch onRouteSelectedLatch = new CountDownLatch(1);
        CountDownLatch onRouteUnselectedLatch = new CountDownLatch(1);
        String descriptorId = StubMediaRouteProviderService.ROUTE_ID1;

        addCallback(new MediaRouter.Callback() {
            @Override
            public void onRouteSelected(@NonNull MediaRouter router,
                    @NonNull RouteInfo selectedRoute, int reason,
                    @NonNull RouteInfo requestedRoute) {
                if (TextUtils.equals(selectedRoute.getDescriptorId(), descriptorId)
                        && reason == MediaRouter.UNSELECT_REASON_ROUTE_CHANGED) {
                    onRouteSelectedLatch.countDown();
                }
            }

            @Override
            public void onRouteUnselected(MediaRouter router, RouteInfo route, int reason) {
                if (TextUtils.equals(route.getDescriptorId(), descriptorId)
                        && reason == MediaRouter.UNSELECT_REASON_STOPPED) {
                    onRouteUnselectedLatch.countDown();
                }
            }
        });
        waitForRoutesAdded();
        assertNotNull(mRoutes);

        RouteInfo routeToSelect = mRoutes.get(descriptorId);
        assertNotNull(routeToSelect);

        getInstrumentation().runOnMainSync(() -> mRouter.selectRoute(routeToSelect));
        assertTrue(onRouteSelectedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

        // Wait for a session being created.
        PollingCheck.waitFor(TIMEOUT_MS,
                () -> !mMr2ProviderServiceAdapter.getAllSessionInfo().isEmpty());
        //TODO: Find a correct session info
        for (RoutingSessionInfo sessionInfo : mMr2ProviderServiceAdapter.getAllSessionInfo()) {
            mMr2ProviderServiceAdapter.onReleaseSession(MediaRoute2ProviderService.REQUEST_ID_NONE,
                    sessionInfo.getId());
        }
        assertTrue(onRouteUnselectedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    void addCallback(MediaRouter.Callback callback) {
        getInstrumentation().runOnMainSync(() -> {
            mRouter.addCallback(mSelector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
                            | MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
        });
        mCallbacks.add(callback);
    }

    void waitForRoutesAdded() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        MediaRouter.Callback callback = new MediaRouter.Callback() {
            @Override
            public void onRouteAdded(MediaRouter router, RouteInfo route) {
                if (!route.isDefaultOrBluetooth()) {
                    latch.countDown();
                }
            }
        };

        addCallback(callback);

        latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        getInstrumentation().runOnMainSync(() -> mRoutes = mRouter.getRoutes().stream().collect(
                Collectors.toMap(route -> route.getDescriptorId(), route -> route)));
    }
}
