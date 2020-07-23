/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.add

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import org.rust.ide.sdk.RsDetectedSdk
import org.rust.ide.sdk.RsSdkAdditionalDataPanel
import org.rust.ide.sdk.RsSdkUtils.detectRustupSdks
import org.rust.ide.ui.layout
import java.awt.BorderLayout
import java.awt.Component

abstract class RsAddToolchainPanelBase(private val existingSdks: List<Sdk>) : RsAddSdkPanel() {
    protected abstract val sdkComboBoxLabel: String
    private val sdkComboBox: RsSdkPathChoosingComboBox = RsSdkPathChoosingComboBox()
    private val sdkAdditionalDataPanel: RsSdkAdditionalDataPanel = RsSdkAdditionalDataPanel()

    init {
        layout = BorderLayout()
        val formPanel = layout {
            row(sdkComboBoxLabel, sdkComboBox)
            sdkAdditionalDataPanel.attachTo(this)
        }
        add(formPanel, BorderLayout.NORTH)
        addToolchainsAsync(sdkComboBox) { detectRustupSdks(existingSdks) }
        addChangeListener(Runnable {
            sdkAdditionalDataPanel.update()
        })
    }

    override fun validateAll(): List<ValidationInfo> =
        listOfNotNull(validateSdkComboBox(sdkComboBox))

    override fun getOrCreateSdk(): Sdk? {
        val sdk = when (val sdk = sdkComboBox.selectedSdk) {
            is RsDetectedSdk -> sdk.setup(existingSdks)
            else -> sdk
        }

        val modificator = sdk?.sdkModificator ?: return null
        modificator.sdkAdditionalData = sdkAdditionalDataPanel.data
        runWriteAction(modificator::commitChanges)

        return sdk
    }


    final override fun add(comp: Component, constraints: Any?) {
        super.add(comp, constraints)
    }

    final override fun addChangeListener(listener: Runnable) {
        sdkComboBox.childComponent.addItemListener { listener.run() }
    }
}
