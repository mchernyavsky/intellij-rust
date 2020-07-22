/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapiext.isUnitTestMode
import com.intellij.util.io.exists
import com.intellij.util.text.SemVer
import org.rust.cargo.CargoConstants.XARGO_MANIFEST_FILE
import org.rust.cargo.toolchain.RustcVersion.Companion.scrapeRustcVersion
import org.rust.openapiext.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

data class RustToolchain(val location: Path, val name: String? = null) {

    fun looksLikeValidToolchain(): Boolean =
        hasExecutable(CARGO) && hasExecutable(RUSTC)

    fun queryVersions(): VersionInfo {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        return VersionInfo(scrapeRustcVersion(pathToExecutable(RUSTC)))
    }

    fun getSysroot(projectDirectory: Path): String? {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        val timeoutMs = 10000
        val output = GeneralCommandLine(pathToExecutable(RUSTC))
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(projectDirectory)
            .withParameters("--print", "sysroot")
            .execute(timeoutMs)
        return if (output?.isSuccess == true) output.stdout.trim() else null
    }

    fun getCfgOptions(projectDirectory: Path): List<String>? {
        val timeoutMs = 10000
        val output = GeneralCommandLine(pathToExecutable(RUSTC))
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(projectDirectory)
            .withParameters("--print", "cfg")
            .execute(timeoutMs)
        return if (output?.isSuccess == true) output.stdoutLines else null
    }

    fun rawCargo(): Cargo = Cargo(pathToExecutable(CARGO))

    fun cargoOrWrapper(cargoProjectDirectory: Path?): Cargo {
        val hasXargoToml = cargoProjectDirectory?.resolve(XARGO_MANIFEST_FILE)?.let { Files.isRegularFile(it) } == true
        val cargoWrapper = if (hasXargoToml && hasExecutable(XARGO)) XARGO else CARGO
        return Cargo(pathToExecutable(cargoWrapper))
    }

    fun rustfmt(): Rustfmt = Rustfmt(pathToExecutable(RUSTFMT))

    fun grcov(): Grcov? = if (hasCargoExecutable(GRCOV)) Grcov(pathToCargoExecutable(GRCOV)) else null

    fun evcxr(): Evcxr? = if (hasCargoExecutable(EVCXR)) Evcxr(pathToCargoExecutable(EVCXR)) else null

    private fun pathToExecutable(toolName: String): Path {
        val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
        return location.resolve("bin/$exeName").toAbsolutePath()
    }

    // for executables installed using `cargo install`
    private fun pathToCargoExecutable(toolName: String): Path {
        // Binaries installed by `cargo install` (e.g. Grcov, Evcxr) are placed in ~/.cargo/bin by default:
        // https://doc.rust-lang.org/cargo/commands/cargo-install.html
        // But toolchain root may be different (e.g. on Arch Linux it is usually /usr/bin)
        val path = pathToExecutable(toolName)
        if (path.exists()) return path

        val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
        val cargoBinPath = File(FileUtil.expandUserHome("~/.cargo/bin")).toPath()
        return cargoBinPath.resolve(exeName).toAbsolutePath()
    }

    private fun hasExecutable(exec: String): Boolean =
        Files.isExecutable(pathToExecutable(exec))

    private fun hasCargoExecutable(exec: String): Boolean =
        Files.isExecutable(pathToCargoExecutable(exec))

    data class VersionInfo(
        val rustc: RustcVersion?
    )

    companion object {
        private const val RUSTC = "rustc"
        private const val RUSTFMT = "rustfmt"
        private const val CARGO = "cargo"
        private const val XARGO = "xargo"
        private const val GRCOV = "grcov"
        private const val EVCXR = "evcxr"

        val MIN_SUPPORTED_TOOLCHAIN = SemVer.parseFromText("1.32.0")!!
    }
}
