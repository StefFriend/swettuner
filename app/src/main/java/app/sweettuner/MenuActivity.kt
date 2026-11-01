package app.sweettuner

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.sweettuner.core.ABOUT_SWEETENING
import app.sweettuner.core.Music
import app.sweettuner.core.StringMatcher
import app.sweettuner.core.Sweetening
import app.sweettuner.core.Sweetenings
import app.sweettuner.core.Tuning
import app.sweettuner.core.TuningFamily
import app.sweettuner.core.TuningGuide
import app.sweettuner.core.Tunings
import app.sweettuner.ui.padForSystemBars
import java.util.Locale

/** Tuning and sweetening selection, with explanations. Built in code: no layout XML, no libraries. */
class MenuActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout

    private val expandedTunings = mutableSetOf<String>()
    private val expandedSweet = mutableSetOf<String>()

    private val bg = 0xFF0D0F12.toInt()
    private val card = 0xFF161A20.toInt()
    private val cardSelected = 0xFF1E232B.toInt()
    private val fg = 0xFFF2F2F2.toInt()
    private val dim = 0xFF9AA1AC.toInt()
    private val faint = 0xFF2A2F37.toInt()
    private val accent = 0xFFE8B04B.toInt()

    private val mono = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }
        scroll.addView(content, MATCH_PARENT, WRAP_CONTENT)
        scroll.padForSystemBars()
        setContentView(scroll)
        render()
    }

    // ------------------------------------------------------------------ building the page

    private fun render() {
        val y = scroll.scrollY
        content.removeAllViews()
        val tuning = prefs.tuning
        val sweet = prefs.sweetening

        // Top bar
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(12))
            addView(label("‹", 34f, fg).apply {
                setPadding(dp(4), 0, dp(16), dp(4))
                setOnClickListener { finish() }
                contentDescription = "Back"
            })
            addView(label("Tuning & Sweetening", 21f, fg, medium))
        })

        content.addView(label("Current: ${tuning.name} · ${sweet.shortName} · A4 ${prefs.a4} Hz", 13f, accent).apply {
            setPadding(0, 0, 0, dp(8))
        })

        // Tunings
        section("TUNING")
        for (family in TuningFamily.entries) {
            subheader(family.title)
            Tunings.all.filter { it.family == family }.forEach { content.addView(tuningCard(it, it.id == tuning.id)) }
        }

        // Sweetenings
        section("SWEETENING")
        content.addView(paragraph(
            "Small per-string offsets from equal temperament. Offsets shown are for ${tuning.name}.", dim,
        ))
        Sweetenings.all.forEach { content.addView(sweetCard(it, tuning, it.id == sweet.id)) }

        // Reference pitch
        section("REFERENCE PITCH")
        content.addView(a4Row())

        // About
        section("ABOUT SWEETENED TUNING")
        content.addView(paragraph(ABOUT_SWEETENING, dim))
        content.addView(paragraph(
            "Tip: tune up to the note (from flat to pitch), not down, so the string settles in the nut " +
                "and stays in tune. Re-check every string once more after retuning to an open tuning: " +
                "the neck relief changes and earlier strings drift.", dim,
        ))

        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun tuningCard(t: Tuning, selected: Boolean): View {
        val expanded = t.id in expandedTunings
        val box = cardBox(selected)

        box.addView(titleRow(t.name, selected))
        box.addView(label(t.fullNotes(), 14f, if (selected) accent else dim, mono).apply { setPadding(0, dp(2), 0, dp(4)) })
        box.addView(label(t.summary, 13.5f, dim))
        box.addView(toggle(expanded) {
            if (!expandedTunings.add(t.id)) expandedTunings.remove(t.id)
            render()
        })

        if (expanded) {
            box.addView(paragraph(t.details, fg))
            t.chordName?.let { box.addView(paragraph("Open strings: $it chord.", dim)) }
            val fromStd = TuningGuide.fromStandard(t)
            if (fromStd.isNotEmpty()) {
                box.addView(small("HOW TO GET THERE FROM STANDARD"))
                fromStd.forEach { box.addView(bullet(it)) }
            }
            box.addView(small("RELATIVE TUNING BY EAR"))
            TuningGuide.relative(t).forEach { box.addView(bullet(it)) }
        }

        box.setOnClickListener {
            prefs.tuning = t
            render()
        }
        return box
    }

    private fun sweetCard(s: Sweetening, t: Tuning, selected: Boolean): View {
        val expanded = s.id in expandedSweet
        val box = cardBox(selected)
        box.addView(titleRow(s.name, selected))

        val offsets = s.offsets(t)
        val compact = offsets.joinToString("  ") { if (it == 0.0) "0" else Music.formatCents(it) }
        val reason = s.notApplicableReason(t)
        box.addView(label(
            if (reason == null) "6→1:  $compact ¢" else "no effect on ${t.name}",
            13f, if (selected) accent else dim, mono,
        ).apply { setPadding(0, dp(2), 0, dp(4)) })
        box.addView(label(s.summary, 13.5f, dim))
        box.addView(toggle(expanded) {
            if (!expandedSweet.add(s.id)) expandedSweet.remove(s.id)
            render()
        })

        if (expanded) {
            box.addView(paragraph(s.details, fg))
            box.addView(small("HOW TO TUNE"))
            box.addView(paragraph(s.howTo, fg))
            if (reason != null) {
                box.addView(paragraph(reason, accent))
            } else {
                box.addView(small("TARGETS FOR ${t.name.uppercase(Locale.US)} (A4 = ${prefs.a4} Hz)"))
                val targets = StringMatcher.targets(t, offsets, prefs.a4.toDouble())
                for (i in t.midi.indices) {
                    val off = if (offsets[i] == 0.0) "  0.0" else Music.formatCents(offsets[i]).padStart(5)
                    box.addView(label(
                        String.format(Locale.US, "String %d  %-4s %s ¢  %8.2f Hz", t.stringNumber(i), t.fullName(i), off, targets[i]),
                        13f, fg, mono,
                    ).apply { setPadding(dp(4), dp(1), 0, dp(1)) })
                }
            }
            box.addView(small("SOURCE"))
            box.addView(paragraph(s.source, dim))
        }

        box.setOnClickListener {
            prefs.sweetening = s
            render()
        }
        return box
    }

    private fun a4Row(): View {
        val row = cardBox(false).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun button(textValue: String, action: () -> Unit) = label(textValue, 22f, accent, medium).apply {
            gravity = Gravity.CENTER
            minWidth = dp(52); minHeight = dp(44)
            background = GradientDrawable().apply { setCornerRadius(dp(10).toFloat()); setStroke(dp(1), faint) }
            setOnClickListener { action() }
        }
        row.addView(button("−") { prefs.a4 = prefs.a4 - 1; render() })
        row.addView(label("A4 = ${prefs.a4} Hz", 18f, fg, mono).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        row.addView(button("+") { prefs.a4 = prefs.a4 + 1; render() })
        row.addView(label("440", 14f, dim, medium).apply {
            setPadding(dp(14), dp(10), dp(4), dp(10))
            setOnClickListener { prefs.a4 = 440; render() }
            contentDescription = "Reset to 440 Hz"
        })
        return row
    }

    // ------------------------------------------------------------------ small view helpers

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun label(s: String, sizeSp: Float, color: Int, face: Typeface? = null) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        if (face != null) typeface = face
    }

    private fun paragraph(s: String, color: Int) = label(s, 14f, color).apply {
        setLineSpacing(0f, 1.25f)
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun bullet(s: String) = label("•  $s", 13.5f, fg).apply {
        setLineSpacing(0f, 1.2f)
        setPadding(dp(4), dp(2), 0, dp(2))
    }

    private fun small(s: String) = label(s, 11.5f, accent, medium).apply {
        letterSpacing = 0.1f
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun section(s: String) {
        content.addView(label(s, 13f, accent, medium).apply {
            letterSpacing = 0.16f
            setPadding(0, dp(24), 0, dp(6))
        })
    }

    private fun subheader(s: String) {
        content.addView(label(s, 15f, fg, medium).apply { setPadding(0, dp(12), 0, dp(6)) })
    }

    private fun titleRow(title: String, selected: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label(title, 16.5f, fg, medium).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        if (selected) addView(label("✓", 18f, accent, medium))
    }

    private fun toggle(expanded: Boolean, onClick: () -> Unit) =
        label(if (expanded) "Hide details ▴" else "How it works & how to tune ▾", 13f, accent, medium).apply {
            setPadding(0, dp(10), dp(24), dp(2))
            setOnClickListener { onClick() }
        }

    private fun cardBox(selected: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            setCornerRadius(dp(14).toFloat())
            setColor(if (selected) cardSelected else card)
            if (selected) setStroke(dp(2), accent)
        }
        isClickable = true
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) }
    }
}
