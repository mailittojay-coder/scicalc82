package com.scicalc.fx82.core

/** Everything the screen needs to draw, as plain data. */
data class UiState(
    val before: String,            // expression text left of the cursor
    val after: String,             // expression text right of the cursor
    val resultLines: List<String>, // empty while editing
    val error: String?,
    val shift: Boolean,
    val alpha: Boolean,
    val store: Boolean,
    val recall: Boolean,
    val hyp: Boolean,
    val overwrite: Boolean,
    val hasMemory: Boolean,
    val angle: AngleUnit,
    val displayLabel: String,      // "", "Fix", "Sci"
    val fractionView: Boolean,
    val canScrollUp: Boolean,
    val canScrollDown: Boolean
)

enum class Outcome { NONE, OPEN_MODE }

class Calculator(private val random: java.util.Random = java.util.Random()) {

    var angle: AngleUnit = AngleUnit.DEG
    var displayMode: DisplayMode = DisplayMode.Norm1
    /** true → mixed fractions (a b/c), false → improper (d/c). */
    var mixedFractions: Boolean = true

    private val expr = mutableListOf<String>()
    private var cursor = 0

    private var shift = false
    private var alpha = false
    private var store = false
    private var recall = false
    private var hyp = false
    private var overwrite = false

    private var showingResult = false
    private var results: List<Pair<String?, Double>> = emptyList()
    private var sawFraction = false
    private var fractionView = false
    private var dmsView = false
    private var error: ErrorKind? = null

    private var ans = 0.0
    val vars = mutableMapOf<String, Double>()

    private val history = mutableListOf<List<String>>()
    private var historyIndex = -1

    // ---------- public API ----------

    fun press(key: Key): Outcome {
        val useAlpha = alpha || store || recall
        var act: Act? = when {
            useAlpha -> key.alpha
            shift -> key.shift
            else -> key.primary
        }
        // keys without an alpha function cancel STO/RCL and behave normally
        if (act == null && (store || recall)) {
            store = false; recall = false
            act = if (shift) key.shift else key.primary
        }
        // SHIFT/ALPHA presses and control keys keep working with a modifier held
        if (act == null && key.primary is Cmd) {
            val c = (key.primary as Cmd).cmd
            if (c == Command.SHIFT || c == Command.ALPHA || c == Command.LEFT || c == Command.RIGHT ||
                c == Command.UP || c == Command.DOWN || c == Command.EQ
            ) act = key.primary
        }
        val wasShift = shift
        var outcome = Outcome.NONE
        if (act != null) {
            val isModifier = act is Cmd && (act.cmd == Command.SHIFT || act.cmd == Command.ALPHA)
            if (!isModifier) {
                shift = false; alpha = false
            }
            outcome = perform(act, wasShift)
        } else {
            shift = false; alpha = false
        }
        return outcome
    }

    /** Convenience for tests and automation: press by key id, e.g. "EQ", "7", "sin". */
    fun press(id: String): Outcome = press(Layout.key(id))

    fun state(): UiState {
        val shown = display(expr)
        val before = shown.take(cursor).joinToString("")
        val after = shown.drop(cursor).joinToString("")
        return UiState(
            before = before,
            after = after,
            resultLines = if (showingResult) resultLines() else emptyList(),
            error = error?.message,
            shift = shift, alpha = alpha, store = store, recall = recall, hyp = hyp,
            overwrite = overwrite,
            hasMemory = (vars["M"] ?: 0.0) != 0.0,
            angle = angle,
            displayLabel = when (displayMode) {
                is DisplayMode.Fix -> "Fix"
                is DisplayMode.Sci -> "Sci"
                else -> ""
            },
            fractionView = showingResult && fractionView && results.size == 1,
            canScrollUp = history.isNotEmpty() && historyIndex != 0,
            canScrollDown = historyIndex != -1
        )
    }

    fun expressionText(): String = display(expr).joinToString("")

    /** Joined result text for tests. */
    fun resultText(): String = if (showingResult) resultLines().joinToString("  ") else ""

    fun errorText(): String? = error?.message

    fun reset() {
        vars.clear(); history.clear(); historyIndex = -1; ans = 0.0
        clearEntry()
    }

    // ---------- command handling ----------

    private fun perform(act: Act, shiftHeld: Boolean): Outcome {
        when (act) {
            is Ins -> insertKey(act)
            is Cmd -> return command(act.cmd, shiftHeld)
        }
        return Outcome.NONE
    }

    private fun insertKey(act: Ins) {
        val first = act.tokens[0]

        if (first in T.VARS) {
            // variable keys: STO stores, RCL/ALPHA insert
            if (store) {
                store = false
                val v = currentValue()
                if (v != null) vars[first] = v
                return
            }
            if (recall) { recall = false }
            insert(act.tokens)
            return
        }
        var tokens = act.tokens
        if (hyp && first in setOf("sin(", "cos(", "tan(", "sin⁻¹(", "cos⁻¹(", "tan⁻¹(")) {
            tokens = listOf(
                if (first.contains("⁻¹")) first.replace("⁻¹(", "h⁻¹(") else first.replace("(", "h(")
            )
        }
        hyp = false
        insert(tokens)
    }

