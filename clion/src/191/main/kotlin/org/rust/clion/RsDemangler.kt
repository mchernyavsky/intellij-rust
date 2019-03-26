/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion

import kotlin.experimental.and

/**
 * Demangle Rust compiler symbol names.
 *
 * This objext provides a `demangle` function which will return a `Demangle` sentinel value that can be used to learn
 * about the demangled version of a symbol name. The demangled representation will be the same as the original if it
 * doesn't look like a mangled symbol name.
 */
object RsDemangler {
    /**
     * Representation of a demangled symbol name.
     */
    data class Demangle(
        val original: String,
        val inner: String,
        val suffix: String,
        val isValid: Boolean,
        val elementsNum: Int // The number of ::-separated elements in the original name.
    ) {
        fun format(skipHash: Boolean = false): String? {
            if (!isValid) return original

            val inner = inner

        }
    }

    /**
     * De-mangles a Rust symbol into a more readable version
     *
     * All Rust symbols by default are mangled as they contain characters that cannot be represented in all object files.
     * The mangling mechanism is similar to C++'s, but Rust has a few specifics to handle items like lifetimes in symbols.
     *
     * This function will take a **mangled** symbol and return a value. When printed, the de-mangled version will be written.
     * If the symbol does not look like a mangled symbol, the original value will be written instead.
     *
     * All Rust symbols are in theory lists of "::"-separated identifiers. Some assemblers, however, can't handle these
     * characters in symbol names. To get around this, we use C++-style mangling. The mangling method is:
     *
     * 1. Prefix the symbol with "_ZN"
     * 2. For each element of the path, emit the length plus the element
     * 3. End the path with "E"
     *
     * For example, "_ZN4testE" to "test" and "_ZN3foo3barE" to "foo::bar".
     *
     * We're the ones printing our backtraces, so we can't rely on anything else to demangle our symbols.
     * It's *much* nicer to look at demangled symbols, so this function is implemented to give us nice pretty output.
     *
     * Note that this demangler isn't quite as fancy as it could be. We have lots of other information in our symbols
     * like hashes, version, type information, etc. Additionally, this doesn't handle glue symbols at all.
     */
    fun demangle(name: String): Demangle {
        var text = name

        // During ThinLTO LLVM may import and rename internal symbols, so strip out those endings first as they're one
        // of the last manglings applied to symbol names.
        val llvm = ".llvm."
        val index = text.indexOf(llvm)
        if (index != -1) {
            val candidate = text.slice(index + llvm.length until text.length)
            val allHex = candidate.all { ch ->
                when (ch) {
                    in 'A'..'F', in '0'..'9', '@' -> true
                    else -> false
                }
            }

            if (allHex) {
                text = text.slice(0 until index)
            }
        }

        // Output like LLVM IR adds extra period-delimited words.
        // See if we are in that case and save the trailing words if so.
        var suffix = ""
        val textIndex = text.lastIndexOf("E.")
        if (textIndex != -1) {
            // Split at point after the E, before the period
            val head = text.slice(0..textIndex)
            val tail = text.slice(textIndex + 1..text.length)
            if (isSymbolLike(tail)) {
                text = head
                suffix = tail
            }
        }

        // First validate the symbol. If it doesn't look like anything we're expecting, we just print it literally.
        // Note that we must handle non-Rust symbols because we could have any function in the backtrace.
        var isValid = true
        var inner = ""
        if (text.length > 4 && text.startsWith("_ZN") && text.endsWith("E")) {
            inner = text.slice(3 until text.length)
        } else if (text.length > 3 && text.startsWith("ZN") && text.endsWith("E")) {
            // On Windows, dbghelp strips leading underscores, so we accept "ZN...E" form too.
            inner = text.slice(2 until text.length)
        } else if (text.length > 5 && text.startsWith("ZN") && text.endsWith("E")) {
            // On OSX, symbols are prefixed with an extra _
            inner = text.slice(4 until text.length)
        } else {
            isValid = false
        }

        // Only work with ascii text
        if (inner.toByteArray().any { byte -> byte and 0x80.toByte() != 0.toByte() }) {
            isValid = false
        }

        var elementsNum = 0
        if (isValid) {
            val chars = inner.chars()
            while (isValid) {
                var i = 0L
                // TODO
                while (chars.hasNext()) {
                    val char = chars.nextChar()

                }

                if (i == 0L) {
                    isValid = chars.hasNext()
                    break
                } else if (chars.limit(i).count() == i) {
                    isValid = false
                } else {
                    elementsNum += 1
                }
            }
        }

        return Demangle(text, inner, suffix, isValid, elementsNum)
    }

    /**
     * The same as `demangle`, except return `null` if the string does not appear to be a Rust symbol, rather than
     * "demangling" the given string as a no-op.
     */
    fun tryDemangle(name: String): Demangle? {
        val sym = demangle(name)
        return if (sym.isValid) sym else null
    }

    /**
     * Rust hashes are hex digits with an `h` prepended.
     */
    private fun isRustHash(text: String): Boolean = text.matches(RUST_HASH_RE)

    private fun isSymbolLike(text: String): Boolean =
        text.all { isAsciiAlphanumeric(it) || isAsciiPunctuation(it) }

    /**
     * Copied from the documentation of `char::is_ascii_alphanumeric`
     */
    private fun isAsciiAlphanumeric(ch: Char): Boolean =
        when (ch) {
            in '\u0041'..'\u005A',
            in '\u0061'..'\u007A',
            in '\u0030'..'\u0039' -> true
            else -> false
        }

    /**
     * Copied from the documentation of `char::is_ascii_punctuation`
     */
    private fun isAsciiPunctuation(ch: Char): Boolean =
        when (ch) {
            in '\u0021'..'\u002F',
            in '\u003A'..'\u0040',
            in '\u005B'..'\u0060',
            in '\u007B'..'\u007E' -> true
            else -> false
        }

    private val RUST_HASH_RE: Regex = Regex("^h[[:xdigit:]]*$")

    private val DOLLAR_TABLE: Map<String, String> =
        hashMapOf(
            "\$SP\$" to "@",
            "\$BP\$" to "*",
            "\$RF\$" to "&",
            "\$LT\$" to "<",
            "\$GT\$" to ">",
            "\$LP\$" to "(",
            "\$RP\$" to ")",
            "\$C\$" to ",",

            // In theory we can demangle any Unicode code point, but for simplicity we just catch the common ones.
            "\$u7e\$" to "~",
            "\$u20\$" to " ",
            "\$u27\$" to "'",
            "\$u3d\$" to "=",
            "\$u5b\$" to "[",
            "\$u5d\$" to "]",
            "\$u7b\$" to "{",
            "\$u7d\$" to "}",
            "\$u3b\$" to ";",
            "\$u2b\$" to "+",
            "\$u21\$" to "!",
            "\$u22\$" to "\""
        )
}
