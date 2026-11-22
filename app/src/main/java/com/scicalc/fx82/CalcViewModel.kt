package com.scicalc.fx82

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.scicalc.fx82.core.AngleUnit
import com.scicalc.fx82.core.Calculator
import com.scicalc.fx82.core.DisplayMode
import com.scicalc.fx82.core.Key
import com.scicalc.fx82.core.Outcome
import com.scicalc.fx82.core.T
import com.scicalc.fx82.core.UiState

class CalcViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("scicalc", Context.MODE_PRIVATE)
    private val calc = Calculator()

    var ui by mutableStateOf(calc.state())
        private set
    var showModeMenu by mutableStateOf(false)
        private set

    init {
        runCatching {
            calc.angle = AngleUnit.valueOf(prefs.getString("angle", "DEG") ?: "DEG")
            calc.mixedFractions = prefs.getBoolean("mixed", true)
            val n = prefs.getInt("digits", 0)
            calc.displayMode = when (prefs.getString("display", "norm1")) {
                "norm2" -> DisplayMode.Norm2
                "fix" -> DisplayMode.Fix(n.coerceIn(0, 9))
                "sci" -> DisplayMode.Sci(n.coerceIn(1, 10))
                else -> DisplayMode.Norm1
            }
            for (name in T.VARS) {
                prefs.getString("var_$name", null)?.toDoubleOrNull()?.let { calc.vars[name] = it }
            }
        }
        refresh()
    }

    fun press(key: Key) {
        if (calc.press(key) == Outcome.OPEN_MODE) showModeMenu = true
        refresh()
        save()
    }

    fun dismissModeMenu() { showModeMenu = false }

    // -------- settings exposed to the mode menu --------
    val angle: AngleUnit get() = calc.angle
    val displayMode: DisplayMode get() = calc.displayMode
    val mixedFractions: Boolean get() = calc.mixedFractions

    fun setAngle(a: AngleUnit) { calc.angle = a; refresh(); save() }
    fun setDisplay(d: DisplayMode) { calc.displayMode = d; refresh(); save() }
    fun setMixed(mixed: Boolean) { calc.mixedFractions = mixed; refresh(); save() }

    private fun refresh() { ui = calc.state() }

    private fun save() {
        prefs.edit().apply {
            putString("angle", calc.angle.name)
            putBoolean("mixed", calc.mixedFractions)
            when (val d = calc.displayMode) {
                is DisplayMode.Norm1 -> putString("display", "norm1")
                is DisplayMode.Norm2 -> putString("display", "norm2")
                is DisplayMode.Fix -> { putString("display", "fix"); putInt("digits", d.digits) }
                is DisplayMode.Sci -> { putString("display", "sci"); putInt("digits", d.sig) }
            }
            for (name in T.VARS) {
                val v = calc.vars[name]
                if (v != null) putString("var_$name", v.toString()) else remove("var_$name")
            }
            apply()
        }
    }
}
