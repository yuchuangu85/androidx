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

package androidx.build.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class AndroidXIssueRegistry : IssueRegistry() {
    override val minApi = CURRENT_API
    override val api = 8
    override val issues get(): List<Issue> {
        return Issues
    }

    companion object {
        val Issues get(): List<Issue> {
            return listOf(
                BanParcelableUsage.ISSUE,
                BanConcurrentHashMap.ISSUE,
                BanKeepAnnotation.ISSUE,
                BanTargetApiAnnotation.ISSUE,
                MissingTestSizeAnnotation.ISSUE,
                SampledAnnotationEnforcer.MISSING_SAMPLED_ANNOTATION,
                SampledAnnotationEnforcer.OBSOLETE_SAMPLED_ANNOTATION,
                SampledAnnotationEnforcer.MISSING_SAMPLES_DIRECTORY,
                SampledAnnotationEnforcer.UNRESOLVED_SAMPLE_LINK,
                SampledAnnotationEnforcer.MULTIPLE_FUNCTIONS_FOUND,
                SampledAnnotationEnforcer.INVALID_SAMPLES_LOCATION,
                ObsoleteBuildCompatUsageDetector.ISSUE,
                BanSynchronizedMethods.ISSUE,
                MetadataTagInsideApplicationTagDetector.ISSUE,
                PrivateConstructorForUtilityClass.ISSUE,
                UnsafeNewApiCallsDetector.ISSUE
            )
        }
    }
}
