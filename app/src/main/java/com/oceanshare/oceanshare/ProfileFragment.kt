package com.oceanshare.oceanshare

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.oceanshare.oceanshare.authentication.User
import kotlinx.android.synthetic.main.dialog_not_implemented.view.*
import kotlinx.android.synthetic.main.fragment_profile.*
import kotlinx.android.synthetic.main.fragment_profile.view.*

class ProfileFragment : Fragment() {
    private var mDatabase: DatabaseReference = FirebaseDatabase.getInstance().reference
    private val currentUser = FirebaseAuth.getInstance().currentUser?.uid.toString()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_profile, container, false)

        setupProfilePage(rootView)
        getActualUserData()

        return rootView
    }

    @SuppressLint("InflateParams")
    private fun setupProfilePage(view: View) {
        val userListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val user = dataSnapshot.getValue(User::class.java)
                if (user?.name != null) {
                    username_text_view.text = String.format(resources.getString(R.string.hello_profile), user.name)
                }
                if (user?.ship_name != null) {
                    ship_name_text_view.text = user.ship_name
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

        view.subscriptionButton.setOnClickListener {
            val subscriptionIntent = Intent(activity, StripeActivity::class.java)
            startActivity(subscriptionIntent)
        }

        view.settings_button.setOnClickListener {
            val settingsIntent = Intent(activity, PreferencesActivity::class.java)
            startActivity(settingsIntent)
        }

        view.add_media_button.setOnClickListener {
            val mDialogView = LayoutInflater.from(context).inflate(R.layout.dialog_not_implemented, null)
            val mBuilder = context?.let { it1 ->
                AlertDialog.Builder(it1, R.style.DialogTheme)
                        .setView(mDialogView)
            }
            val mAlertDialog = mBuilder?.show()
            mDialogView.dialogCancelBtn.setOnClickListener {
                mAlertDialog?.dismiss()
            }

            mDialogView.dialogLearnMoreButton.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("https://sagotg.github.io/OceanShare/")
                startActivity(intent)
            }
        }

        view.edit_infos_button.setOnClickListener {
            val settingsIntent = Intent(activity, SettingsActivity::class.java)
            startActivity(settingsIntent)
        }

    }

    private fun getActualUserData() {
        mDatabase.child("users").child(currentUser).addChildEventListener(
                object : ChildEventListener {
                    override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                        println(p0.child("preferences").value)
                        if (p0.child("type").exists()) {
                            subscriptionButton.visibility = View.GONE
                            already_subcribe.visibility = View.VISIBLE
                        }
                    }

                    override fun onChildChanged(p0: DataSnapshot, s: String?) {
                        if (p0.child("type").exists()) {
                            subscriptionButton.visibility = View.GONE
                            already_subcribe.visibility = View.VISIBLE
                        }
                    }
                    override fun onChildRemoved(p0: DataSnapshot) {}
                    override fun onChildMoved(p0: DataSnapshot, s: String?) {}
                    override fun onCancelled(p0: DatabaseError) {}

                })
    }

}
