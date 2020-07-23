/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.AdditionalDataConfigurable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.projectRoots.SdkModificator
import org.rust.ide.ui.layout
import javax.swing.JComponent

class RsAdditionalDataConfigurable(
    private val sdkModel: SdkModel,
    private var modificator: SdkModificator
) : AdditionalDataConfigurable {
    private val sdkAdditionalDataPanel: RsSdkAdditionalDataPanel = RsSdkAdditionalDataPanel()
    private val listener: SdkModel.Listener = object : SdkModel.Listener {
        override fun sdkHomeSelected(sdk: Sdk, newSdkHome: String) {
            // TODO
        }
    }

    init {
        sdkModel.addListener(listener)
    }

    override fun setSdk(sdk: Sdk) {
        modificator = sdk.sdkModificator
        sdkAdditionalDataPanel.update(sdk.homePath)
        reset()
    }

    override fun createComponent(): JComponent = layout {
        sdkAdditionalDataPanel.attachTo(this)
    }

    override fun isModified(): Boolean = modificator.sdkAdditionalData != sdkAdditionalDataPanel.data

    override fun apply() {
        modificator.sdkAdditionalData = sdkAdditionalDataPanel.data
        runWriteAction(modificator::commitChanges)
    }

    override fun reset() {
        sdkAdditionalDataPanel.data = modificator.sdkAdditionalData as? RsSdkAdditionalData ?: return
    }

    override fun disposeUIResources() = sdkModel.removeListener(listener)
}
