/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import com.intellij.openapi.util.SystemInfo

class UnixSdkFlavor private constructor() : RsSdkFlavor {

    override fun isApplicable(): Boolean = SystemInfo.isUnix && !SystemInfo.isMac

    companion object {
        fun getInstance(): RsSdkFlavor? = RsSdkFlavor.EP_NAME.findExtension(UnixSdkFlavor::class.java)
    }
}
