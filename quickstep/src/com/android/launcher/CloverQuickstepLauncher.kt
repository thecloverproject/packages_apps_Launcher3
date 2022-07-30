/*
 * Copyright (C) 2024-2025 The Clover Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher

import android.app.smartspace.SmartspaceTarget
import android.os.Bundle
import com.android.launcher.CloverLauncherModelDelegate.SmartspaceItem
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.qsb.LauncherUnlockAnimationController
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.quickstep.SystemUiProxy
import com.google.android.systemui.smartspace.BcSmartspaceDataProvider

class CloverQuickstepLauncher : QuickstepLauncher() {

    companion object {
        private const val TAG = "CloverQuickstepLauncher"
    }

    private val mSmartspacePlugin = BcSmartspaceDataProvider()
    private val mUnlockAnimationController = LauncherUnlockAnimationController(this)

    fun getSmartspacePlugin(): BcSmartspaceDataProvider {
        return mSmartspacePlugin
    }

    fun getLauncherUnlockAnimationController(): LauncherUnlockAnimationController {
        return mUnlockAnimationController
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        SystemUiProxy.INSTANCE.get(this).setLauncherUnlockAnimationController(
            this.javaClass.simpleName,
            mUnlockAnimationController
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        SystemUiProxy.INSTANCE.get(this).setLauncherUnlockAnimationController("null", null)
    }

    override fun onOverlayVisibilityChanged(visible: Boolean) {
        super.onOverlayVisibilityChanged(visible)
        mUnlockAnimationController.updateSmartspaceState()
    }

    override fun onPageEndTransition() {
        super.onPageEndTransition()
        mUnlockAnimationController.updateSmartspaceState()
    }

    override fun bindExtraContainerItems(container: BgDataModel.FixedContainerItems) {
        if (container.containerId == -110) {
            val targets = container.items
                .filterIsInstance<SmartspaceItem>()
                .map { it.smartspaceTarget }
            
            mSmartspacePlugin.onTargetsAvailable(targets)
        }
        super.bindExtraContainerItems(container)
    }
}
