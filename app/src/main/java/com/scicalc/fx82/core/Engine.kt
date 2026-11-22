package com.scicalc.fx82.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tanh

enum class AngleUnit(val label: String) { DEG("DEG"), RAD("RAD"), GRA("GRA") }

enum class ErrorKind(val message: String) {
    MATH("Math ERROR"),
    SYNTAX("Syntax ERROR")
}

class CalcException(val kind: ErrorKind) : Exception(kind.message)

/** Token vocabulary shared by the key handler, the parser and the display. */
object T {
    const val NEG = "(−)"        // unary minus, entered with the (−) key
    const val MINUS = "−"        // binary minus
    const val TIMES = "×"
    const val DIV = "÷"
    const val PLUS = "+"
    const val POW = "^"
    const val ROOT = "ˣ√"
    const val NPR = "nPr"
    const val NCR = "nCr"
    const val EXP = "×10ˣ"
    const val FRAC = "⌟"
    const val DEG = "°"
    const val MIN = "′"
    const val SEC = "″"
    const val LPAR = "("
    const val RPAR = ")"
    const val COMMA = ","
    const val SQ = "²"
    const val CUBE = "³"
    const val RECIP = "⁻¹"
    const val FACT = "!"
    const val PERCENT = "%"
    const val PI = "π"
    const val E = "e"
    const val ANS = "Ans"
    const val RAN = "Ran#"

    val VARS = listOf("A", "B", "C", "D", "E", "F", "X", "Y", "M")
    val DIGITS = setOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

    val ONE_ARG = setOf(
        "sin(", "cos(", "tan(", "sin⁻¹(", "cos⁻¹(", "tan⁻¹(",
        "sinh(", "cosh(", "tanh(", "sinh⁻¹(", "cosh⁻¹(", "tanh⁻¹(",
        "log(", "ln(", "10^(", "e^(", "√(", "∛(", "Abs("
    )
    val TWO_ARG = setOf("Pol(", "Rec(", "RanInt(")
}

class EvalContext(
    val angle: AngleUnit,
    val vars: MutableMap<String, Double>,
    val ans: Double,
    val random: java.util.Random = java.util.Random()
) {
    var sawFraction = false
    /** Set by Pol( and Rec( : labelled pair of results. */
    var multi: List<Pair<String, Double>>? = null
}

class EvalResult(val values: List<Pair<String?, Double>>, val sawFraction: Boolean)

object Evaluator {

    fun evaluate(tokens: List<String>, ctx: EvalContext): EvalResult {
        if (tokens.isEmpty()) throw CalcException(ErrorKind.SYNTAX)
        val value = Parser(tokens, ctx).parseAll()
        val multi = ctx.multi
        val values: List<Pair<String?, Double>> =
            if (multi != null && isSingleCall(tokens)) {
                multi.map { (label, v) -> label to finish(v) }
            } else {
                listOf<Pair<String?, Double>>(null to finish(value))
            }
        return EvalResult(values, ctx.sawFraction)
    }

    private fun finish(v: Double): Double = round(15, check(v))

    /** True when the whole expression is one Pol( / Rec( call. */
    private fun isSingleCall(tokens: List<String>): Boolean {
        if (tokens[0] != "Pol(" && tokens[0] != "Rec(") return false
        var depth = 0
        for (i in tokens.indices) {
            val t = tokens[i]
            if (t.endsWith("(") && t != T.NEG) depth++
            if (t == T.RPAR) depth--
            if (depth <= 0 && i < tokens.lastIndex) return false
        }
        return true
    }

    fun round(digits: Int, v: Double): Double {
        if (v == 0.0 || v.isNaN() || v.isInfinite()) return v
        return BigDecimal(v).round(MathContext(digits, RoundingMode.HALF_UP)).toDouble()
    }

    fun check(v: Double): Double {
        if (v.isNaN() || v.isInfinite() || abs(v) >= 1e100) throw CalcException(ErrorKind.MATH)
        return v
    }
}

private class Parser(private val t: List<String>, private val ctx: EvalContext) {
    private var p = 0

