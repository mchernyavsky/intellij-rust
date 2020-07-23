/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.add

import com.intellij.openapi.projectRoots.Sdk

class RsAddSystemWideToolchainPanel(existingSdks: List<Sdk>) : RsAddToolchainPanelBase(existingSdks) {
    override val panelName: String = "System toolchain"
    override val sdkComboBoxLabel: String = "Toolchain path:"
}
