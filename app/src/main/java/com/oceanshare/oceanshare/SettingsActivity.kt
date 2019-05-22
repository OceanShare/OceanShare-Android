package com.oceanshare.oceanshare

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.oceanshare.oceanshare.authentication.User
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : AppCompatActivity() {

    private var mDatabase: DatabaseReference = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.hide()

        getUserInformations()

        settings_back_button.setOnClickListener {
            if (name_edit_text.text.isNotEmpty()) {
                saveUsername(mDatabase, name_edit_text.text.toString())
            }
            if (email_edit_text.text.isNotEmpty()) {
                saveEmail(mDatabase, email_edit_text.text.toString())
            }
            if (ship_name_edit_text.text.isNotEmpty()) {
                saveShipName(mDatabase, ship_name_edit_text.text.toString())
            }
            this.finish()
        }
    }

    public override fun onStart() {
        super.onStart()
    }

    private fun getUserInformations() {
        val userListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val user = dataSnapshot.getValue(User::class.java)
                if (user?.name != null) {
                    name_edit_text.setText(user.name)
                }
                if (FirebaseAuth.getInstance().currentUser?.email != null) {
                    email_edit_text.setText(FirebaseAuth.getInstance().currentUser?.email)
                }
                if (user?.shipName != null) {
                    ship_name_edit_text.setText(user.shipName)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                println("loadPost:onCancelled ${databaseError.toException()}")
            }
        }
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid
        if (uid != null) {
            mDatabase.child("users").child(uid).addListenerForSingleValueEvent(userListener)
        }
    }

    private fun saveUsername(firebaseData: DatabaseReference, username: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid
        if (uid != null) {
            firebaseData.child("users").child(uid).child("name").setValue(username)
        }
    }

    private fun saveShipName(firebaseData: DatabaseReference, shipName: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid
        if (uid != null) {
            firebaseData.child("users").child(uid).child("shipName").setValue(shipName)
        }
    }

    private fun saveEmail(firebaseData: DatabaseReference, email: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid
        if (uid != null) {
            firebaseData.child("users").child(uid).child("email").setValue(email)
        }
    }
}
