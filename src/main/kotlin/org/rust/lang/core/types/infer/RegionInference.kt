/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.infer.outlives.OutlivesEnvironment
import org.rust.lang.core.types.infer.outlives.RegionRelations
import org.rust.lang.core.types.region.Scope
import org.rust.lang.core.types.region.ScopeTree

data class RegionContext(
    val fnContext: RsFnInferenceContext,

    val regionScopeTree: ScopeTree,

    val outlivesEnvironment: OutlivesEnvironment,

    // innermost fn body
    val body: RsBlock,

    // callSite scope of innermost fn
    val callSiteScope: Scope?,

    // innermost fn or loop
    val repeatingScope: RsElement,

    // element being analyzed (the subject of the analysis)
    val subject: RsElement
) {
    // A set of constraints that regionck must validate. Each
    // constraint has the form `T:'a`, meaning "some type `T` must
    // outlive the lifetime 'a". These constraints derive from
    // instantiated type parameters. So if you had a struct defined
    // like
    //
    //     struct Foo<T:'static> { ... }
    //
    // then in some expression `let x = Foo { ... }` it will
    // instantiate the type parameter `T` with a fresh type `$0`. At
    // the same time, it will record a region obligation of
    // `$0:'static`. This will get checked later by regionck. (We
    // can't generally check these things right away because we have
    // to wait until types are resolved.)
    //
    // These are stored in a map keyed to the id of the innermost
    // enclosing fn body / static initializer expression. This is
    // because the location where the obligation was incurred can be
    // relevant with respect to which sublifetime assumptions are in
    // place. The reason that we store under the fn-id, and not
    // something more fine-grained, is so that it is easier for
    // regionck to be sure that it has found *all* the region
    // obligations (otherwise, it's easy to fail to walk to a
    // particular node-id).
    //
    // Before running `resolve_regions_and_report_errors`, the creator
    // of the inference context is expected to invoke
    // `process_region_obligations` (defined in `self::region_obligations`)
    // for each body-id in this map, which will process the
    // obligations within. This is expected to be done 'late enough'
    // that all type inference variables have been bound and so forth.
    private val obligations: List<Pair<RsElement, RegionObligation>> = mutableListOf()

    constructor(
        fnContext: RsFnInferenceContext,
        initialRepeatingScope: RsElement,
        initialBody: RsBlock,
        subject: RsElement,
        parametersEnvironment: ParametersEnvironment
    ) : this(
        fnContext,
        fnContext.TODO().regionScopeTree(subject),
        initialRepeatingScope,
        initialBody,
        null,
        subject,
        OutlivesEnvironment(parametersEnvironment)
    )

    fun resolveRegions(
        regionContext: RegionContext,
        regionMap: ScopeTree,
        outlivesEnvironment: OutlivesEnvironment
    ) {
        val regionRelations = RegionRelations()

    }
}
