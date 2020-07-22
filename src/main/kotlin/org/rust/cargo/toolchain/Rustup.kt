/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.project.settings.rustup
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.util.DownloadResult
import org.rust.ide.actions.InstallComponentAction
import org.rust.ide.notifications.showBalloon
import org.rust.openapiext.GeneralCommandLine
import org.rust.openapiext.execute
import org.rust.openapiext.fullyRefreshDirectory
import java.nio.file.Path

class Rustup(private val rustupPath: Path) {

    fun downloadStdlib(owner: Disposable, toolchainName: String): DownloadResult<VirtualFile> {
        val toolchain = listToolchains().find { it.name == toolchainName }
            ?: return DownloadResult.Err("Invalid toolchain")

        // Sometimes we have stdlib but don't have write access to install it (for example, github workflow)
        if (needInstallComponent(toolchainName, "rust-src")) {
            val result = downloadComponent(owner, toolchainName, "rust-src")
            if (result is DownloadResult.Err) return result
        }

        val stdlibPath = FileUtil.join(toolchain.path, "lib/rustlib/src/rust/src")
        val stdlib = LocalFileSystem.getInstance().refreshAndFindFileByPath(stdlibPath)
            ?: return DownloadResult.Err("Failed to find stdlib")

        LOG.info("stdlib path: ${stdlib.path}")
        fullyRefreshDirectory(stdlib)
        return DownloadResult.Ok(stdlib)
    }

    fun downloadComponent(owner: Disposable, toolchainName: String, componentName: String): DownloadResult<Unit> =
        try {
            GeneralCommandLine(rustupPath)
                .withParameters("component", "add", "--toolchain", toolchainName, componentName)
                .execute(owner, false)
            DownloadResult.Ok(Unit)
        } catch (e: ExecutionException) {
            val message = "rustup failed: `${e.message}`"
            LOG.warn(message)
            DownloadResult.Err(message)
        }

    private fun needInstallComponent(toolchainName: String, componentName: String): Boolean {
        val isInstalled = listComponents(toolchainName)
            .find { (name, _) -> name.startsWith(componentName) }
            ?.isInstalled
            ?: return false

        return !isInstalled
    }

    private fun listComponents(toolchainName: String): List<Component> =
        GeneralCommandLine(rustupPath)
            .withParameters("component", "list", "--toolchain", toolchainName)
            .execute()
            ?.stdoutLines
            ?.map { Component.from(it) }
            .orEmpty()

    private data class Component(val name: String, val isInstalled: Boolean) {
        companion object {
            fun from(line: String): Component {
                val name = line.substringBefore(' ')
                val isInstalled = line.substringAfter(' ') in listOf("(installed)", "(default)")
                return Component(name, isInstalled)
            }
        }
    }

    fun listToolchains(): List<Toolchain> =
        GeneralCommandLine(rustupPath)
            .withParameters("toolchain", "list", "--verbose")
            .execute()
            ?.stdoutLines
            ?.map { Toolchain.from(it) }
            .orEmpty()

    data class Toolchain(val name: String, val path: String, val isDefault: Boolean) {
        override fun toString(): String = name

        companion object {
            fun from(line: String): Toolchain {
                val before = line.substringBefore('\t')
                val name = before.removeSuffix(" (default)")
                val isDefault = before.endsWith("(default)")
                val path = line.substringAfter('\t')
                return Toolchain(name, path, isDefault)
            }
        }
    }

    companion object {
        private val LOG: Logger = Logger.getInstance(Rustup::class.java)

        fun checkNeedInstallClippy(project: Project): Boolean = checkNeedInstallComponent(project, "clippy")

        fun checkNeedInstallRustfmt(project: Project): Boolean = checkNeedInstallComponent(project, "rustfmt")

        // We don't want to install the component if:
        // 1. It is already installed
        // 2. We don't have Rustup
        // 3. Rustup doesn't have this component
        private fun checkNeedInstallComponent(project: Project, componentName: String): Boolean {
            val rustup = project.rustup ?: return false
            val toolchainName = checkNotNull(project.toolchain?.name) { "Toolchain name can't be null" }
            val needInstall = rustup.needInstallComponent(toolchainName, componentName)

            if (needInstall) {
                project.showBalloon(
                    "${componentName.capitalize()} is not installed",
                    NotificationType.ERROR,
                    InstallComponentAction(toolchainName, componentName)
                )
            }

            return needInstall
        }
    }
}
