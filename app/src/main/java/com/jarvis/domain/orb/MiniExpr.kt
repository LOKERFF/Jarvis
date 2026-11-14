package com.jarvis.domain.orb

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Tiny, safe math-expression compiler used by the orb's "custom_formula" shape.
 *
 * There is no reflection and no eval: the string is tokenised once and turned
 * into plain Kotlin lambdas. A malicious or broken formula simply returns null
 * and the orb falls back to a sphere, so the app can never execute anything.
 *
 * Supported:
 *   numbers        1, 0.5, -2.5, 1e-3
 *   variables      u, v   (surface parameters, 0..1)  and  t (time, seconds)
 *   constants      pi, tau, e
 *   operators      + - * / ^   and unary -
 *   functions      sin cos tan abs sqrt exp log ln floor ceil round sign
 *                  min(a,b) max(a,b) pow(a,b) atan2(y,x)
 */
/** Compiled orb expression: surface params u, v (0..1) and time t (seconds). */
typealias MiniExprFn = (u: Double, v: Double, t: Double) -> Double

object MiniExpr {

    fun parse(source: String): MiniExprFn? {
        return try {
            val parser = Parser(tokenize(source))
            val fn = parser.expression()
            parser.requireEnd()
            fn
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * A custom shape is three expressions separated by ';' -> x ; y ; z.
     * Returns null if the text does not contain exactly three valid formulas.
     */
    fun parseTriple(source: String): List<MiniExprFn>? {
        val parts = source.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size != 3) return null
        val fns = parts.map { parse(it) ?: return null }
        return fns
    }

    // ---------------------------------------------------------------- tokens

    private sealed interface Token {
        data class Num(val value: Double) : Token
        data class Ident(val name: String) : Token
        data class Sym(val char: Char) : Token
    }

    private fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++

                c.isDigit() || (c == '.' && i + 1 < input.length && input[i + 1].isDigit()) -> {
                    val start = i
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                    // optional scientific notation
                    if (i < input.length && (input[i] == 'e' || input[i] == 'E')) {
                        val save = i
                        i++
                        if (i < input.length && (input[i] == '+' || input[i] == '-')) i++
                        if (i < input.length && input[i].isDigit()) {
                            while (i < input.length && input[i].isDigit()) i++
                        } else {
                            i = save
                        }
                    }
                    val raw = input.substring(start, i)
                    val value = raw.toDoubleOrNull()
                        ?: throw IllegalArgumentException("bad number: $raw")
                    tokens += Token.Num(value)
                }

                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < input.length && (input[i].isLetterOrDigit() || input[i] == '_')) i++
                    tokens += Token.Ident(input.substring(start, i).lowercase())
                }

                c in "+-*/^()," -> {
                    tokens += Token.Sym(c)
                    i++
                }

                else -> throw IllegalArgumentException("unexpected character: $c")
            }
        }
        if (tokens.isEmpty()) throw IllegalArgumentException("empty formula")
        return tokens
    }

    // ---------------------------------------------------------------- parser

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun requireEnd() {
            if (pos != tokens.size) throw IllegalArgumentException("trailing input")
        }

        private fun peek(): Token? = tokens.getOrNull(pos)

        private fun next(): Token = tokens.getOrNull(pos++)
            ?: throw IllegalArgumentException("unexpected end of formula")

        private fun expect(char: Char) {
            val token = next()
            if (token !is Token.Sym || token.char != char) {
                throw IllegalArgumentException("expected '$char'")
            }
        }

        fun expression(): MiniExprFn = addSub()

        private fun addSub(): MiniExprFn {
            var left = mulDiv()
            while (true) {
                val token = peek()
                if (token is Token.Sym && (token.char == '+' || token.char == '-')) {
                    pos++
                    val right = mulDiv()
                    val l = left
                    left = if (token.char == '+') {
                        { u, v, t -> l(u, v, t) + right(u, v, t) }
                    } else {
                        { u, v, t -> l(u, v, t) - right(u, v, t) }
                    }
                } else {
                    return left
                }
            }
        }

        private fun mulDiv(): MiniExprFn {
            var left = unary()
            while (true) {
                val token = peek()
                if (token is Token.Sym && (token.char == '*' || token.char == '/')) {
                    pos++
                    val right = unary()
                    val l = left
                    left = if (token.char == '*') {
                        { u, v, t -> l(u, v, t) * right(u, v, t) }
                    } else {
                        { u, v, t ->
                            val d = right(u, v, t)
                            if (abs(d) < 1e-12) 0.0 else l(u, v, t) / d
                        }
                    }
                } else {
                    return left
                }
            }
        }

        private fun unary(): MiniExprFn {
            val token = peek()
            if (token is Token.Sym && token.char == '-') {
                pos++
                val inner = unary()
                return { u, v, t -> -inner(u, v, t) }
            }
            if (token is Token.Sym && token.char == '+') {
                pos++
                return unary()
            }
            return power()
        }

        private fun power(): MiniExprFn {
            val base = atom()
            val token = peek()
            if (token is Token.Sym && token.char == '^') {
                pos++
                val exponent = unary() // right associative
                return { u, v, t ->
                    val b = base(u, v, t)
                    val e = exponent(u, v, t)
                    if (b < 0.0) 0.0 else b.pow(e)
                }
            }
            return base
        }

        private fun atom(): MiniExprFn {
            val token = next()
            return when (token) {
                is Token.Num -> {
                    val value = token.value
                    { _, _, _ -> value }
                }

                is Token.Ident -> identifier(token.name)

                is Token.Sym -> {
                    if (token.char == '(') {
                        val inner = expression()
                        expect(')')
                        inner
                    } else {
                        throw IllegalArgumentException("unexpected '${token.char}'")
                    }
                }
            }
        }

        private fun identifier(name: String): MiniExprFn {
            // constants
            when (name) {
                "pi" -> return { _, _, _ -> Math.PI }
                "tau" -> return { _, _, _ -> Math.PI * 2.0 }
                "e" -> return { _, _, _ -> Math.E }
                "u", "x" -> return { u, _, _ -> u }
                "v", "y" -> return { _, v, _ -> v }
                "t" -> return { _, _, t -> t }
            }

            // functions
            if (peek() !is Token.Sym || (peek() as Token.Sym).char != '(') {
                throw IllegalArgumentException("unknown symbol: $name")
            }
            expect('(')

            if (name == "min" || name == "max" || name == "pow" || name == "atan2") {
                val a = expression()
                expect(',')
                val b = expression()
                expect(')')
                return when (name) {
                    "min" -> { u, v, t -> min(a(u, v, t), b(u, v, t)) }
                    "max" -> { u, v, t -> max(a(u, v, t), b(u, v, t)) }
                    "pow" -> { u, v, t -> a(u, v, t).pow(b(u, v, t)) }
                    else -> { u, v, t -> atan2(a(u, v, t), b(u, v, t)) }
                }
            }

            val a = expression()
            expect(')')
            return when (name) {
                "sin" -> { u, v, t -> sin(a(u, v, t)) }
                "cos" -> { u, v, t -> cos(a(u, v, t)) }
                "tan" -> { u, v, t -> tan(a(u, v, t)) }
                "abs" -> { u, v, t -> abs(a(u, v, t)) }
                "sqrt" -> { u, v, t -> sqrt(abs(a(u, v, t))) }
                "exp" -> { u, v, t -> exp(a(u, v, t).coerceIn(-50.0, 50.0)) }
                "log", "ln" -> { u, v, t -> ln(abs(a(u, v, t)).coerceAtLeast(1e-9)) }
                "floor" -> { u, v, t -> floor(a(u, v, t)) }
                "ceil" -> { u, v, t -> ceil(a(u, v, t)) }
                "round" -> { u, v, t -> round(a(u, v, t)) }
                "sign" -> { u, v, t -> if (a(u, v, t) < 0) -1.0 else 1.0 }
                else -> throw IllegalArgumentException("unknown function: $name")
            }
        }
    }
}
