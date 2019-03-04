/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.ExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.rust.openapiext.*
import java.nio.file.Path

private val LOG = Logger.getInstance(Rustup::class.java)

class Rustup(
    private val toolchain: RustToolchain,
    private val rustup: Path,
    private val projectDirectory: Path
) {
    @Suppress("unused")
    sealed class DownloadResult<out T> {
        class Ok<T>(val value: T) : DownloadResult<T>()
        class Err(val error: String) : DownloadResult<Nothing>()
    }

    data class Component(val name: String, val isInstalled: Boolean) {
        companion object {
            fun from(line: String): Component {
                val name = line.substringBefore(' ')
                val isInstalled = line.substringAfter(' ') in listOf("(installed)", "(default)")
                return Component(name, isInstalled)
            }
        }
    }

    fun listComponents(): List<Component> =
        GeneralCommandLine(rustup)
            .withWorkDirectory(projectDirectory)
            .withParameters("component", "list")
            .execute(null)
            ?.stdoutLines
            ?.map { Component.from(it) }
            .orEmpty()

    fun listToolchains(): List<String> =
        GeneralCommandLine(rustup)
            .withWorkDirectory(projectDirectory)
            .withParameters("toolchain", "list")
            .execute(null)
            ?.stdoutLines
            ?.map { it.substringBefore(' ') }
            .orEmpty()

    fun downloadStdlib(): DownloadResult<VirtualFile> {
        val downloadProcessOutput = GeneralCommandLine(rustup)
            .withWorkDirectory(projectDirectory)
            .withParameters("component", "add", "rust-src")
            .execute(null)

        return if (downloadProcessOutput?.isSuccess == true) {
            val sources = getStdlibFromSysroot() ?: return DownloadResult.Err("Failed to find stdlib in sysroot")
            fullyRefreshDirectory(sources)
            DownloadResult.Ok(sources)
        } else {
            val message = "rustup failed: `${downloadProcessOutput?.stderr ?: ""}`"
            LOG.warn(message)
            DownloadResult.Err(message)
        }
    }

    fun downloadComponent(owner: Disposable, componentName: String): DownloadResult<Unit> =
        try {
            GeneralCommandLine(rustup)
                .withWorkDirectory(projectDirectory)
                .withParameters("component", "add", componentName)
                .execute(owner, false)
            DownloadResult.Ok(Unit)
        } catch (e: ExecutionException) {
            val message = "rustup failed: `${e.message}`"
            LOG.warn(message)
            DownloadResult.Err(message)
        }

    fun getStdlibFromSysroot(): VirtualFile? {
        val sysroot = toolchain.getSysroot(projectDirectory) ?: return null
        val fs = LocalFileSystem.getInstance()
        return fs.refreshAndFindFileByPath(FileUtil.join(sysroot, "lib/rustlib/src/rust/src"))
    }
}
