/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import org.rust.lang.core.psi.RsPatBinding

class RsVariableInplaceRenameHandler : VariableInplaceRenameHandler() {
    override fun isAvailable(element: PsiElement?, editor: Editor, file: PsiFile): Boolean =
        element is RsPatBinding && editor.settings.isVariableInplaceRenameEnabled

    override fun createRenamer(elementToRename: PsiElement, editor: Editor): VariableInplaceRenamer =
        RsVariableInplaceRenamer(elementToRename as PsiNamedElement, editor)
}
