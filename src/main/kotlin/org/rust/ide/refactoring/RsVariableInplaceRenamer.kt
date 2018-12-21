/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import org.rust.lang.core.psi.CAN_NOT_BE_ESCAPED
import org.rust.lang.core.psi.RS_RAW_PREFIX
import org.rust.lang.core.psi.escapeIdentifierIfNeeded

class RsVariableInplaceRenamer(
    elementToRename: PsiNamedElement,
    editor: Editor
) : VariableInplaceRenamer(elementToRename, editor) {
    override fun performRefactoring(): Boolean {
        if (!isValidRustVariableIdentifier(myInsertedName) && myInsertedName !in CAN_NOT_BE_ESCAPED) {
            runWriteAction { myEditor.document.insertString(myRenameOffset.startOffset, RS_RAW_PREFIX) }
            myInsertedName = myInsertedName?.escapeIdentifierIfNeeded()
        }
        return super.performRefactoring()
    }
}
