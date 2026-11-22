package com.scicalc.fx82.ui

import androidx.compose.ui.graphics.Color
import com.scicalc.fx82.core.KeyStyle

/** Colours chosen to feel like a hand-held scientific calculator: dark shell, grey-green LCD, gold SHIFT, red ALPHA. */
object Palette {
    val Body = Color(0xFF26282C)
    val BodyEdge = Color(0xFF16171A)

    val LcdBg = Color(0xFFC3CEB2)
    val LcdBgEdge = Color(0xFF8E9A7E)
    val LcdInk = Color(0xFF1A2015)
    val LcdGhost = Color(0x261A2015)

    val ShiftGold = Color(0xFFE2B341)
    val AlphaRed = Color(0xFFE0675A)

    val KeyFunction = Color(0xFF3B3E44)
    val KeyFunctionText = Color(0xFFF1F1EF)
    val KeyDigit = Color(0xFFE9E8E3)
    val KeyDigitText = Color(0xFF16171A)
    val KeyOperator = Color(0xFFC9CAC8)
    val KeyDelete = Color(0xFFD9602F)
    val KeyEquals = Color(0xFF4F7FB8)
    val KeyNav = Color(0xFF2F3237)

    fun keyColor(style: KeyStyle): Color = when (style) {
        KeyStyle.SHIFT -> ShiftGold
        KeyStyle.ALPHA -> AlphaRed
        KeyStyle.NAV -> KeyNav
        KeyStyle.FUNCTION -> KeyFunction
        KeyStyle.DIGIT -> KeyDigit
        KeyStyle.OPERATOR -> KeyOperator
        KeyStyle.DELETE -> KeyDelete
        KeyStyle.EQUALS -> KeyEquals
    }

    fun keyText(style: KeyStyle): Color = when (style) {
        KeyStyle.DIGIT, KeyStyle.OPERATOR, KeyStyle.SHIFT -> KeyDigitText
        else -> KeyFunctionText
    }
}
