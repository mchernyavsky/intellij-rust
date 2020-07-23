/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.util.containers.FactoryMap
import org.rust.cargo.project.configurable.RsConfigurableToolchainList
import org.rust.cargo.project.settings.rustSdk
import org.rust.ide.sdk.add.RsAddSdkDialog
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionListener

class RsSdkDetailsDialog(
    private val project: Project?,
    private val selectedSdkCallback: (Sdk?) -> Unit,
    private val cancelCallback: (Boolean) -> Unit
) : DialogWrapper(project) {
    private val modificators: MutableMap<Sdk, SdkModificator> = FactoryMap.create { it.sdkModificator }

    private val modifiedModificators: MutableSet<SdkModificator> = hashSetOf()

    // there is an assumption that dialog started with unmodified sdks model otherwise processing `Cancel` will be more
    // complicated to correctly revert changes
    private val sdkModelListener: SdkModel.Listener = MySdkModelListener()

    private val toolchainList: RsConfigurableToolchainList = RsConfigurableToolchainList.getInstance(project)

    private val projectSdksModel: ProjectSdksModel = toolchainList.model.apply { addListener(sdkModelListener) }

    private val sdkList: JBList<Sdk> = buildSdkList(modificators, ListSelectionListener { updateOkButton() })

    private val mainPanel: JPanel = buildPanel(
        sdkList,
        addAction = AnActionButtonRunnable {
            addSdk()
            updateOkButton()
        },
        editAction = AnActionButtonRunnable {
            editSdk()
            updateOkButton()
        },
        removeAction = AnActionButtonRunnable {
            removeSdk()
            updateOkButton()
        }
    )

    private val effectiveProject: Project
        get() = project ?: ProjectManager.getInstance().defaultProject

    private val originalSelectedSdk: Sdk?
        get() {
            val editableSdk = editableSelectedSdk ?: return null
            return projectSdksModel.findSdk(editableSdk)
        }

    private val editableSelectedSdk: Sdk?
        get() = sdkList.selectedValue

    init {
        title = "Rust Toolchains"
        init()
        refreshSdkList()
        updateOkButton()
    }

    override fun dispose() {
        projectSdksModel.removeListener(sdkModelListener)
        super.dispose()
    }

    override fun createCenterPanel(): JComponent = mainPanel

    override fun getPreferredFocusedComponent(): JComponent = sdkList

    private fun updateOkButton() {
        super.setOKActionEnabled(
            projectSdksModel.isModified
                || modifiedModificators.isNotEmpty()
                || originalSelectedSdk !== effectiveProject.rustSdk
        )
    }

    override fun doOKAction() {
        apply()
        super.doOKAction()
    }

    override fun doCancelAction() {
        modificators.clear()
        modifiedModificators.clear()
        val modified = projectSdksModel.isModified
        if (modified) {
            projectSdksModel.reset(project)
        }
        cancelCallback(modified)
        super.doCancelAction()
    }

    private fun apply() {
        for (modificator in modifiedModificators) {
            if (modificator.isWritable) {
                modificator.commitChanges()
            }
        }

        modificators.clear()
        modifiedModificators.clear()

        try {
            projectSdksModel.apply()
        } catch (e: ConfigurationException) {
            LOG.error(e)
        }

        val sdk = originalSelectedSdk
        selectedSdkCallback(sdk)

        Disposer.dispose(disposable)
    }

    private fun refreshSdkList() {
        var projectSdk = effectiveProject.rustSdk
        sdkList.model = CollectionListModel(toolchainList.allRustSdks)
        if (projectSdk != null) {
            projectSdk = projectSdksModel.findSdk(projectSdk.name)
            sdkList.clearSelection()
            sdkList.setSelectedValue(projectSdk, true)
            sdkList.updateUI()
        }
    }

    private fun addSdk() {
        RsAddSdkDialog.show(project, projectSdksModel.sdks.toList()) { sdk ->
            if (sdk != null && projectSdksModel.findSdk(sdk.name) == null) {
                projectSdksModel.addSdk(sdk)
                setSelectedSdk(sdk)
            }
        }
    }

    private fun setSelectedSdk(selectedSdk: Sdk?) {
        val sdk = selectedSdk?.let { projectSdksModel.findSdk(it.name) }
        sdkList.setSelectedValue(sdk, true)
    }

    private fun editSdk() {
        val currentSdk = editableSelectedSdk ?: return
        val modificator = modificators[currentSdk] ?: return
        val dialog = RsEditSdkDialog(effectiveProject, modificator) {
            if (isDuplicateSdkName(it, currentSdk)) {
                "Please specify a unique name for the toolchain"
            } else {
                null
            }
        }

        if (dialog.showAndGet()) {
            sdkList.repaint()
            val needsReload = currentSdk.homePath != dialog.homePath || modificator.sdkAdditionalData != dialog.data
            if (needsReload || modificator.name != dialog.name) {
                modifiedModificators.add(modificator)
                modificator.name = dialog.name
                modificator.homePath = dialog.homePath
                modificator.sdkAdditionalData = dialog.data
                if (needsReload) {
                    reloadSdk(currentSdk)
                }
            }
        }
    }

    private fun isDuplicateSdkName(name: String, sdk: Sdk): Boolean {
        for (existingSdk in projectSdksModel.sdks) {
            if (existingSdk === sdk) continue
            val existingName = modificators[existingSdk]?.name ?: existingSdk.name
            if (existingName == name) return true
        }
        return false
    }

    private fun removeSdk() {
        val selectedSdk = editableSelectedSdk ?: return
        projectSdksModel.removeSdk(selectedSdk)

        val modificator = modificators[selectedSdk]
        if (modificator != null) {
            modifiedModificators.remove(modificator)
            modificators.remove(selectedSdk)
        }

        refreshSdkList()
        effectiveProject.rustSdk?.let { sdkList.setSelectedValue(it, true) }
    }

    private fun reloadSdk(currentSdk: Sdk) = RsSdkUpdater.updateLocalSdkVersion(currentSdk)

    private inner class MySdkModelListener : SdkModel.Listener {
        override fun sdkAdded(sdk: Sdk) = refreshSdkList()
        override fun sdkChanged(sdk: Sdk, previousName: String) = refreshSdkList()
    }

    companion object {
        private val LOG: Logger = Logger.getInstance(RsSdkDetailsDialog::class.java)

        private fun buildSdkList(
            modificators: Map<Sdk, SdkModificator>,
            selectionListener: ListSelectionListener
        ): JBList<Sdk> {
            val result = JBList<Sdk>()
            result.cellRenderer = RsSdkListCellRenderer(modificators)
            result.selectionMode = ListSelectionModel.SINGLE_SELECTION
            result.addListSelectionListener(selectionListener)
            ListSpeedSearch(result)
            return result
        }

        private fun buildPanel(
            sdkList: JBList<Sdk>,
            addAction: AnActionButtonRunnable,
            editAction: AnActionButtonRunnable,
            removeAction: AnActionButtonRunnable,
            vararg extraActions: AnActionButton
        ): JPanel = ToolbarDecorator
            .createDecorator(sdkList)
            .disableUpDownActions()
            .setAddAction(addAction)
            .setEditAction(editAction)
            .setRemoveAction(removeAction)
            .addExtraActions(*extraActions)
            .setPreferredSize(Dimension(600, 500))
            .createPanel()
    }
}
