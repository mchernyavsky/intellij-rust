/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.lifetimeParameters
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.mergeFlags
import org.rust.lang.core.types.region.ReEarlyBound
import org.rust.lang.core.types.region.ReUnknown
import org.rust.lang.core.types.region.Region
import org.rust.lang.core.types.type

/**
 * Represents struct/enum/union.
 * "ADT" may be read as "Algebraic Data Type".
 * The name is inspired by rustc
 */
@Suppress("DataClassPrivateConstructor")
data class TyAdt private constructor(
    val item: RsStructOrEnumItemElement,
    val typeArguments: List<Ty>,
    val lifetimeArguments: List<Region>
) : Ty(mergeFlags(typeArguments) or mergeFlags(lifetimeArguments)) {

    // This method is rarely called (in comparison with folding),
    // so we can implement it in a such inefficient way
    override val typeParameterValues: Substitution
        get() {
            val typeSubst = item.typeParameters.withIndex().associate { (i, param) ->
                TyTypeParameter.named(param) to typeArguments.getOrElse(i) { TyUnknown }
            }
            val lifetimeSubst = item.lifetimeParameters.withIndex().associate { (i, param) ->
                ReEarlyBound(param) to lifetimeArguments.getOrElse(i) { ReUnknown }
            }
            return Substitution(typeSubst, lifetimeSubst)
        }

    override fun superFoldWith(folder: TypeFolder): TyAdt =
        TyAdt(item, typeArguments.map { it.foldWith(folder) }, lifetimeArguments.map { it.foldWith(folder) })

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        typeArguments.any { visitor.visitTy(it) }

    companion object {
        fun valueOf(struct: RsStructOrEnumItemElement): TyAdt {
            val item = CompletionUtil.getOriginalOrSelf(struct)
            return TyAdt(item, defaultTypeArguments(struct), defaultLifetimeArguments(struct))
        }
    }
}

private fun defaultTypeArguments(item: RsStructOrEnumItemElement): List<Ty> =
    item.typeParameters.map { param ->
        param.typeReference?.type ?: TyTypeParameter.named(param)
    }

private fun defaultLifetimeArguments(item: RsStructOrEnumItemElement): List<Region> =
    item.lifetimeParameters.map { param -> ReEarlyBound(param) }
