/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.ide.plugins.PluginManagerCore.isUnitTestMode
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.startup.StartupActivity
import org.rust.ide.sdk.RsSdkType.Companion.getSdkKey
import org.rust.ide.sdk.RsSdkUtils.findSdkByKey

class RsSdkUpdater : StartupActivity.Background {

    override fun runActivity(project: Project) {
        if (isUnitTestMode || project.isDisposed) return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Updating Rust Toolchains", false) {
            override fun run(indicator: ProgressIndicator) {
                val sdks = ProjectJdkTable.getInstance().getSdksOfType(RsSdkType.getInstance())
                for (sdk in sdks) {
                    updateLocalSdkVersion(sdk)
                }
            }
        })
    }

    companion object {

        fun updateLocalSdkVersion(sdk: Sdk) {
            val modificatorToRead = sdk.sdkModificator
            val versionString = sdk.sdkType.getVersionString(sdk)
            if (versionString != modificatorToRead.versionString) {
                changeSdkModificator(sdk) { modificatorToWrite ->
                    modificatorToWrite.versionString = versionString
                    true
                }
            }
        }

        private fun changeSdkModificator(sdk: Sdk, processor: (SdkModificator) -> Boolean) {
            val key = getSdkKey(sdk)
            TransactionGuard.getInstance().assertWriteSafeContext(ModalityState.defaultModalityState())
            invokeAndWaitIfNeeded {
                val sdkInsideInvoke = findSdkByKey(key)
                val effectiveModificator = sdkInsideInvoke?.sdkModificator ?: sdk.sdkModificator
                if (processor(effectiveModificator)) {
                    effectiveModificator.commitChanges()
                }
            }
        }
    }
}
