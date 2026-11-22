package com.scicalc.fx82

import com.scicalc.fx82.core.AngleUnit
import com.scicalc.fx82.core.Calculator
import com.scicalc.fx82.core.DisplayMode
import kotlin.test.Test
import kotlin.test.assertEquals

class CalculatorTest {

    /**
     * Space separated key ids. A number such as "12.5" expands to its digit keys.
     * "S:xxx" = SHIFT then key, "A:xxx" = ALPHA then key.
     */
    private fun run(keys: String, setup: Calculator.() -> Unit = {}): Calculator {
        val c = Calculator(java.util.Random(1))
        c.setup()
        for (k in keys.trim().split(Regex("\\s+"))) press(c, k)
        return c
    }

    private fun press(c: Calculator, k: String) {
        when {
            k.startsWith("S:") -> { c.press("SHIFT"); c.press(k.substring(2)) }
            k.startsWith("A:") -> { c.press("ALPHA"); c.press(k.substring(2)) }
            Regex("[0-9.]+").matches(k) -> k.forEach { c.press(if (it == '.') "DOT" else it.toString()) }
            else -> c.press(k)
        }
    }

    private fun check(keys: String, expected: String, setup: Calculator.() -> Unit = {}) {
        val c = run(keys, setup)
        assertEquals(expected, c.errorText() ?: c.resultText(), "keys: $keys")
    }

    @Test fun basicArithmetic() {
        check("7 + 8 EQ", "15")
        check("12 × 3 MINUS 6 EQ", "30")
        check("2 + 3 × 4 EQ", "14")
        check("1 ÷ 3 EQ", "0.3333333333")
        check("0.1 + 0.2 EQ", "0.3")
        check("99999 × 99999 EQ", "9999800001")
        check("999999 × 999999 EQ", "9.99998×10¹¹")
    }

    @Test fun errors() {
        check("5 ÷ 0 EQ", "Math ERROR")
        check("5 + EQ", "Syntax ERROR")
        check("√ NEG 4 EQ", "Math ERROR")
        check("70 S:x⁻¹ EQ", "Math ERROR")
        check("( 5 EQ", "5")
        check("5 ) EQ", "Syntax ERROR")
    }

    @Test fun negation() {
        check("NEG 5 + 3 EQ", "-2")
        check("5 × NEG 2 EQ", "-10")
        check("NEG 2 x² EQ", "-4")
        check("( NEG 2 ) x² EQ", "4")
    }

    @Test fun powersAndRoots() {
        check("2 ^ 10 EQ", "1024")
        check("2 ^ NEG 1 EQ", "0.5")
        check("5 x² EQ", "25")
        check("3 x³ EQ", "27")
        check("√ 144 EQ", "12")
        check("3 S:^ 27 EQ", "3")
        check("S:x³ 27 EQ", "3")
        check("4 x⁻¹ EQ", "0.25")
        check("S:x³ NEG 8 EQ", "-2")
    }

    @Test fun trigDegrees() {
        check("sin 30 EQ", "0.5")
        check("cos 60 EQ", "0.5")
        check("tan 45 EQ", "1")
        check("sin 90 EQ", "1")
        check("cos 90 EQ", "0")
        check("tan 90 EQ", "Math ERROR")
        check("S:sin 1 EQ", "90")
        check("S:cos 0 EQ", "90")
        check("S:tan 1 EQ", "45")
        check("sin 180 EQ", "0")
    }

    @Test fun trigRadians() {
        check("sin π ÷ 2 EQ", "1") { angle = AngleUnit.RAD }
        check("cos π EQ", "-1") { angle = AngleUnit.RAD }
        check("sin 1 EQ", "0.8414709848") { angle = AngleUnit.RAD }
    }

    @Test fun trigGradians() {
        check("sin 100 EQ", "1") { angle = AngleUnit.GRA }
        check("S:tan 1 EQ", "50") { angle = AngleUnit.GRA }
    }

    @Test fun drgKeyCycles() {
        val c = run("S:Ans")
        assertEquals(AngleUnit.RAD, c.angle)
        c.press("SHIFT"); c.press("Ans")
        assertEquals(AngleUnit.GRA, c.angle)
        c.press("SHIFT"); c.press("Ans")
        assertEquals(AngleUnit.DEG, c.angle)
    }

    @Test fun logs() {
        check("log 1000 EQ", "3")
        check("ln S:π EQ", "1")
        check("S:ln 1 EQ", "2.718281828")
        check("S:log 2 EQ", "100")
        check("ln 0 EQ", "Math ERROR")
        check("log NEG 1 EQ", "Math ERROR")
    }

    @Test fun factorialsAndCombinatorics() {
        check("5 S:x⁻¹ EQ", "120")
        check("5 nCr 2 EQ", "10")
        check("5 S:nCr 2 EQ", "20")
        check("0 S:x⁻¹ EQ", "1")
        check("3 nCr 5 EQ", "Math ERROR")
    }

