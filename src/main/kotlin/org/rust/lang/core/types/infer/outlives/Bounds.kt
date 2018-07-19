/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer.outlives

import org.rust.lang.core.types.region.Region
import org.rust.lang.core.types.ty.TyProjection

/**
 * Outlives bounds are relationships between generic parameters, whether they both be regions (`'a: 'b`) or whether
 * types are whether they both be regions (`'a: 'b`) or whether types are involved (`T: 'a`). These relationships can
 * be extracted from the full set of predicates we understand or also from types (in which case they are called implied
 * bounds). They are fed to the [OutlivesEnvironment] which in turn is supplied to the region checker and other parts
 * of the inference system.
 */
sealed class OutlivesBound {
    data class RegionSubRegion(val region1: Region, val region2: Region) : OutlivesBound()
    data class RegionSubParameter(val region: Region) : OutlivesBound()
    data class RegionSubProjection(val region: Region, val projection: TyProjection) : OutlivesBound()
}


