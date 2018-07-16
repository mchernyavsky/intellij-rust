/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.region
//
//import com.intellij.openapi.util.TextRange
//import org.rust.lang.core.psi.ext.RsElement
//
//data class Scope(val element: RsElement, val code: Int) {
//
//    val data: ScopeData
//        get() = when (code) {
//            SCOPE_DATA_NODE -> ScopeData.Node(element)
//            SCOPE_DATA_CALLSITE -> ScopeData.CallSite(element)
//            SCOPE_DATA_ARGUMENTS -> ScopeData.Arguments(element)
//            SCOPE_DATA_DESTRUCTION -> ScopeData.Destruction(element)
//            else -> ScopeData.Remainder(BlockRemainder(element, code))
//        }
//
//    fun span(scopeTree: ScopeTree): TextRange {
//        // TODO
//    }
//
//    companion object {
//
//        fun from(scopeData: ScopeData): Scope {
//            val (element, code) = when (scopeData) {
//                is ScopeData.Node -> Pair(scopeData.element, SCOPE_DATA_NODE)
//                is ScopeData.CallSite -> Pair(scopeData.element, SCOPE_DATA_CALLSITE)
//                is ScopeData.Arguments -> Pair(scopeData.element, SCOPE_DATA_ARGUMENTS)
//                is ScopeData.Destruction -> Pair(scopeData.element, SCOPE_DATA_DESTRUCTION)
//                is ScopeData.Remainder -> Pair(scopeData.remainder.block, scopeData.remainder.firstStatementIndex)
//            }
//            return Scope(element, code)
//        }
//
//        fun node(element: RsElement): Scope = from(ScopeData.Node(element))
//        fun callSite(element: RsElement): Scope = from(ScopeData.CallSite(element))
//        fun arguments(element: RsElement): Scope = from(ScopeData.Arguments(element))
//        fun destruction(element: RsElement): Scope = from(ScopeData.Destruction(element))
//        fun remainder(remainder: BlockRemainder): Scope = from(ScopeData.Remainder(remainder))
//    }
//}
//
//const val SCOPE_DATA_NODE: Int = 0.inv()
//const val SCOPE_DATA_CALLSITE: Int = 1.inv()
//const val SCOPE_DATA_ARGUMENTS: Int = 2.inv()
//const val SCOPE_DATA_DESTRUCTION: Int = 3.inv()
//const val SCOPE_DATA_REMAINDER_MAX: Int = 4.inv()
//
//sealed class ScopeData {
//    data class Node(val element: RsElement) : ScopeData()
//
//    // Scope of the call-site for a function or closure (outlives the arguments as well as the body).
//    data class CallSite(val element: RsElement) : ScopeData()
//
//    // Scope of arguments passed to a function or closure (they outlive its body).
//    data class Arguments(val element: RsElement) : ScopeData()
//
//    // Scope of destructors for temporaries of node-id.
//    data class Destruction(val element: RsElement) : ScopeData()
//
//    // Scope following a `let id = expr;` binding in a block.
//    data class Remainder(val remainder: BlockRemainder) : ScopeData()
//}
//
///**
// * Represents a subscope of `block` for a binding that is introduced by `block.stmts[first_statement_index]`.
// * Such subscopes represent  a suffix of the block. Note that each subscope does not include the initializer expression,
// * if any, for the statement indexed by  `first_statement_index`.
// */
//data class BlockRemainder(val block: RsElement, val firstStatementIndex: Int) {
//}
//
//typealias ScopeDepth = Int
//typealias ScopeInfo = Pair<Scope, ScopeDepth>
//
//class ScopeTree {
//    private val rootBody: RsElement? = null
//    private val rootParent: RsElement? = null
//    private val parentMap: MutableMap<Scope, ScopeInfo> = mutableMapOf()
//    private val variableMap: MutableMap<RsElement, Scope> = mutableMapOf()
//    private val destructionScopes: MutableMap<RsElement, Scope> = mutableMapOf()
//    private val rvalueScopes: MutableMap<RsElement, Scope?> = mutableMapOf()
//    private val closureTree: MutableMap<RsElement, RsElement> = mutableMapOf()
//
//    fun recordScopeParent(child: Scope, parent: ScopeInfo?) {
//        if (parent != null) {
//            val previous = parentMap.put(child, parent)
//            assert(previous == null)
//        }
//
//        // record the destruction scopes for later so we can query them
//        val data = child.data
//        if (data is ScopeData.Destruction) {
//            destructionScopes[data.element] = child
//        }
//    }
//
//    fun destructionScope(element: RsElement): Scope? = destructionScopes[element]?.copy()
//
//    /*
//     * Records that `sub_closure` is defined within `sup_closure`. These ids  should be the id of the block that is the
//     * fn body, which is  also the root of the region hierarchy for that fn.
//     */
//    fun recordClosureParent(subClosure: RsElement, supClosure: RsElement) {
//        require(subClosure != supClosure)
//        val previous = closureTree.put(subClosure, supClosure)
//        assert(previous == null)
//    }
//
//    fun recordVarScope(variable: RsElement, lifetime: Scope) {
//        assert(variable != lifetime.element)
//        variableMap[variable] = lifetime
//    }
//
//    fun recordRvalueScope(variable: RsElement, lifetime: Scope?) {
//        if (lifetime != null) {
//            assert(variable != lifetime.element)
//        }
//        rvalueScopes[variable] = lifetime
//    }
//
//    /*
//     * Returns the narrowest scope that encloses `id`, if any.
//     */
//    fun enclosingScope(scope: Scope): Scope? = parentMap[scope]?.copy()?.first
//
//    /*
//     * Returns the lifetime of the local variable `var_id`
//     */
//    fun variableScope(variable: RsElement): Scope = checkNotNull(variableMap[variable])
//
//    /*
//     * Returns the scope when temp created by expr_id will be cleaned up
//     */
//    fun temporaryScope(expr: RsElement): Scope? {
//        // check for a designated rvalue scope
//        rvalueScopes[expr]?.let { return it }
//
//        // else, locate the innermost terminating scope  if there's one. Static items, for instance, won't have an
//        // enclosing scope, hence no scope will be returned.
//        var current = Scope.node(expr)
//        while (true) {
//            val parent = parentMap[current]?.first ?: break
//            if (parent.data is ScopeData.Destruction) {
//                return current
//            } else {
//                current = parent
//            }
//        }
//
//        return null
//    }
//
//    /*
//     * Returns the lifetime of the variable `element`.
//     */
//    fun variableRegion(element: RsElement): Region = ReScope(variableScope(element))
//
//    fun scopeIntersect(scope1: Scope, scope2: Scope): Boolean =
//        isSubscopeOf(scope1, scope2) || isSubscopeOf(scope2, scope1)
//
//    /*
//     * Returns true if `subScope` is equal to or is lexically nested inside `superScope` and false otherwise.
//     */
//    fun isSubscopeOf(subScope: Scope, superScope: Scope): Boolean {
//        var scope = subScope
//        while (scope != superScope) {
//            scope = enclosingScope(scope) ?: return false
//        }
//        return true
//    }
//
//    /*
//     * Returns the id of the innermost containing body
//     */
//    fun containingBody(_scope: Scope): RsElement? {
//        var scope = _scope
//        while (true) {
//            (scope.data as? ScopeData.CallSite)?.let { return it.element }
//            scope = enclosingScope(scope) ?: return null
//        }
//    }
//
//    /*
//     * Finds the nearest common ancestor of two scopes.  That is, finds the smallest scope which is greater than or
//     * equal to both `scope1` and `scope2`.
//     */
//    fun nearestCommonAncestor(_scope1: Scope, _scope2: Scope): Scope {
//        if (_scope1 == _scope2) return _scope1
//
//        var scope1 = _scope1
//        var scope2 = _scope2
//
//        // Get the depth of each scope's parent. If either scope has no parent, it must be the root, which means we can
//        // stop immediately because the root must be the nearest common ancestor. (In practice, this is moderately
//        // common.)
//        val (parent1, parent1Depth) = parentMap[scope1] ?: return scope1
//        val (parent2, parent2Depth) = parentMap[scope2] ?: return scope2
//
//        when {
//            parent1Depth > parent2Depth -> {
//                // `a` is lower than `b`. Move `a` up until it's at the same depth  as `b`. The first move up is trivial
//                // because we already found `parent_a` above; the loop does the remaining N-1 moves.
//                scope1 = parent1
//                repeat(parent2Depth - parent1Depth - 1) {
//                    scope1 = parentMap[scope1]!!.first
//                }
//            }
//            parent2Depth > parent1Depth -> {
//                // `b` is lower than `a`.
//                scope2 = parent2
//                repeat(parent1Depth - parent2Depth - 1) {
//                    scope2 = parentMap[scope2]!!.first
//                }
//            }
//            else -> {
//                // Both scopes are at the same depth, and we know they're not equal  because that case was tested for at
//                // the top of this function. So we can trivially move them both up one level now.
//                assert(parent1Depth != 0)
//                scope1 = parent1
//                scope2 = parent2
//            }
//        }
//
//        // Now both scopes are at the same level. We move upwards in lockstep  until they match. In practice, this loop
//        // is almost always executed zero times because `a` is almost always a direct ancestor of `b` or vice versa.
//        while (scope1 != scope2) {
//            scope1 = parentMap[scope1]!!.first
//            scope2 = parentMap[scope2]!!.first
//        }
//
//        return scope1
//    }
//
//    /*
//     * Assuming that the provided region was defined within this `ScopeTree`, returns the outermost `Scope` that the
//     * region outlives.
//     */
//    fun earlyFreeScope(region: ReEarlyBound): Scope {
//        val paramOwner = TODO()
//        val paramOwnerElement
//
//        return Scope.callSite(element)
//    }
//}
//
//class Context {
//    val root: RsElement? = null
//    val variableParent: ScopeInfo? = null
//    val parent: ScopeInfo? = null
//}
