/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer.outlives

import com.intellij.openapi.util.TextRange
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsLifetimeParameter
import org.rust.lang.core.types.infer.RsInferenceContext
import org.rust.lang.core.types.region.Region
import org.rust.lang.core.types.ty.Ty

data class OutlivesEnvironment(
    val parameterEnvironment: ParameterEnvironment,
    val freeRegionMap: FreeRegionMap,
    val regionBoundPairs: List<Pair<Region, RsLifetimeParameter>>
) {
    constructor(parameterEnvironment: ParameterEnvironment) : this(parameterEnvironment, FreeRegionMap(), emptyList()) {
        addOutlivesBounds(null, explicitOutlivesBounds(parameterEnvironment))
    }

//    fun pushSnapshotPreClosure(): Int { TODO() }
//    fun popSnapshotPostClosure(): Int { TODO() }

    /**
     * This method adds "implied bounds" into the outlives environment.
     * Implied bounds are outlives relationships that we can deduce on the basis that certain types must be well-formed
     * -- these are either the types that appear in the function signature or else the input types to an impl.
     * For example, if you have a function like
     * ```
     * fn foo<'a, 'b, T>(x: &'a &'b [T]) {}
     * ```
     * we can assume in the caller's body that `'b: 'a` and that `T: 'b` (and hence, transitively, that `T: 'a`).
     * This method would add those assumptions into the outlives-environment.
     */
    fun addImpliedBounds(
        inferenceContext: RsInferenceContext,
        fnSignatureTys: List<Ty>,
        body: RsBlock,
        span: TextRange
    ) {
        for (ty in fnSignatureTys) {
            val resolved = inferenceContext.resolveTypeVarsIfPossible(ty)
            val impliedBounds = inferenceContext.impliedOutlivesBounds(parameterEnvironment, body, resolved, span)
            addOutlivesBounds(inferenceContext, impliedBounds)
        }
    }

    /**
     * Processes outlives bounds that are known to hold, whether from implied or other sources.
     * The [inferenceContext] parameter is optional; if the implied bounds may contain inference variables, it must be
     * supplied, in which case we will register "givens" on the inference context. (See [RegionConstraintData].)
     */
    fun addOutlivesBounds(inferenceContext: RsInferenceContext?, outlivesBounds: List<OutlivesBound>) {
        // Record relationships such as `T:'x` that don't go into the free-region-map but which we use here.
        for (outlivesBound in outlivesBounds) {
            when (outlivesBound) {

            }
        }
    }
}