    @Test fun implicitMultiplication() {
        check("2 π EQ", "6.283185307")
        check("2 ( 3 + 4 ) EQ", "14")
        check("2 sin 30 EQ", "1")
        check("( 1 + 1 ) ( 2 + 2 ) EQ", "8")
    }

    @Test fun unclosedParenthesisAtEnd() {
        check("2 × ( 3 + 4 EQ", "14")
        check("sin 30 EQ", "0.5")
    }

    @Test fun ansChaining() {
        check("6 × 7 EQ + 8 EQ", "50")
        check("3 + 4 EQ Ans × 2 EQ", "14")
        check("9 EQ x² EQ", "81")
        check("2 EQ 3 EQ", "3")
    }

    @Test fun fractions() {
        check("1 FRAC 2 + 1 FRAC 3 EQ", "5⌟6")
        check("1 FRAC 2 + 1 FRAC 2 EQ", "1")
        check("2 FRAC 1 FRAC 2 EQ", "2⌟1⌟2")
        check("3 FRAC 4 EQ SD", "0.75")
        check("0.75 EQ SD", "3⌟4")
        check("1 FRAC 0 EQ", "Math ERROR")
        check("1 FRAC 3 × 3 EQ", "1")
    }

    @Test fun fractionImproper() {
        check("1 FRAC 2 + 2 EQ", "5⌟2") { mixedFractions = false }
        check("1 FRAC 2 + 2 EQ", "2⌟1⌟2")
    }

    @Test fun scientificEntry() {
        check("1.5 ×10ˣ 3 EQ", "1500")
        check("2 ×10ˣ NEG 3 EQ", "2×10⁻³")
        check("25 ×10ˣ NEG 3 EQ", "0.025")
        check("1 ×10ˣ 10 EQ", "1×10¹⁰")
    }

    @Test fun displayModes() {
        check("2 ÷ 3 EQ", "0.667") { displayMode = DisplayMode.Fix(3) }
        check("12345 EQ", "1.23×10⁴") { displayMode = DisplayMode.Sci(3) }
        check("1 ÷ 3 EQ", "0.3333333333")
        check("1 ÷ 300 EQ", "3.333333333×10⁻³")
        check("1 ÷ 300 EQ", "0.003333333333") { displayMode = DisplayMode.Norm2 }
    }

    @Test fun dms() {
        check("1 DMS 30 DMS EQ", "1.5")
        check("1 DMS 30 DMS 36 DMS EQ", "1.51")
        check("1.5 EQ DMS", "1°30′0″")
        check("sin 30 DMS EQ", "0.5")
    }

    @Test fun memoryAndVariables() {
        check("5 EQ S:RCL NEG AC A:NEG × 3 EQ", "15")
        check("5 M+ 7 M+ AC A:M+ EQ", "12")
        check("10 M+ 4 S:M+ AC A:M+ EQ", "6")
        check("2 EQ S:RCL DMS AC 3 EQ S:RCL hyp AC A:DMS + A:hyp EQ", "5")
    }

    @Test fun editing() {
        val c = run("123 DEL DEL 4")
        assertEquals("14", c.expressionText())
        val d = run("1 + 2 LEFT LEFT 9")
        assertEquals("19+2", d.expressionText())
        d.press("EQ")
        assertEquals("21", d.resultText())
    }

    @Test fun overwriteMode() {
        val c = run("1 2 3 LEFT LEFT S:DEL 9")
        assertEquals("193", c.expressionText())
    }

    @Test fun historyReplay() {
        val c = run("1 + 2 EQ AC 4 × 5 EQ UP")
        assertEquals("4×5", c.expressionText())
        c.press("UP")
        assertEquals("1+2", c.expressionText())
        c.press("DOWN")
        assertEquals("4×5", c.expressionText())
    }

    @Test fun editAfterError() {
        val c = run("5 ÷ 0 EQ")
        assertEquals("Math ERROR", c.errorText())
        c.press("LEFT")
        c.press("DEL")
        c.press("2")
        c.press("EQ")
        assertEquals("2.5", c.resultText())
    }

    @Test fun polarRectangular() {
        check("Pol( 3 S:) 4 ) EQ", "r = 5  θ = 53.13010235")
        check("Pol( 1 S:) 1 ) EQ", "r = 1.414213562  θ = 45")
        check("S:Pol( 2 S:) 30 ) EQ", "x = 1.732050808  y = 1")
    }

    @Test fun hyperbolic() {
        check("hyp sin 0 EQ", "0")
        check("hyp cos 0 EQ", "1")
        check("hyp S:sin 0 EQ", "0")
        check("hyp sin 1 EQ", "1.175201194")
    }

    @Test fun percentAndAbs() {
        check("50 S:x² EQ", "0.5")
        check("S:√ NEG 7 EQ", "7")
    }

    @Test fun randoms() {
        val c = run("S:DOT 1 S:) 6 ) EQ")
        val r = c.resultText().toInt()
        assert(r in 1..6) { "RanInt result $r" }
    }
}
