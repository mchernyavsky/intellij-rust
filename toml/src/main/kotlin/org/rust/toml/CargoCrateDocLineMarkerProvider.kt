/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.rust.cargo.CargoConstants.MANIFEST_FILE
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.ext.elementType
import org.toml.lang.psi.*
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

class CargoCrateDocLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: MutableList<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
        if (!tomlPluginIsAbiCompatible()) return
        val firstElement = elements.firstOrNull() ?: return
        val file = firstElement.containingFile
        if (!file.name.equals(MANIFEST_FILE, ignoreCase = true)) return

        loop@ for (element in elements) {
            val parent = element.parent
            if (parent is TomlKey) {
                val key = parent
                val keyValue = key.parent as? TomlKeyValue ?: continue@loop
                val table = keyValue.parent as? TomlTable ?: continue@loop
                if (!table.header.isDependencyListHeader) continue@loop
                if (key.firstChild?.nextSibling != null) continue@loop
                val pkgName = keyValue.crateName
                val pkgVersion = keyValue.version ?: continue@loop
                result += genLineMarkerInfo(element, pkgName, pkgVersion)
            } else if (element.elementType == TomlElementTypes.L_BRACKET) {
                val header = parent as? TomlTableHeader ?: continue@loop
                val names = header.names
                if (names.getOrNull(names.size - 2)?.isDependencyKey != true) continue@loop
                val table = parent.parent as? TomlTable ?: continue@loop
                val version = table.entries.find { it.name == "version" }?.value?.stringValue ?: continue@loop
                result += genLineMarkerInfo(element, names.last().text, version)
            }
        }
    }

    private fun genLineMarkerInfo(anchor: PsiElement, name: String, version: String): LineMarkerInfo<PsiElement> {
        return LineMarkerInfo(
            anchor,
            anchor.textRange,
            RsIcons.DOCS_MARK,
            { "Open documentation for `$name@$version`" },
            { _, _ -> BrowserUtil.browse("https://docs.rs/$name/$version/$name") },
            GutterIconRenderer.Alignment.LEFT)

    }
}

private val TomlKeyValue.name get() = key.text
private val TomlKeyValue.crateName: String
    get() {
        return when (val rootValue = value) {
            is TomlInlineTable -> (rootValue.entries.find { it.name == "package" }?.value?.stringValue)
                ?: key.text
            else -> key.text
        }
    }
private val TomlKeyValue.version: String?
    get() {
        return when (val value = value) {
            is TomlLiteral -> value.stringValue
            is TomlInlineTable -> value.entries.find { it.name == "version" }?.value?.stringValue
            else -> null
        }
    }

private val TomlValue.stringValue: String? get() {
    val kind = (this as? TomlLiteral)?.kind
    return (kind as? TomlLiteralKind.String)?.value
}
