package com.scicalc.fx82.core

enum class Command {
    AC, DEL, LEFT, RIGHT, UP, DOWN, EQ,
    SHIFT, ALPHA, MODE, STO, RCL, MPLUS, MMINUS,
    SD, DMS, HYP, FRAC_FORMAT, DRG, CLR, INS
}

sealed interface Act { val label: String }
data class Ins(override val label: String, val tokens: List<String> = listOf(label)) : Act
data class Cmd(override val label: String, val cmd: Command) : Act

/** One physical key: what it does plain, with SHIFT, and with ALPHA. */
data class Key(
    val primary: Act,
    val shift: Act? = null,
    val alpha: Act? = null,
    val style: KeyStyle = KeyStyle.FUNCTION,
    val id: String = primary.label
)

enum class KeyStyle { SHIFT, ALPHA, NAV, FUNCTION, DIGIT, OPERATOR, DELETE, EQUALS }

object Layout {

    private fun ins(label: String, vararg tokens: String) =
        Ins(label, if (tokens.isEmpty()) listOf(label) else tokens.toList())

    private fun cmd(label: String, c: Command) = Cmd(label, c)

    private fun fn(
        primary: Act, shift: Act? = null, alpha: Act? = null,
        style: KeyStyle = KeyStyle.FUNCTION, id: String? = null
    ) = Key(primary, shift, alpha, style, id ?: primary.label)

    private fun v(name: String) = Ins(name)

    private fun digit(d: String, shift: Act? = null) = fn(ins(d), shift, null, KeyStyle.DIGIT)

    val rows: List<List<Key>> = listOf(
        // navigation row
        listOf(
            fn(cmd("SHIFT", Command.SHIFT), style = KeyStyle.SHIFT),
            fn(cmd("ALPHA", Command.ALPHA), style = KeyStyle.ALPHA),
            fn(cmd("◀", Command.LEFT), style = KeyStyle.NAV, id = "LEFT"),
            fn(cmd("▲", Command.UP), style = KeyStyle.NAV, id = "UP"),
            fn(cmd("▼", Command.DOWN), style = KeyStyle.NAV, id = "DOWN"),
            fn(cmd("▶", Command.RIGHT), style = KeyStyle.NAV, id = "RIGHT")
        ),
        listOf(
            fn(cmd("MODE", Command.MODE), cmd("CLR", Command.CLR)),
            fn(ins("x⁻¹", T.RECIP), ins("x!", T.FACT)),
            fn(ins("nCr", T.NCR), ins("nPr", T.NPR)),
            fn(ins("Pol(", "Pol("), ins("Rec(", "Rec(")),
            fn(ins("x³", T.CUBE), ins("∛", "∛("))
        ),
        listOf(
            fn(ins("a b/c", T.FRAC), cmd("d/c", Command.FRAC_FORMAT), id = "FRAC"),
            fn(ins("√", "√("), ins("Abs", "Abs(")),
            fn(ins("x²", T.SQ), ins("%", T.PERCENT)),
            fn(ins("^", T.POW), ins("ˣ√", T.ROOT)),
            fn(ins("log", "log("), ins("10ˣ", "10^(")),
            fn(ins("ln", "ln("), ins("eˣ", "e^("))
        ),
        listOf(
            fn(ins("(−)", T.NEG), null, v("A"), id = "NEG"),
            fn(cmd("°′″", Command.DMS), null, v("B"), id = "DMS"),
            fn(cmd("hyp", Command.HYP), null, v("C")),
            fn(ins("sin", "sin("), ins("sin⁻¹", "sin⁻¹("), v("D")),
            fn(ins("cos", "cos("), ins("cos⁻¹", "cos⁻¹("), v("E")),
            fn(ins("tan", "tan("), ins("tan⁻¹", "tan⁻¹("), v("F"))
        ),
        listOf(
            fn(cmd("RCL", Command.RCL), cmd("STO", Command.STO), id = "RCL"),
            fn(ins("π", T.PI), ins("e", T.E)),
            fn(ins("(", T.LPAR), null, v("Y")),
            fn(ins(")", T.RPAR), ins(",", T.COMMA), v("X")),
            fn(cmd("S⇔D", Command.SD), id = "SD"),
            fn(cmd("M+", Command.MPLUS), cmd("M−", Command.MMINUS), v("M"))
        ),
        listOf(
            digit("7"), digit("8"), digit("9"),
            fn(cmd("DEL", Command.DEL), cmd("INS", Command.INS), style = KeyStyle.DELETE),
            fn(cmd("AC", Command.AC), cmd("CLR", Command.CLR), style = KeyStyle.DELETE)
        ),
        listOf(
            digit("4"), digit("5"), digit("6"),
            fn(ins("×", T.TIMES), style = KeyStyle.OPERATOR),
            fn(ins("÷", T.DIV), style = KeyStyle.OPERATOR)
        ),
        listOf(
            digit("1"), digit("2"), digit("3"),
            fn(ins("+", T.PLUS), style = KeyStyle.OPERATOR),
            fn(ins("−", T.MINUS), style = KeyStyle.OPERATOR, id = "MINUS")
        ),
        listOf(
            digit("0"),
            fn(ins("."), ins("RanInt", "RanInt("), style = KeyStyle.DIGIT, id = "DOT"),
            fn(ins("×10ˣ", T.EXP), ins("Ran#", T.RAN)),
            fn(ins("Ans", T.ANS), cmd("DRG▸", Command.DRG)),
            fn(cmd("=", Command.EQ), style = KeyStyle.EQUALS, id = "EQ")
        )
    )

    private val byId: Map<String, Key> = rows.flatten().associateBy { it.id }

    fun key(id: String): Key = byId[id] ?: error("No key $id")
}