    private fun syntax(): Nothing = throw CalcException(ErrorKind.SYNTAX)
    private fun math(): Nothing = throw CalcException(ErrorKind.MATH)
    private fun peek(): String? = if (p < t.size) t[p] else null
    private fun chk(v: Double): Double = Evaluator.check(v)

    fun parseAll(): Double {
        val v = parseExpr()
        if (p != t.size) syntax()
        return v
    }

    // expr := mulDiv (('+'|'−') mulDiv)*
    private fun parseExpr(): Double {
        var v = parseMulDiv()
        while (true) {
            when (peek()) {
                T.PLUS -> { p++; v = chk(v + parseMulDiv()) }
                T.MINUS -> { p++; v = chk(v - parseMulDiv()) }
                else -> return v
            }
        }
    }

    // mulDiv := permComb (('×'|'÷') permComb)*
    private fun parseMulDiv(): Double {
        var v = parsePermComb()
        while (true) {
            when (peek()) {
                T.TIMES -> { p++; v = chk(v * parsePermComb()) }
                T.DIV -> {
                    p++
                    val d = parsePermComb()
                    if (d == 0.0) math()
                    v = chk(v / d)
                }
                else -> return v
            }
        }
    }

    // permComb := implicit (('nPr'|'nCr') implicit)*
    private fun parsePermComb(): Double {
        var v = parseImplicit()
        while (true) {
            when (peek()) {
                T.NPR -> { p++; v = permutations(v, parseImplicit()) }
                T.NCR -> { p++; v = combinations(v, parseImplicit()) }
                else -> return v
            }
        }
    }

    // implicit := unary unary*   (2π, 3(4+1), 2sin(30) ...)
    private fun parseImplicit(): Double {
        var v = parseUnary()
        while (startsPrimary(peek())) {
            v = chk(v * parseUnary())
        }
        return v
    }

    private fun startsPrimary(tok: String?): Boolean {
        if (tok == null) return false
        return tok in T.DIGITS || tok == "." || tok == T.LPAR || tok == T.NEG ||
            tok in T.ONE_ARG || tok in T.TWO_ARG || tok == T.PI || tok == T.E ||
            tok == T.ANS || tok == T.RAN || tok in T.VARS
    }

    private fun parseUnary(): Double {
        if (peek() == T.NEG) {
            p++
            return -parseUnary()
        }
        return parsePower()
    }

    // power := postfix (('^'|'ˣ√') exponent)?   right associative
    private fun parsePower(): Double {
        val base = parsePostfix()
        return when (peek()) {
            T.POW -> { p++; power(base, parseExponent()) }
            T.ROOT -> { p++; root(base, parseExponent()) }
            else -> base
        }
    }

    private fun parseExponent(): Double {
        if (peek() == T.NEG) {
            p++
            return -parseExponent()
        }
        return parsePower()
    }

    private fun parsePostfix(): Double {
        var v = parsePrimary()
        while (true) {
            when (peek()) {
                T.SQ -> { p++; v = chk(v * v) }
                T.CUBE -> { p++; v = chk(v * v * v) }
                T.RECIP -> { p++; if (v == 0.0) math(); v = 1.0 / v }
                T.FACT -> { p++; v = factorial(v) }
                T.PERCENT -> { p++; v /= 100.0 }
                else -> return v
            }
        }
    }

    private fun parsePrimary(): Double {
        val tok = peek() ?: syntax()
        when {
            tok in T.DIGITS || tok == "." -> return parseNumber()
            tok == T.LPAR -> {
                p++
                val v = parseExpr()
                closeParen()
                return v
            }
            tok in T.ONE_ARG -> {
                p++
                val a = parseExpr()
                closeParen()
                return applyFunction(tok, a)
            }
            tok in T.TWO_ARG -> {
                p++
                val a = parseExpr()
                if (peek() != T.COMMA) syntax()
                p++
                val b = parseExpr()
                closeParen()
                return applyTwoArg(tok, a, b)
            }
            tok == T.PI -> { p++; return Math.PI }
            tok == T.E -> { p++; return Math.E }
            tok == T.ANS -> { p++; return ctx.ans }
            tok == T.RAN -> { p++; return floor(ctx.random.nextDouble() * 1000.0) / 1000.0 }
            tok in T.VARS -> { p++; return ctx.vars[tok] ?: 0.0 }
        }
        syntax()
    }

