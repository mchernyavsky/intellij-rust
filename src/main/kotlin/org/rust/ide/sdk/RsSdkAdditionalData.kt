package org.rust.ide.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import org.jdom.Element
import org.rust.ide.sdk.flavors.RsSdkFlavor

class RsSdkAdditionalData(val flavor: RsSdkFlavor?) : SdkAdditionalData {
    var toolchainName: String? = null
    var stdlibPath: String? = null

    private constructor(from: RsSdkAdditionalData) : this(from.flavor) {
        toolchainName = from.toolchainName
        stdlibPath = from.stdlibPath
    }

    fun copy(): RsSdkAdditionalData = RsSdkAdditionalData(this)

    fun save(rootElement: Element) {
        toolchainName?.let { rootElement.setAttribute(TOOLCHAIN_NAME, it) }
        stdlibPath?.let { rootElement.setAttribute(STDLIB_PATH, it) }
    }

    private fun load(element: Element?) {
        if (element == null) return
        toolchainName = element.getAttributeValue(TOOLCHAIN_NAME)
        stdlibPath = element.getAttributeValue(STDLIB_PATH)
    }

    companion object {
        private const val TOOLCHAIN_NAME: String = "TOOLCHAIN_NAME"
        private const val STDLIB_PATH: String = "STDLIB_PATH"

        fun load(sdk: Sdk, element: Element?): RsSdkAdditionalData {
            val data = RsSdkAdditionalData(RsSdkFlavor.getFlavor(sdk.homePath))
            data.load(element)
            return data
        }
    }
}
