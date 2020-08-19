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

package androidx.navigation.dynamicfeatures.fragment.ui

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.util.Log
import androidx.annotation.RestrictTo
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.dynamicfeatures.Constants
import androidx.navigation.dynamicfeatures.DynamicExtras
import androidx.navigation.dynamicfeatures.DynamicInstallMonitor
import androidx.navigation.fragment.findNavController
import com.google.android.play.core.common.IntentSenderForResultStarter
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus

/**
 * The base class for [Fragment]s that handle dynamic feature installation.
 *
 * When extending from this class, you are responsible for forwarding installation state changes
 * to your UI via the provided hooks in [onCancelled], [onFailed], [onProgress].
 *
 * The installation process itself is handled within the [AbstractProgressFragment] itself.
 * Navigation to the target destination will occur once the installation is completed.
 */
abstract class AbstractProgressFragment : Fragment {

    internal companion object {
        private const val INSTALL_REQUEST_CODE = 1
        private const val TAG = "AbstractProgress"
    }

    private val installViewModel: InstallViewModel by viewModels {
        InstallViewModel.FACTORY
    }
    private val destinationId by lazy {
        requireArguments().getInt(Constants.DESTINATION_ID)
    }
    private val destinationArgs: Bundle? by lazy {
        requireArguments().getBundle(Constants.DESTINATION_ARGS)
    }
    private var navigated = false

    constructor()

    constructor(contentLayoutId: Int) : super(contentLayoutId)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            navigated = savedInstanceState.getBoolean(Constants.KEY_NAVIGATED, false)
        }
    }

    override fun onResume() {
        super.onResume()
        if (navigated) {
            findNavController().popBackStack()
            return
        }
        var monitor = installViewModel.installMonitor
        if (monitor == null) {
            Log.i(TAG, "onResume: monitor is null, navigating")
            navigate()
            monitor = installViewModel.installMonitor
        }
        if (monitor != null) {
            Log.i(TAG, "onResume: monitor is now not null, observing")
            monitor.status.observe(this, StateObserver(monitor))
        }
    }

    /**
     * Navigates to an installed dynamic feature module or kicks off installation.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    protected fun navigate() {
        Log.i(TAG, "navigate: ")
        val installMonitor = DynamicInstallMonitor()
        val extras = DynamicExtras(installMonitor)
        findNavController().navigate(destinationId, destinationArgs, null, extras)
        if (!installMonitor.isInstallRequired) {
            Log.i(TAG, "navigate: install not required")
            navigated = true
        } else {
            Log.i(TAG, "navigate: setting install monitor")
            installViewModel.installMonitor = installMonitor
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(Constants.KEY_NAVIGATED, navigated)
    }

    private inner class StateObserver constructor(private val monitor: DynamicInstallMonitor) :
        Observer<SplitInstallSessionState> {

        override fun onChanged(sessionState: SplitInstallSessionState?) {
            if (sessionState != null) {
                if (sessionState.hasTerminalStatus()) {
                    monitor.status.removeObserver(this)
                }
                when (sessionState.status()) {
                    SplitInstallSessionStatus.INSTALLED -> {
                        onInstalled()
                        navigate()
                    }
                    SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> try {
                        val splitInstallManager = monitor.splitInstallManager
                        if (splitInstallManager == null) {
                            onFailed(SplitInstallErrorCode.INTERNAL_ERROR)
                            return
                        }
                        splitInstallManager.startConfirmationDialogForResult(
                            sessionState,
                            IntentSenderForResultStarter { intent,
                                                           requestCode,
                                                           fillInIntent,
                                                           flagsMask,
                                                           flagsValues,
                                                           extraFlags,
                                                           options ->
                                startIntentSenderForResult(
                                    intent,
                                    requestCode,
                                    fillInIntent,
                                    flagsMask,
                                    flagsValues,
                                    extraFlags,
                                    options
                                )
                            }, INSTALL_REQUEST_CODE
                        )
                    } catch (e: IntentSender.SendIntentException) {
                        onFailed(SplitInstallErrorCode.INTERNAL_ERROR)
                    }
                    SplitInstallSessionStatus.CANCELED -> onCancelled()
                    SplitInstallSessionStatus.FAILED -> onFailed(sessionState.errorCode())
                    SplitInstallSessionStatus.UNKNOWN ->
                        onFailed(SplitInstallErrorCode.INTERNAL_ERROR)
                    SplitInstallSessionStatus.CANCELING,
                    SplitInstallSessionStatus.DOWNLOADED,
                    SplitInstallSessionStatus.DOWNLOADING,
                    SplitInstallSessionStatus.INSTALLING,
                    SplitInstallSessionStatus.PENDING -> {
                        onProgress(
                            sessionState.status(),
                            sessionState.bytesDownloaded(),
                            sessionState.totalBytesToDownload()
                        )
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == INSTALL_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_CANCELED) {
                onCancelled()
            }
        }
    }

    /**
     * Called when there was a progress update for an active module download.
     *
     * @param status the current installation status from SplitInstallSessionStatus
     * @param bytesDownloaded The bytes downloaded so far.
     * @param bytesTotal The total bytes to be downloaded (can be 0 for some status updates)
     */
    protected abstract fun onProgress(
        @SplitInstallSessionStatus status: Int,
        bytesDownloaded: Long,
        bytesTotal: Long
    )

    /**
     * Called when the user decided to cancel installation.
     */
    protected abstract fun onCancelled()

    /**
     * Called when the installation has failed due to non-user issues.
     *
     * Please check [SplitInstallErrorCode] for error code constants.
     *
     * @param errorCode contains the error code of the installation failure.
     */
    protected abstract fun onFailed(@SplitInstallErrorCode errorCode: Int)

    /**
     * Called when requested module has been successfully installed, just before the
     * [NavController][androidx.navigation.NavController] navigates to the final destination.
     */
    protected open fun onInstalled() = Unit
}
