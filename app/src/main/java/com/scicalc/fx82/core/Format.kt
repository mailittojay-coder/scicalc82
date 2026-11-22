package com.scicalc.fx82.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.roundToLong

sealed class DisplayMode {
    object Norm1 : DisplayMode()
    object Norm2 : DisplayMode()
    data class Fix(val digits: Int) : DisplayMode()
    data class Sci(val sig: Int) : DisplayMode()
}

object Format {

    private val SUPERSCRIPT = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '-' to '⁻'
    )

    /** Decimal text of [v] for the given display mode, 10 significant digits at most. */
    fun decimal(v: Double, mode: DisplayMode): String = when (mode) {
        is DisplayMode.Norm1 -> norm(v, 1e-2, 1e10)
        is DisplayMode.Norm2 -> norm(v, 1e-9, 1e10)
        is DisplayMode.Fix -> fix(v, mode.digits)
        is DisplayMode.Sci -> sci(v, mode.sig)
    }

    private fun norm(v: Double, lowLimit: Double, highLimit: Double): String {
        if (v == 0.0) return "0"
        val a = abs(v)
        if (a < lowLimit || a >= highLimit) return sci(v, 10, trimZeros = true)
        val bd = BigDecimal(v).round(MathContext(10, RoundingMode.HALF_UP))
        return plain(bd)
    }

    private fun fix(v: Double, digits: Int): String {
        val bd = BigDecimal(v).setScale(digits, RoundingMode.HALF_UP)
        val s = bd.toPlainString()
        return if (bd.signum() == 0 && s.startsWith("-")) s.substring(1) else s
    }

    private fun sci(v: Double, sig: Int, trimZeros: Boolean = false): String {
        if (v == 0.0) return if (trimZeros) "0" else "0." + "0".repeat((sig - 1).coerceAtLeast(0)) + "×10⁰"
        val digits = if (sig == 0) 10 else sig
        val bd = BigDecimal(v).round(MathContext(digits, RoundingMode.HALF_UP))
        val exp = bd.precision() - bd.scale() - 1
        var mant = bd.movePointLeft(exp).setScale(digits - 1, RoundingMode.HALF_UP)
        var e = exp
        if (mant.abs() >= BigDecimal.TEN) { mant = mant.movePointLeft(1); e += 1 }
        var m = mant.toPlainString()
        if (trimZeros && m.contains('.')) m = m.trimEnd('0').trimEnd('.')
        return m + "×10" + superscript(e)
    }

    private fun plain(bd: BigDecimal): String {
        var s = bd.stripTrailingZeros().toPlainString()
        if (s == "-0") s = "0"
        return s
    }

    fun superscript(n: Int): String = n.toString().map { SUPERSCRIPT[it] ?: it }.joinToString("")

    // ---------- fractions ----------

    class Fraction(val whole: Long, val num: Long, val den: Long, val negative: Boolean)

    /** Best fraction with denominator up to 9999 via continued fractions; null if not tidy. */
    fun toFraction(v: Double): Fraction? {
        if (v.isNaN() || v.isInfinite()) return null
        val neg = v < 0
        val x = abs(v)
        if (x >= 1e10) return null
        val w = floor(x).toLong()
        val f = x - w
        if (f < 1e-10) return Fraction(w, 0, 1, neg)

        // convergents h/k of f
        var hPrev = 1L; var h = 0L
        var kPrev = 0L; var k = 1L
        var y = f
        var found = false
        for (i in 0 until 25) {
            val a = floor(1.0 / y)
            val frac = 1.0 / y - a
            val ai = a.toLong()
            val hn = ai * h + hPrev
            val kn = ai * k + kPrev
            if (kn > 9999) break
            hPrev = h; h = hn
            kPrev = k; k = kn
            if (abs(f - h.toDouble() / k.toDouble()) < 1e-10) { found = true; break }
            if (frac < 1e-12) break
            y = frac
        }
        if (!found || k == 0L) return null
        return Fraction(w, h, k, neg)
    }

    /** "a⌟b" for proper, "w⌟n⌟d" for mixed, "n⌟d" improper if [improper]. Null if not representable. */
    fun fractionText(v: Double, improper: Boolean): String? {
        val fr = toFraction(v) ?: return null
        if (fr.num == 0L) return null
        val sign = if (fr.negative) "-" else ""
        return if (improper || fr.whole == 0L) {
            val top = fr.whole * fr.den + fr.num
            "$sign$top⌟${fr.den}"
        } else {
            "$sign${fr.whole}⌟${fr.num}⌟${fr.den}"
        }
    }

    /** Degrees → d°m′s″ */
    fun dms(v: Double): String {
        val neg = v < 0
        val x = abs(v)
        var d = floor(x).toLong()
        val minutesFull = (x - d) * 60.0
        var m = floor(minutesFull + 1e-9).toLong()
        var s = (minutesFull - m) * 60.0
        s = Math.round(s * 1000.0) / 1000.0
        if (s >= 60.0) { s -= 60.0; m += 1 }
        if (m >= 60) { m -= 60; d += 1 }
        val sText = if (s == floor(s)) s.toLong().toString() else BigDecimal(s).stripTrailingZeros().toPlainString()
        return (if (neg) "-" else "") + "$d°$m′$sText″"
    }
}
