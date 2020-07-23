/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import org.rust.ide.ui.layout
import org.rust.openapiext.pathTextField
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

class RsEditSdkDialog(
    project: Project,
    sdk: SdkModificator,
    nameValidator: (String) -> String?
) : DialogWrapper(project, true) {
    val name: String get() = nameTextField.text
    private val nameTextField: JTextField = JTextField()

    val homePath: String get() = homePathTextField.text
    private val homePathTextField: TextFieldWithBrowseButton = pathTextField(
        RsSdkType.getInstance().homeChooserDescriptor,
        disposable,
        "Select toolchain or rustup path"
    ) { sdkAdditionalDataPanel.update(homePath) }

    val data: RsSdkAdditionalData get() = sdkAdditionalDataPanel.data
    private val sdkAdditionalDataPanel: RsSdkAdditionalDataPanel = RsSdkAdditionalDataPanel()

    init {
        title = "Edit Rust Toolchain"
        nameTextField.text = sdk.name
        nameTextField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val nameError = nameValidator(name)
                setErrorText(nameError, nameTextField)
                isOKActionEnabled = nameError == null
            }
        })
        homePathTextField.text = sdk.homePath
        init()
    }

    override fun createCenterPanel(): JComponent = layout {
        row("Name:", nameTextField)
        row("Path:", homePathTextField)
        sdkAdditionalDataPanel.attachTo(this)
    }.apply { preferredSize = Dimension(700, height) }

    override fun getPreferredFocusedComponent(): JComponent = nameTextField

    override fun doValidateAll(): List<ValidationInfo> {
        return super.doValidateAll()
    }
}
