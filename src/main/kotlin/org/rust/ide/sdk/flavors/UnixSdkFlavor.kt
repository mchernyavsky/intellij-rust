/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.flavors

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import java.io.File

class UnixSdkFlavor private constructor() : RsSdkFlavor {

    override fun getHomePathCandidates(): Sequence<File> =
        sequenceOf(
            FileUtil.expandUserHome("~/.cargo"),
            "/usr/local",
            "/usr"
        ).map(::File)

    override fun isApplicable(): Boolean = SystemInfo.isUnix

    companion object {
        fun getInstance(): RsSdkFlavor? = RsSdkFlavor.EP_NAME.findExtension(UnixSdkFlavor::class.java)
    }
}
