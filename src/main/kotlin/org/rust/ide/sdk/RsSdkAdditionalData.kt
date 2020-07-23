/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import org.jdom.Element
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.Rustup
import org.rust.ide.sdk.flavors.RustupSdkFlavor
import java.io.File
import java.nio.file.Paths

val Sdk.toolchain: RustToolchain?
    get() = rustData?.toolchain

val Sdk.rustup: Rustup?
    get() = if (isRustupAvailable) rustData?.rustup else null

val Sdk.isRustupAvailable: Boolean
    get() {
        val homePath = homePath?.let(::File) ?: return false
        return RustupSdkFlavor.isValidSdkHome(homePath)
    }

val Sdk.rustData: RsSdkAdditionalData?
    get() = sdkAdditionalData as? RsSdkAdditionalData

class RsSdkAdditionalData(
    var toolchainName: String? = null,
    var toolchainPath: String? = null,
    var rustupPath: String? = null,
    var stdlibPath: String? = null
) : SdkAdditionalData {
    val toolchain: RustToolchain?
        get() = toolchainPath?.let { RustToolchain(Paths.get(it), toolchainName) }

    val rustup: Rustup?
        get() = rustupPath?.let { Rustup(Paths.get(it)) }

    private constructor(from: RsSdkAdditionalData) : this() {
        toolchainName = from.toolchainName
        toolchainPath = from.toolchainPath
        rustupPath = from.rustupPath
        stdlibPath = from.stdlibPath
    }

    fun copy(): RsSdkAdditionalData = RsSdkAdditionalData(this)

    fun save(rootElement: Element) {
        toolchainName?.let { rootElement.setAttribute(TOOLCHAIN_NAME, it) }
        toolchainPath?.let { rootElement.setAttribute(TOOLCHAIN_PATH, it) }
        rustupPath?.let { rootElement.setAttribute(RUSTUP_PATH, it) }
        stdlibPath?.let { rootElement.setAttribute(STDLIB_PATH, it) }
    }

    private fun load(element: Element?) {
        if (element == null) return
        toolchainName = element.getAttributeValue(TOOLCHAIN_NAME)
        toolchainPath = element.getAttributeValue(TOOLCHAIN_PATH)
        rustupPath = element.getAttributeValue(RUSTUP_PATH)
        stdlibPath = element.getAttributeValue(STDLIB_PATH)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RsSdkAdditionalData

        if (toolchainName != other.toolchainName) return false
        if (toolchainPath != other.toolchainPath) return false
        if (rustupPath != other.rustupPath) return false
        if (stdlibPath != other.stdlibPath) return false

        return true
    }

    override fun hashCode(): Int {
        var result = toolchainName?.hashCode() ?: 0
        result = 31 * result + (toolchainPath?.hashCode() ?: 0)
        result = 31 * result + (rustupPath?.hashCode() ?: 0)
        result = 31 * result + (stdlibPath?.hashCode() ?: 0)
        return result
    }

    companion object {
        private const val TOOLCHAIN_NAME: String = "TOOLCHAIN_NAME"
        private const val TOOLCHAIN_PATH: String = "TOOLCHAIN_PATH"
        private const val STDLIB_PATH: String = "STDLIB_PATH"
        private const val RUSTUP_PATH: String = "RUSTUP_PATH"

        fun load(sdk: Sdk, element: Element?): RsSdkAdditionalData {
            val data = RsSdkAdditionalData()
            data.load(element)
            return data
        }
    }
}
