/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.implementMembers

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.ide.inspections.import.ImportCandidate
import org.rust.ide.inspections.import.canBeImported
import org.rust.ide.inspections.import.import
import org.rust.lang.core.macros.expandedFromRecursively
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyTraitObject
import org.rust.lang.core.types.type
import org.rust.openapiext.checkReadAccessAllowed
import org.rust.openapiext.checkWriteAccessAllowed
import org.rust.openapiext.checkWriteAccessNotAllowed

fun generateTraitMembers(impl: RsImplItem, editor: Editor?) {
    checkWriteAccessNotAllowed()
    val (implInfo, trait) = findMembersToImplement(impl) ?: run {
        if (editor != null) {
            HintManager.getInstance().showErrorHint(editor, "No members to implement have been found")
        }
        return
    }

    val chosen = showTraitMemberChooser(implInfo, impl.project)
    if (chosen.isEmpty()) return
    runWriteAction {
        // Non-null was checked by `findMembersToImplement`.
        insertNewTraitMembers(chosen, impl.members!!, trait)
    }
}

private fun findMembersToImplement(impl: RsImplItem): Pair<TraitImplementationInfo, BoundElement<RsTraitItem>>? {
    checkReadAccessAllowed()

    val trait = impl.traitRef?.resolveToBoundTrait ?: return null
    val implInfo = TraitImplementationInfo.create(trait.element, impl) ?: return null
    if (implInfo.declared.isEmpty()) return null
    return implInfo to trait
}

private fun insertNewTraitMembers(
    selected: Collection<RsAbstractable>,
    existingMembers: RsMembers,
    trait: BoundElement<RsTraitItem>
) {
    checkWriteAccessAllowed()
    if (selected.isEmpty()) return

    val templateImpl = RsPsiFactory(existingMembers.project).createMembers(selected, trait.subst)
    val mod = existingMembers.containingMod
    val superMods = LinkedHashSet(mod.superMods)
    val importCandidates = mutableListOf<ImportCandidate>()
    val importCandidateVisitor = object : RsVisitor() {
        override fun visitElement(element: RsElement) = element.acceptChildren(this)

        override fun visitTypeReference(o: RsTypeReference) {
            val ty = o.type
            val item: RsQualifiedNamedElement = when (ty) {
                is TyAdt -> ty.item
                is TyTraitObject -> ty.trait.element
                else -> return
            }

            val candidate = QualifiedNamedItem.ExplicitItem(item)
                .withModuleReexports(mod.project)
                .mapNotNull { ImportCandidate(it, it.canBeImported(superMods) ?: return@mapNotNull null) }
                .firstOrNull() ?: return

            importCandidates.add(candidate)

            visitElement(o) // type reference also can contains sub type references
        }
    }

    // Visit the existing trait functions to determine the types that need to be imported.
    selected.forEach { it.accept(importCandidateVisitor) }

    // Determine which of the type references we found are already in scope.
    val inScopeItems: Set<RsElement> = importCandidates
        .mapNotNull { it.qualifiedNamedItem.item as? RsItemElement }
        .filterInScope(existingMembers)
        .toSet()

    importCandidates
        .filter { !inScopeItems.contains(it.qualifiedNamedItem.item) }
        .forEach { it.import(mod) }

    val traitMembers = trait.element.expandedMembers
    val newMembers = templateImpl.childrenOfType<RsAbstractable>()

    // [1] First, check if the order of the existingMembers already implemented
    // matches the order of existingMembers in the trait declaration.
    val existingMembersWithPosInTrait = existingMembers.expandedMembers.map { existingMember ->
        Pair(existingMember, traitMembers.indexOfFirst {
            it.elementType == existingMember.elementType && it.name == existingMember.name
        })
    }.toMutableList()
    val existingMembersOrder = existingMembersWithPosInTrait.map { it.second }
    val areExistingMembersInTheRightOrder = existingMembersOrder == existingMembersOrder.sorted()

    for ((index, newMember) in newMembers.withIndex()) {
        val posInTrait = traitMembers.indexOfFirst {
            it.elementType == newMember.elementType && it.name == newMember.name
        }

        var indexedExistingMembers = existingMembersWithPosInTrait.withIndex()

        // If [1] does not hold, the first new member we will append at the end of the implementation.
        // All the other ones will consequently be inserted at the right position in relation to that very first one.
        if (areExistingMembersInTheRightOrder || index > 0) {
            indexedExistingMembers = indexedExistingMembers.filter { it.value.second < posInTrait }
        }

        val anchor = indexedExistingMembers
            .lastOrNull()
            ?.let {
                val member = it.value.first
                IndexedValue(it.index, member.expandedFromRecursively ?: member)
            }
            ?: IndexedValue(-1, existingMembers.lbrace)

        val addedMember = existingMembers.addAfter(newMember, anchor.value) as RsAbstractable
        existingMembersWithPosInTrait.add(anchor.index + 1, Pair(addedMember, posInTrait))

        // If the newly added item is a function, we add an extra line between it and each of its siblings.
        val prev = addedMember.leftSiblings.find { it is RsAbstractable || it is RsMacroCall }
        if (prev != null && (prev is RsFunction || addedMember is RsFunction)) {
            val whitespaces = createExtraWhitespacesAroundFunction(prev, addedMember)
            existingMembers.addBefore(whitespaces, addedMember)
        }

        val next = addedMember.rightSiblings.find { it is RsAbstractable || it is RsMacroCall }
        if (next != null && (next is RsFunction || addedMember is RsFunction)) {
            val whitespaces = createExtraWhitespacesAroundFunction(addedMember, next)
            existingMembers.addAfter(whitespaces, addedMember)
        }
    }
}

private fun createExtraWhitespacesAroundFunction(left: PsiElement, right: PsiElement): PsiElement {
    val lineCount = left
        .rightSiblings
        .takeWhile { it != right }
        .filterIsInstance<PsiWhiteSpace>()
        .map { it.text.count { c -> c == '\n' } }
        .sum()
    val extraLineCount = Math.max(0, 2 - lineCount)
    return RsPsiFactory(left.project).createWhitespace("\n".repeat(extraLineCount))
}
