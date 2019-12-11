package com.oceanshare.oceanshare

import android.support.v7.app.AppCompatActivity
import android.os.Bundle

class PreferencesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)
        supportActionBar?.hide()
    }
}
