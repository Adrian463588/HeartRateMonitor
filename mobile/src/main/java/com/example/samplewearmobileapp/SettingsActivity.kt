package com.example.samplewearmobileapp

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat

/**
 * Hosts the [SettingsFragment] which inflates [R.xml.preferences].
 *
 * Clean Code: thin host activity — all preference logic lives in the fragment.
 * SRP: this class owns only lifecycle coordination and navigation.
 * Replaces deprecated [onBackPressed] with [OnBackPressedCallback] per AndroidX guidance.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Capture global exceptions to prevent silent crashes
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            Log.e(TAG, "Unexpected exception", throwable)
        }

        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // SRP: back-press handling delegated to the dispatcher (replaces deprecated onBackPressed).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(RESULT_OK)
                finish()
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                setResult(RESULT_OK)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        super.onPause()
        // Dismiss any pending tooltip popups to prevent WindowLeaked exceptions
        window.decorView.cancelPendingInputEvents()
    }

    /**
     * Preference fragment — inflates [R.xml.preferences].
     * SRP: only responsible for wiring the preference XML to the fragment lifecycle.
     */
    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
        }
    }

    companion object {
        private const val TAG = "Mobile.SettingsActivity"
    }
}