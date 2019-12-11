package com.oceanshare.oceanshare

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.oceanshare.oceanshare.authentication.User
import kotlinx.android.synthetic.main.activity_preferences.*

class PreferencesActivity : AppCompatActivity() {

    private var database: DatabaseReference = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)
        supportActionBar?.hide()

        setupPreferences()

        backButton.setOnClickListener { this.finish() }
        smallBoat.setOnClickListener {
            setSelectedBoat(0)
        }
        mediumBoat.setOnClickListener {
            setSelectedBoat(1)
        }
        largeBoat.setOnClickListener {
            setSelectedBoat(2)
        }
        largestBoat.setOnClickListener {
            setSelectedBoat(3)
        }

        showPictureSwitch.setOnCheckedChangeListener { switch, _ ->
            saveShowPictureValue(switch.isChecked)
        }

        ghostModeSwitch.setOnCheckedChangeListener { switch, _ ->
            saveGhostModeValue(switch.isChecked)
        }

        temperatureSegmentedControl.addOnSegmentClickListener {
            // TODO: Save this in local storage
            // TODO: Create preferences in Firebase at account creation
        }
    }

    private fun setupPreferences() {
        temperatureSegmentedControl.setSelectedSegment(0)

        val userListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val user = dataSnapshot.getValue(User::class.java)
                val preferences = user?.preferences
                if (preferences?.show_picture != null) {
                    showPictureSwitch.isChecked = preferences.show_picture
                } else {
                    showPictureSwitch.isChecked = false
                }
                if (preferences?.ghost_mode != null) {
                    ghostModeSwitch.isChecked = preferences.ghost_mode
                } else {
                    ghostModeSwitch.isChecked = false
                }
                if (preferences?.boatId != null) {
                    setSelectedBoat(preferences.boatId)
                } else {
                    setSelectedBoat(0)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                println("loadPost:onCancelled ${databaseError.toException()}")
            }
        }
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid
        if (uid != null) {
            database.child("users").child(uid).addListenerForSingleValueEvent(userListener)
        }
    }

    private fun setSelectedBoat(selectedBoatId: Int) {
        smallBoatBackground.setCardBackgroundColor(resources.getColor(R.color.grey, theme))
        mediumBoatBackground.setCardBackgroundColor(resources.getColor(R.color.grey, theme))
        largeBoatBackground.setCardBackgroundColor(resources.getColor(R.color.grey, theme))
        largestBoatBackground.setCardBackgroundColor(resources.getColor(R.color.grey, theme))
        when (selectedBoatId) {
            0 -> {
                smallBoatBackground.setCardBackgroundColor(resources.getColor(R.color.white, theme))
            }
            1 -> {
                mediumBoatBackground.setCardBackgroundColor(resources.getColor(R.color.white, theme))
            }
            2 -> {
                largeBoatBackground.setCardBackgroundColor(resources.getColor(R.color.white, theme))
            }
            3 -> {
                largestBoatBackground.setCardBackgroundColor(resources.getColor(R.color.white, theme))
            }
        }
        saveBoat(selectedBoatId)
    }

    private fun saveShowPictureValue(showPicture: Boolean) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid
        if (uid != null) {
            database.child("users").child(uid).child("preferences").child("show_picture").setValue(showPicture)
        }
    }

    private fun saveGhostModeValue(ghostMode: Boolean) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid
        if (uid != null) {
            database.child("users").child(uid).child("preferences").child("ghost_mode").setValue(ghostMode)
        }
    }

    private fun saveBoat(boatId: Int) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid
        if (uid != null) {
            database.child("users").child(uid).child("preferences").child("boatId").setValue(boatId)
        }
    }
}
