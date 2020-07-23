/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import org.rust.ide.sdk.flavors.RsSdkFlavor
import org.rust.ide.sdk.flavors.RustupSdkFlavor

object RsSdkUtils {

    fun findOrCreateSdk(): Sdk? {
        val allSdks = ProjectJdkTable.getInstance().allJdks.toList()
        val existingSdk = allSdks.find { it.sdkType is RsSdkType }
        if (existingSdk != null) return existingSdk
        val detectedSdk = detectRustSdks(allSdks).firstOrNull() ?: return null
        return detectedSdk.setup(allSdks)
    }

    fun findSdkByKey(key: String): Sdk? = ProjectJdkTable.getInstance().findJdk(key, RsSdkType.getInstance().name)

    fun detectRustSdks(existingSdks: List<Sdk>): List<RsDetectedSdk> =
        detectRustupSdks(existingSdks) + detectSystemWideSdks(existingSdks)

    fun detectRustupSdks(existingSdks: List<Sdk>): List<RsDetectedSdk> {
        val flavors = listOf(RustupSdkFlavor)
        return detectSdks(flavors, existingSdks)
    }

    fun detectSystemWideSdks(existingSdks: List<Sdk>): List<RsDetectedSdk> {
        val flavors = RsSdkFlavor.getApplicableFlavors().filterNot { it is RustupSdkFlavor }
        return detectSdks(flavors, existingSdks)
    }

    private fun detectSdks(flavors: List<RsSdkFlavor>, existingSdks: List<Sdk>): List<RsDetectedSdk> {
        val existingPaths = existingSdks.map { it.homePath }.toSet()
        return flavors.asSequence()
            .flatMap { it.suggestHomePaths() }
            .map { it.absolutePath }
            .distinct()
            .filterNot { it in existingPaths }
            .map { RsDetectedSdk(it) }
            .toList()
    }

    fun isInvalid(sdk: Sdk): Boolean {
        val toolchain = sdk.homeDirectory
        return toolchain == null || !toolchain.exists()
    }
}
