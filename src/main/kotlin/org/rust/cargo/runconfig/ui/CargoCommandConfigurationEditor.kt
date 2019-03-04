/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.ui

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ListCellRendererWrapper
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.Label
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.LayoutBuilder
import com.intellij.ui.layout.Row
import com.intellij.ui.layout.panel
import com.intellij.util.text.nullize
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.CargoCommandLineEditor
import java.awt.Dimension
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel


class CargoCommandConfigurationEditor(private val project: Project) : SettingsEditor<CargoCommandConfiguration>() {
    private fun currentWorkspace(): CargoWorkspace? =
        CargoCommandConfiguration.findCargoProject(project, command.text, currentWorkingDirectory)?.workspace

    private val allCargoProjects: List<CargoProject> =
        project.cargoProjects.allProjects.sortedBy { it.presentableName }

    private val command = CargoCommandLineEditor(project) { this.currentWorkspace() }

    private val backtraceMode = ComboBox<BacktraceMode>().apply {
        BacktraceMode.values()
            .sortedBy { it.index }
            .forEach { addItem(it) }
    }

    private val currentWorkingDirectory: Path? get() = workingDirectory.component.text.nullize()?.let { Paths.get(it) }
    private val workingDirectory = run {
        val textField = TextFieldWithBrowseButton().apply {
            val fileChooser = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
                title = ExecutionBundle.message("select.working.directory.message")
            }
            addBrowseFolderListener(null, null, null, fileChooser)
        }
        LabeledComponent.create(textField, ExecutionBundle.message("run.configuration.working.directory.label"))
    }
    private val cargoProject = ComboBox<CargoProject>().apply {
        renderer = object : ListCellRendererWrapper<CargoProject>() {
            override fun customize(list: JList<*>?, value: CargoProject?, index: Int, selected: Boolean, hasFocus: Boolean) {
                setText(value?.presentableName)
            }
        }
        allCargoProjects.forEach { addItem(it) }

        addItemListener {
            setWorkingDirectoryFromSelectedProject()
            setToolchainListFromSelectedProject()
        }
    }

    private fun setWorkingDirectoryFromSelectedProject() {
        val selectedProject = selectedProject ?: return
        workingDirectory.component.text = selectedProject.workingDirectory.toString()
    }

    private val selectedProject: CargoProject?
        get() {
            val idx = cargoProject.selectedIndex
            return if (idx != -1) cargoProject.getItemAt(idx) else null
        }

    private val environmentVariables = EnvironmentVariablesComponent()
    private val allFeatures = CheckBox("Use all features in tests", false)
    private val nocapture = CheckBox("Show stdout/stderr in tests (and disable test tool window)", false)

    override fun resetEditorFrom(configuration: CargoCommandConfiguration) {
        toolchainName.selectedItem = configuration.toolchainName
        command.text = configuration.command
        allFeatures.isSelected = configuration.allFeatures
        nocapture.isSelected = configuration.nocapture
        backtraceMode.selectedIndex = configuration.backtrace.index
        workingDirectory.component.text = configuration.workingDirectory?.toString() ?: ""
        environmentVariables.envData = configuration.env
        val vFile = currentWorkingDirectory?.let { LocalFileSystem.getInstance().findFileByIoFile(it.toFile()) }
        if (vFile == null) {
            cargoProject.selectedIndex = -1
        } else {
            val projectForWd = project.cargoProjects.findProjectForFile(vFile)
            cargoProject.selectedIndex = allCargoProjects.indexOf(projectForWd)
        }
    }

    private val toolchainName = ComboBox<String>().apply {
        addItem(RustToolchain.DEFAULT_NAME)
        selectedProject?.toolchainsList?.forEach { addItem(it) }
    }

    private fun setToolchainListFromSelectedProject() {
        toolchainName.removeAllItems()
        toolchainName.addItem(RustToolchain.DEFAULT_NAME)
        val selectedProject = selectedProject ?: return
        selectedProject.toolchainsList?.forEach { toolchainName.addItem(it) }
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(configuration: CargoCommandConfiguration) {
        val configToolchainName = toolchainName.selectedItem as? String ?: RustToolchain.DEFAULT_NAME

        configuration.toolchainName = configToolchainName
        configuration.command = command.text
        configuration.allFeatures = allFeatures.isSelected
        configuration.nocapture = nocapture.isSelected
        configuration.backtrace = BacktraceMode.fromIndex(backtraceMode.selectedIndex)
        configuration.workingDirectory = currentWorkingDirectory
        configuration.env = environmentVariables.envData

        val rustupAvailable = project.toolchain?.isRustupAvailable ?: false
        toolchainName.isEnabled = rustupAvailable || configToolchainName != RustToolchain.DEFAULT_NAME
        if (!rustupAvailable && configToolchainName != RustToolchain.DEFAULT_NAME) {
            throw ConfigurationException("Channel cannot be set explicitly because rustup is not available")
        }
    }

    override fun createEditor(): JComponent = panel {
        labeledRow("&Command:", command) { command(CCFlags.pushX, CCFlags.growX) }
        labeledRow("&Toolchain:", toolchainName) { toolchainName() }

        row { allFeatures() }
        row { nocapture() }

        row(environmentVariables.label) { environmentVariables.apply { makeWide() }() }
        row(workingDirectory.label) {
            workingDirectory.apply { makeWide() }()
            if (project.cargoProjects.allProjects.size > 1) {
                cargoProject()
            }
        }
        labeledRow("Back&trace:", backtraceMode) { backtraceMode() }
    }

    private fun LayoutBuilder.labeledRow(labelText: String, component: JComponent, init: Row.() -> Unit) {
        val label = Label(labelText)
        label.labelFor = component
        row(label) { init() }
    }

    private fun JPanel.makeWide() {
        preferredSize = Dimension(1000, height)
    }
}