    private fun command(c: Command, shiftHeld: Boolean): Outcome {
        when (c) {
            Command.SHIFT -> { shift = !shift; alpha = false; store = false; recall = false }
            Command.ALPHA -> { alpha = !alpha; shift = false; store = false; recall = false }
            Command.MODE -> return Outcome.OPEN_MODE
            Command.STO -> { store = true; recall = false }
            Command.RCL -> { recall = true; store = false }
            Command.HYP -> hyp = !hyp
            Command.CLR -> reset()
            Command.INS -> overwrite = !overwrite
            Command.DRG -> {
                angle = when (angle) {
                    AngleUnit.DEG -> AngleUnit.RAD
                    AngleUnit.RAD -> AngleUnit.GRA
                    AngleUnit.GRA -> AngleUnit.DEG
                }
            }
            Command.FRAC_FORMAT -> {
                mixedFractions = !mixedFractions
            }
            Command.AC -> clearEntry()
            Command.DEL -> delete()
            Command.LEFT -> { if (!startEditing(toEnd = true) && cursor > 0) cursor-- }
            Command.RIGHT -> { if (!startEditing(toEnd = false) && cursor < expr.size) cursor++ }
            Command.UP -> scrollHistory(-1)
            Command.DOWN -> scrollHistory(+1)
            Command.EQ -> equals()
            Command.SD -> toggleFraction()
            Command.DMS -> dmsKey()
            Command.MPLUS -> memory(+1.0)
            Command.MMINUS -> memory(-1.0)
        }
        return Outcome.NONE
    }

    // ---------- editing ----------

    private val continuing = setOf(
        T.PLUS, T.MINUS, T.TIMES, T.DIV, T.POW, T.SQ, T.CUBE, T.RECIP, T.FACT,
        T.PERCENT, T.NPR, T.NCR, T.ROOT
    )

    private fun clearEntry() {
        expr.clear(); cursor = 0
        showingResult = false; error = null
        results = emptyList(); dmsView = false; fractionView = false
        historyIndex = -1
        hyp = false
    }

    /** Leaves result/error view for the editor. Returns true if it did (the cursor is then already placed). */
    private fun startEditing(toEnd: Boolean): Boolean {
        if (showingResult || error != null) {
            showingResult = false; error = null
            cursor = if (toEnd) expr.size else 0
            return true
        }
        return false
    }

    private fun insert(tokens: List<String>) {
        var toks = tokens
        if (showingResult) {
            val keepAns = toks[0] in continuing
            expr.clear(); cursor = 0
            showingResult = false
            historyIndex = -1
            if (keepAns) toks = listOf(T.ANS) + toks
        }
        error = null
        if (overwrite) {
            var n = toks.size
            while (n > 0 && cursor < expr.size) { expr.removeAt(cursor); n-- }
        }
        expr.addAll(cursor, toks)
        cursor += toks.size
    }

    private fun delete() {
        if (showingResult || error != null) {
            showingResult = false; error = null
            cursor = expr.size
        }
        if (cursor > 0) {
            expr.removeAt(cursor - 1)
            cursor--
        }
    }

    private fun scrollHistory(dir: Int) {
        if (history.isEmpty()) return
        val next = when {
            dir < 0 && historyIndex == -1 -> history.lastIndex
            dir < 0 -> maxOf(0, historyIndex - 1)
            historyIndex == -1 -> return
            historyIndex + 1 > history.lastIndex -> -1
            else -> historyIndex + 1
        }
        historyIndex = next
        showingResult = false; error = null
        expr.clear()
        if (next >= 0) expr.addAll(history[next])
        cursor = expr.size
    }

    // ---------- evaluation ----------

    private fun equals() {
        if (showingResult) return
        if (expr.isEmpty()) return
        try {
            val ctx = EvalContext(angle, vars, ans, random)
            val res = Evaluator.evaluate(expr, ctx)
            results = res.values
            sawFraction = res.sawFraction
            fractionView = res.sawFraction
            dmsView = false
            ans = results[0].second
            error = null
            showingResult = true
            if (history.lastOrNull() != expr.toList()) {
                history.add(expr.toList())
                if (history.size > 30) history.removeAt(0)
            }
            historyIndex = -1
        } catch (e: CalcException) {
            error = e.kind
            showingResult = false
            cursor = expr.size
        }
    }

    /** Result of the current entry, evaluating it first if needed. */
    private fun currentValue(): Double? {
        if (!showingResult) equals()
        return if (showingResult) results[0].second else null
    }

    private fun memory(sign: Double) {
        val v = currentValue() ?: return
        vars["M"] = Evaluator.round(15, (vars["M"] ?: 0.0) + sign * v)
    }

    private fun toggleFraction() {
        if (!showingResult || results.size != 1) return
        val v = results[0].second
        dmsView = false
        if (!fractionView) {
            if (Format.fractionText(v, !mixedFractions) != null) fractionView = true
        } else {
            fractionView = false
        }
    }

    /** Result showing → toggle d°m′s″ view. Otherwise inserts ° ′ ″ in sequence. */
    private fun dmsKey() {
        if (showingResult && results.size == 1) {
            dmsView = !dmsView
            if (dmsView) fractionView = false
            return
        }
        var i = cursor - 1
        while (i >= 0 && (expr[i] in T.DIGITS || expr[i] == ".")) i--
        val prev = if (i >= 0 && i < cursor - 1) expr[i] else null
        insert(
            listOf(
                when (prev) {
                    T.DEG -> T.MIN
                    T.MIN -> T.SEC
                    else -> T.DEG
                }
            )
        )
    }

    // ---------- display ----------

    private fun resultLines(): List<String> =
        results.map { (label, v) ->
            val text = formatValue(v)
            if (label != null) "$label = $text" else text
        }

    private fun formatValue(v: Double): String {
        if (dmsView) return Format.dms(v)
        if (fractionView && results.size == 1) {
            Format.fractionText(v, !mixedFractions)?.let { return it }
        }
        return Format.decimal(v, displayMode)
    }

    private fun display(tokens: List<String>): List<String> = tokens.map {
        when (it) {
            T.EXP -> "ᴇ"
            T.NEG -> "−"
            T.MINUS -> "−"
            T.FRAC -> "⌟"
            else -> it
        }
    }
}
