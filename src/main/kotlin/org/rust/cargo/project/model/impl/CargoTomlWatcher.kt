/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.PathUtil
import org.rust.cargo.CargoConstants.LOCK_FILE
import org.rust.cargo.CargoConstants.MANIFEST_FILE
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.workspace.PackageOrigin

/**
 * File changes listener, detecting changes inside the `Cargo.toml` files
 * and creation of `*.rs` files acting as automatic crate root.
 */
class CargoTomlWatcher(
    private val cargoProjects: CargoProjectsService,
    private val onCargoTomlChange: () -> Unit
) : BulkFileListener {

    override fun before(events: List<VFileEvent>) = Unit

    override fun after(events: List<VFileEvent>) {
        if (events.any { isInterestingEvent(it) }) onCargoTomlChange()
    }

    private fun isInterestingEvent(event: VFileEvent): Boolean {
        if (!Companion.isInterestingEvent(event)) return false

        // Fixes https://github.com/intellij-rust/intellij-rust/issues/5621
        // For some reason, Cargo bumps modification time of `Cargo.toml` of `openid 0.4.0`
        // dependency on each `cargo metadata` invocation. Let's ignore changes in
        // `Cargo.toml`/`Cargo.lock` outside of a workspace
        val file = when (event) {
            is VFileContentChangeEvent -> event.file
            else -> return true
        }
        return cargoProjects.findPackageForFile(file)?.origin == PackageOrigin.WORKSPACE
    }

    companion object {
        // These are paths and files names used by Cargo to infer targets without Cargo.toml
        // https://github.com/rust-lang/cargo/blob/2c2e07f5cfc9a5de10854654bc1e8abd02ae7b4f/src/cargo/util/toml.rs#L50-L56
        private val IMPLICIT_TARGET_FILES = listOf(
            "/build.rs", "/src/main.rs", "/src/lib.rs"
        )

        private val IMPLICIT_TARGET_DIRS = listOf(
            "/src/bin", "/examples", "/tests", "/benches"
        )

        @VisibleForTesting
        fun isInterestingEvent(event: VFileEvent): Boolean = when {
            event.pathEndsWith(MANIFEST_FILE) || event.pathEndsWith(LOCK_FILE) -> true
            event is VFileContentChangeEvent -> false
            !event.pathEndsWith(".rs") -> false
            event is VFilePropertyChangeEvent && event.propertyName != VirtualFile.PROP_NAME -> false
            IMPLICIT_TARGET_FILES.any { event.pathEndsWith(it) } -> true
            else -> {
                val parent = PathUtil.getParentPath(event.path)
                val grandParent = PathUtil.getParentPath(parent)
                IMPLICIT_TARGET_DIRS.any { parent.endsWith(it) || (event.pathEndsWith("main.rs") && grandParent.endsWith(it)) }
            }
        }

        private fun VFileEvent.pathEndsWith(suffix: String): Boolean = path.endsWith(suffix) ||
            this is VFilePropertyChangeEvent && oldPath.endsWith(suffix)
    }
}