    /** A closing parenthesis may be left out at the very end of the expression. */
    private fun closeParen() {
        when (peek()) {
            T.RPAR -> p++
            null -> {}
            else -> syntax()
        }
    }

    private fun startsDigit(): Boolean = peek().let { it != null && (it in T.DIGITS || it == ".") }

    private fun readDigits(): String {
        val sb = StringBuilder()
        while (p < t.size && (t[p] in T.DIGITS || t[p] == ".")) {
            sb.append(t[p]); p++
        }
        return sb.toString()
    }

    private fun toNumber(s: String): Double {
        if (s.isEmpty() || s.count { it == '.' } > 1 || s == ".") syntax()
        return s.toDoubleOrNull() ?: syntax()
    }

    private fun parseNumber(): Double {
        val s = readDigits()
        val first = toNumber(s)

        // fractions: a⌟b  or  a⌟b⌟c (mixed)
        if (peek() == T.FRAC) {
            p++
            val s2 = readDigits()
            if (s.contains('.') || s2.isEmpty() || s2.contains('.')) syntax()
            val whole: Double
            val num: Double
            val den: Double
            if (peek() == T.FRAC) {
                p++
                val s3 = readDigits()
                if (s3.isEmpty() || s3.contains('.')) syntax()
                whole = first; num = s2.toDouble(); den = s3.toDouble()
            } else {
                whole = 0.0; num = first; den = s2.toDouble()
            }
            if (den == 0.0) math()
            ctx.sawFraction = true
            return whole + num / den
        }

        // scientific entry: 1.5 ×10ˣ -3
        if (peek() == T.EXP) {
            p++
            var negative = false
            if (peek() == T.NEG) { p++; negative = true }
            val e = readDigits()
            if (e.isEmpty() || e.contains('.')) syntax()
            val text = (if (s.startsWith(".")) "0$s" else s) + "E" + (if (negative) "-" else "") + e
            return text.toDoubleOrNull() ?: syntax()
        }

        // degrees-minutes-seconds: 1°30′15″
        if (peek() == T.DEG) {
            p++
            var total = first
            if (startsDigit()) {
                val m = toNumber(readDigits())
                if (peek() != T.MIN) syntax()
                p++
                total += m / 60.0
                if (startsDigit()) {
                    val sec = toNumber(readDigits())
                    if (peek() == T.SEC) p++
                    total += sec / 3600.0
                }
            }
            return total
        }
        return first
    }

    // ---------- maths ----------

    private fun applyFunction(name: String, a: Double): Double = when (name) {
        "sin(" -> trig(0, a)
        "cos(" -> trig(1, a)
        "tan(" -> trig(2, a)
        "sin⁻¹(" -> { if (abs(a) > 1.0) math(); fromRadians(asin(a)) }
        "cos⁻¹(" -> { if (abs(a) > 1.0) math(); fromRadians(acos(a)) }
        "tan⁻¹(" -> fromRadians(atan(a))
        "sinh(" -> chk(sinh(a))
        "cosh(" -> chk(cosh(a))
        "tanh(" -> tanh(a)
        "sinh⁻¹(" -> sign(a) * ln(abs(a) + sqrt(a * a + 1.0))
        "cosh⁻¹(" -> { if (a < 1.0) math(); ln(a + sqrt(a * a - 1.0)) }
        "tanh⁻¹(" -> { if (abs(a) >= 1.0) math(); 0.5 * ln((1.0 + a) / (1.0 - a)) }
        "log(" -> { if (a <= 0.0) math(); log10(a) }
        "ln(" -> { if (a <= 0.0) math(); ln(a) }
        "10^(" -> chk(10.0.pow(a))
        "e^(" -> chk(exp(a))
        "√(" -> { if (a < 0.0) math(); sqrt(a) }
        "∛(" -> cbrt(a)
        "Abs(" -> abs(a)
        else -> syntax()
    }

