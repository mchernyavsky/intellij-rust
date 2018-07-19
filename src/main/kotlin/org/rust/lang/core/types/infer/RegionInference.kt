/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.infer.outlives.OutlivesEnvironment
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
}
