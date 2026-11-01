package app.sweettuner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import app.sweettuner.audio.AudioEngine
import app.sweettuner.core.PitchEstimate
import app.sweettuner.core.PitchSmoother
import app.sweettuner.core.StringMatcher
import app.sweettuner.core.Sweetening
import app.sweettuner.core.Tuning
import app.sweettuner.ui.TunerView
import app.sweettuner.ui.padForSystemBars

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var tuner: TunerView
    private lateinit var engine: AudioEngine
    private val smoother = PitchSmoother()

    private lateinit var tuning: Tuning
    private lateinit var sweetening: Sweetening
    private var offsets = DoubleArray(6)
    private var a4 = 440

    private var askedThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = Prefs(this)

        tuner = TunerView(this)
        tuner.padForSystemBars()
        tuner.onMenu = { startActivity(Intent(this, MenuActivity::class.java)) }
        tuner.onLockChanged = { smoother.reset() }
        tuner.onMessageTap = { onPermissionMessageTapped() }
        setContentView(tuner)

        engine = AudioEngine(this, ::onPitch)
    }

    override fun onResume() {
        super.onResume()
        tuning = prefs.tuning
        sweetening = prefs.sweetening
        a4 = prefs.a4
        offsets = sweetening.offsets(tuning)
        tuner.setConfig(tuning, sweetening, a4)
        smoother.reset()
        startIfAllowed()
    }

    override fun onPause() {
        super.onPause()
        engine.stop()
    }

    private fun onPitch(estimate: PitchEstimate?) {
        if (estimate == null) {
            smoother.reset()
            tuner.setReading(null)
            return
        }
        if (estimate.clarity < 0.80) return // too noisy / inharmonic to trust
        val hz = smoother.push(estimate.frequency)
        val reading = StringMatcher.match(hz, tuning, offsets, a4.toDouble(), tuner.lockedString)
        tuner.setReading(reading)
    }

    // ------------------------------------------------------------------ microphone permission

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startIfAllowed() {
        if (hasMic()) {
            tuner.setMessage(null)
            if (!engine.start()) tuner.setMessage("Could not open the microphone. Is another app using it?")
        } else if (!askedThisSession) {
            askedThisSession = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        } else {
            tuner.setMessage("Microphone access is needed to hear your guitar.")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_MIC) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startIfAllowed()
        else tuner.setMessage("Microphone access is needed to hear your guitar.")
    }

    private fun onPermissionMessageTapped() {
        if (hasMic()) {
            startIfAllowed(); return
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        } else {
            // Permanently denied (or never asked on this install): open the app's settings page.
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        }
    }

    private companion object { const val REQ_MIC = 1 }
}
