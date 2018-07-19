/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer.outlives

import org.rust.lang.core.psi.RsLifetimeParameter
import org.rust.lang.core.types.infer.RsInferenceContext
import org.rust.lang.core.types.region.Region

data class OutlivesEnvironment(
    val parameterEnvironment: ParameterEnvironment,
    val freeRegionMap: FreeRegionMap,
    val regionBoundPairs: List<Pair<Region, RsLifetimeParameter>>
) {
    constructor(parameterEnvironment: ParameterEnvironment): this(parameterEnvironment, FreeRegionMap(), emptyList()) {
        addOutlivesBounds(null, explicitOutlivesBounds(parameterEnvironment))
    }

//    fun pushSnapshotPreClosure(): Int { TODO() }
//    fun popSnapshotPostClosure(): Int { TODO() }

    fun addImpliedBounds() {

    }

    fun addOutlivesBounds(inferenceContext: RsInferenceContext?, outlivesBounds: List<OutlivesBound>) {

    }
}
