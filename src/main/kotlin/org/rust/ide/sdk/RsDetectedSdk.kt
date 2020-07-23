/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import org.rust.cargo.toolchain.Rustup
import org.rust.openapiext.computeWithCancelableProgress
import org.rust.stdext.toPath

class RsDetectedSdk(homePath: String) : ProjectJdkImpl(homePath, RsSdkType.getInstance()) {

    init {
        this.homePath = homePath
    }

    override fun getVersionString(): String? = ""

    fun setup(existingSdks: List<Sdk>): Sdk? {
        val data = RsSdkAdditionalData()

        val homePath = homePath
        if (homePath != null && isRustupAvailable) {
            val project = ProjectManager.getInstance().defaultProject
            val toolchain = project.computeWithCancelableProgress("Fetching default toolchain...") {
                val rustup = Rustup(homePath.toPath())
                rustup.listToolchains().find { it.isDefault }
            } ?: return null
            data.toolchainName = toolchain.name
            data.toolchainPath = toolchain.path
            data.rustupPath = homePath
        } else {
            data.toolchainPath = homePath
        }

        return SdkConfigurationUtil.setupSdk(
            existingSdks.toTypedArray(),
            homeDirectory ?: return null,
            RsSdkType.getInstance(),
            false,
            data,
            null
        )
    }
}
