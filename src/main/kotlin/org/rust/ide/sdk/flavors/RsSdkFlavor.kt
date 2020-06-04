/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import org.rust.ide.icons.RsIcons
import org.rust.ide.sdk.RsSdkAdditionalData
import org.rust.lang.RsConstants.CARGO
import org.rust.lang.RsConstants.RUSTC
import org.rust.stdext.toPath
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon

interface RsSdkFlavor {
    val name: String get() = "Rust"
    val icon: Icon get() = RsIcons.RUST

    fun suggestHomePaths(module: Module?): Sequence<String> = emptySequence()

    /**
     * Flavor is added to result in [getApplicableFlavors] if this method returns true.
     * If the only condition is independence of platform, then [isPlatformIndependent] should be used.
     * @return whether this flavor is applicable
     */
    fun isApplicable(): Boolean = false

    /**
     * Used for distinguishing platform flavors from platform-independent ones in [getPlatformIndependentFlavors].
     * @return whether the flavor is platform independent
     */
    fun isPlatformIndependent(): Boolean = false

    fun isValidSdkHome(path: String): Boolean {
        val file = File(path)
        return file.isFile && isValidSdkPath(file)
    }

    fun isValidSdkPath(sdkHome: File): Boolean =
        sdkHome.hasExecutable(RUSTC) && sdkHome.hasExecutable(CARGO)

    fun getSdkPath(path: VirtualFile?): VirtualFile? = path

    companion object {
        @JvmField
        val EP_NAME: ExtensionPointName<RsSdkFlavor> = ExtensionPointName.create("org.rust.rustSdkFlavor")

        fun getApplicableFlavors(addPlatformIndependent: Boolean = true): List<RsSdkFlavor> =
            EP_NAME.extensionList.filter { flavor ->
                flavor.isApplicable() || addPlatformIndependent && flavor.isPlatformIndependent()
            }

        fun getPlatformIndependentFlavors(): List<RsSdkFlavor> =
            EP_NAME.extensionList.filter { flavor -> flavor.isPlatformIndependent() }

        fun getFlavor(sdk: Sdk): RsSdkFlavor? {
            val flavor = (sdk.sdkAdditionalData as? RsSdkAdditionalData)?.flavor
            if (flavor != null) return flavor
            return getFlavor(sdk.homePath)
        }

        fun getFlavor(sdkPath: String?): RsSdkFlavor? {
            if (sdkPath == null) return null
            return getApplicableFlavors().find { flavor -> flavor.isValidSdkHome(sdkPath) }
        }

        fun getPlatformIndependentFlavor(sdkPath: String?): RsSdkFlavor? {
            if (sdkPath == null) return null
            return getPlatformIndependentFlavors().find { flavor -> flavor.isValidSdkHome(sdkPath) }
        }

        private fun File.hasExecutable(toolName: String): Boolean =
            Files.isExecutable(pathToExecutable(toolName))

        private fun File.pathToExecutable(toolName: String): Path {
            val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
            return resolve(exeName).absolutePath.toPath()
        }
    }
}
