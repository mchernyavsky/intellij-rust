/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.Link
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.Rustup
import org.rust.ide.sdk.flavors.RustupSdkFlavor
import org.rust.ide.ui.RsLayoutBuilder
import org.rust.openapiext.ComboBoxDelegate
import org.rust.openapiext.UiDebouncer
import org.rust.openapiext.pathToDirectoryTextField
import org.rust.stdext.toPath
import java.io.File
import java.nio.file.Paths
import javax.swing.JComponent

class RsSdkAdditionalDataPanel(
    private var homePath: String,
    private val updateListener: (() -> Unit)? = null
) : Disposable {
    private val updateDebouncer: UiDebouncer = UiDebouncer(this)

    private val rustup: Rustup?
        get() = if (RustupSdkFlavor.isValidSdkHome(File(homePath))) Rustup(homePath.toPath()) else null

    private val toolchainComboBox: ComboBox<Rustup.Toolchain> = ComboBox()
    private val toolchain: Rustup.Toolchain by ComboBoxDelegate(toolchainComboBox)

    private val stdlibPathField: TextFieldWithBrowseButton =
        pathToDirectoryTextField(this, "Select directory with standard library source code")

    private val downloadStdlibLink: JComponent = Link("Download via rustup", action = {
        val rustup = rustup ?: return@Link
        object : Task.Backgroundable(null, "Downloading Rust standard library") {
            override fun shouldStartInBackground(): Boolean = false
            override fun onSuccess() = update()
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                rustup.downloadStdlib(myProject, toolchain.name)
            }
        }.queue()
    }).apply { isVisible = false }

    var data: RsSdkAdditionalData
        get() {
            return RsSdkAdditionalData(
                toolchainName = toolchain.name,
                toolchainPath = toolchain.path,
                rustupPath = homePath,
                stdlibPath = stdlibPathField.text.blankToNull()?.takeIf { rustup == null }
            )
        }
        set(value) {
            stdlibPathField.text = value.stdlibPath ?: ""
            update()
        }

    fun attachTo(layout: RsLayoutBuilder) = with(layout) {
        row("Toolchain:", toolchainComboBox)
        row("Standard library:", stdlibPathField)
        row(component = downloadStdlibLink)
    }

    @Throws(ConfigurationException::class)
    fun validateSettings() {
        val toolchain = data.toolchain ?: return
        if (!toolchain.looksLikeValidToolchain()) {
            throw ConfigurationException("Invalid toolchain location: can't find Cargo in ${toolchain.location}")
        }
    }

    fun update(newHomePath: String? = null) {
        if (newHomePath == homePath) return
        updateDebouncer.run(
            onPooledThread = {
                val rustup = Rustup(homePath.toPath())
                if (rustup != null) {
                    rustup.listToolchains()
                } else {
                    emptyList()
                }


                val toolchain = RustToolchain(Paths.get(pathToToolchain))
                val rustcVersion = toolchain.queryVersions().rustc?.semver
                val rustup = toolchain.rustup
                val stdlibLocation = toolchain.getStdlibFromSysroot(cargoProjectDir)?.presentableUrl
                Triple(rustcVersion, stdlibLocation, rustup != null)
            },
            onUiThread = { (toolchains, stdlibLocation, hasRustup) ->
                toolchainComboBox.removeAllItems()
                toolchains.forEach { toolchainComboBox.addItem(it) }
                toolchainComboBox.isEnabled = toolchains.isNotEmpty()
                stdlibPathField.isEditable = !hasRustup
                stdlibPathField.button.isEnabled = !hasRustup
                if (stdlibLocation != null) {
                    stdlibPathField.text = stdlibLocation
                }
                downloadStdlibLink.isVisible = hasRustup && stdlibLocation == null
                updateListener?.invoke()
            }
        )
    }

    override fun dispose() {}
}

private fun String.blankToNull(): String? = if (isBlank()) null else this
