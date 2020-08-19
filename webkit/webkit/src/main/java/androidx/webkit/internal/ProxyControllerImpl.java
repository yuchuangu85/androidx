/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.webkit.internal;

import androidx.annotation.NonNull;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;

import org.chromium.support_lib_boundary.ProxyControllerBoundaryInterface;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Implementation of {@link ProxyController}.
 */
public class ProxyControllerImpl extends ProxyController {
    private ProxyControllerBoundaryInterface mBoundaryInterface;

    @Override
    public void setProxyOverride(@NonNull ProxyConfig proxyConfig, @NonNull Executor executor,
            @NonNull Runnable listener) {
        WebViewFeatureInternal feature = WebViewFeatureInternal.PROXY_OVERRIDE;
        if (feature.isSupportedByWebView()) {
            List<ProxyConfig.ProxyRule> proxyRulesList = proxyConfig.getProxyRules();

            // A 2D String array representation is required by reflection
            String[][] proxyRulesArray = new String[proxyRulesList.size()][2];
            for (int i = 0; i < proxyRulesList.size(); i++) {
                proxyRulesArray[i][0] = proxyRulesList.get(0).getSchemeFilter();
                proxyRulesArray[i][1] = proxyRulesList.get(0).getUrl();
            }

            getBoundaryInterface().setProxyOverride(proxyRulesArray,
                    proxyConfig.getBypassRules().toArray(new String[0]), listener, executor);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    @Override
    public void clearProxyOverride(@NonNull Executor executor, @NonNull Runnable listener) {
        WebViewFeatureInternal feature = WebViewFeatureInternal.PROXY_OVERRIDE;
        if (feature.isSupportedByWebView()) {
            getBoundaryInterface().clearProxyOverride(listener, executor);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    private ProxyControllerBoundaryInterface getBoundaryInterface() {
        if (mBoundaryInterface == null) {
            mBoundaryInterface = WebViewGlueCommunicator.getFactory().getProxyController();
        }
        return mBoundaryInterface;
    }
}
