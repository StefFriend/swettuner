package app.sweettuner

import android.content.Context
import app.sweettuner.core.Sweetening
import app.sweettuner.core.Sweetenings
import app.sweettuner.core.Tuning
import app.sweettuner.core.Tunings

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("sweet_tuner", Context.MODE_PRIVATE)

    var tuning: Tuning
        get() = Tunings.byId(sp.getString(KEY_TUNING, null))
        set(v) = sp.edit().putString(KEY_TUNING, v.id).apply()

    var sweetening: Sweetening
        get() = Sweetenings.byId(sp.getString(KEY_SWEET, null))
        set(v) = sp.edit().putString(KEY_SWEET, v.id).apply()

    /** Reference pitch for A4 in Hz. */
    var a4: Int
        get() = sp.getInt(KEY_A4, 440).coerceIn(A4_MIN, A4_MAX)
        set(v) = sp.edit().putInt(KEY_A4, v.coerceIn(A4_MIN, A4_MAX)).apply()

    companion object {
        const val A4_MIN = 415
        const val A4_MAX = 466
        private const val KEY_TUNING = "tuning"
        private const val KEY_SWEET = "sweetening"
        private const val KEY_A4 = "a4"
    }
}
