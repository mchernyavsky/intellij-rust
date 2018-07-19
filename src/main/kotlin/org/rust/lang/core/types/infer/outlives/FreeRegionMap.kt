/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer.outlives

import org.rust.lang.core.types.region.Region

data class Edge<T, U>(val from: T, val to: U)

class FreeRegionMap {
    private val relation: MutableSet<Edge<Region, Region>> = mutableSetOf()

    fun isEmpty(): Boolean = relation.isEmpty()

    // Record that `'sup:'sub`. Or, put another way, `'sub <= 'sup`.
    // (with the exception that `'static: 'x` is not notable)
    fun relateRegions(sub: Region, sup: Region) {
        if (isFreeOrStatic(sub) && isFree(sup)) {
            relation.add(sub, sup)
        }
    }

    fun leastUpperBoundFreeRegions(region1: Region, region2: Region): Region {
        assert(isFree(region1))
        assert(isFree(region2))
        val result = if (region1 == region2) {
            region1
        } else {
            relation.postdomUpperBound()
        }
    }
}

private fun <T, U> MutableSet<Edge<T, U>>.add(from: T, to: U) = add(Edge(from, to))