    private fun applyTwoArg(name: String, a: Double, b: Double): Double {
        when (name) {
            "Pol(" -> {
                val r = hypot(a, b)
                val theta = fromRadians(atan2(b, a))
                ctx.vars["X"] = Evaluator.round(15, r)
                ctx.vars["Y"] = Evaluator.round(15, theta)
                ctx.multi = listOf("r" to r, "θ" to theta)
                return r
            }
            "Rec(" -> {
                val x = a * trig(1, b)
                val y = a * trig(0, b)
                ctx.vars["X"] = Evaluator.round(15, x)
                ctx.vars["Y"] = Evaluator.round(15, y)
                ctx.multi = listOf("x" to x, "y" to y)
                return x
            }
            "RanInt(" -> {
                if (a != floor(a) || b != floor(b) || a > b) math()
                return a + floor(ctx.random.nextDouble() * (b - a + 1.0))
            }
        }
        syntax()
    }

    private fun fromRadians(r: Double): Double = when (ctx.angle) {
        AngleUnit.DEG -> Math.toDegrees(r)
        AngleUnit.RAD -> r
        AngleUnit.GRA -> r * 200.0 / Math.PI
    }

    /** kind: 0 = sin, 1 = cos, 2 = tan. Exact at multiples of a quarter turn, like a real calculator. */
    private fun trig(kind: Int, x: Double): Double {
        val quarter = when (ctx.angle) {
            AngleUnit.DEG -> 90.0
            AngleUnit.RAD -> Math.PI / 2.0
            AngleUnit.GRA -> 100.0
        }
        if (ctx.angle != AngleUnit.RAD && abs(x) >= 9e9) math()
        val q = x / quarter
        val nearest = Math.rint(q)
        val tolerance = (if (ctx.angle == AngleUnit.RAD) 1e-12 else 1e-13) * max(1.0, abs(q))
        if (abs(q - nearest) <= tolerance) {
            val k = (((nearest % 4.0) + 4.0) % 4.0).toInt()
            return when (kind) {
                0 -> doubleArrayOf(0.0, 1.0, 0.0, -1.0)[k]
                1 -> doubleArrayOf(1.0, 0.0, -1.0, 0.0)[k]
                else -> if (k % 2 == 0) 0.0 else math()
            }
        }
        val rad = when (ctx.angle) {
            AngleUnit.DEG -> Math.toRadians(x % 360.0)
            AngleUnit.RAD -> x
            AngleUnit.GRA -> (x % 400.0) * Math.PI / 200.0
        }
        return when (kind) {
            0 -> Math.sin(rad)
            1 -> Math.cos(rad)
            else -> Math.tan(rad)
        }
    }

    private fun power(a: Double, b: Double): Double {
        if (a == 0.0) {
            if (b <= 0.0) math()
            return 0.0
        }
        if (a < 0.0 && b != Math.rint(b)) math()
        return chk(a.pow(b))
    }

    /** x ˣ√ y  =  y^(1/x) */
    private fun root(x: Double, y: Double): Double {
        if (x == 0.0) math()
        if (y < 0.0) {
            val odd = x == Math.rint(x) && abs(x) % 2.0 == 1.0
            if (!odd) math()
            return chk(-((-y).pow(1.0 / x)))
        }
        return chk(y.pow(1.0 / x))
    }

    private fun factorial(v: Double): Double {
        if (v < 0.0 || v != floor(v) || v > 69.0) math()
        var r = 1.0
        for (i in 2..v.toInt()) r *= i
        return r
    }

    private fun requireCount(v: Double) {
        if (v < 0.0 || v != floor(v) || v >= 1e10) math()
    }

    private fun permutations(n: Double, r: Double): Double {
        requireCount(n); requireCount(r)
        if (r > n) math()
        var res = 1.0
        var i = 0.0
        while (i < r) {
            res *= (n - i)
            if (res >= 1e100) math()
            i += 1.0
        }
        return res
    }

    private fun combinations(n: Double, r: Double): Double {
        requireCount(n); requireCount(r)
        if (r > n) math()
        val k = minOf(r, n - r)
        var res = 1.0
        var i = 0.0
        while (i < k) {
            res = res * (n - i) / (i + 1.0)
            if (res >= 1e100) math()
            i += 1.0
        }
        return Math.rint(res)
    }
}
