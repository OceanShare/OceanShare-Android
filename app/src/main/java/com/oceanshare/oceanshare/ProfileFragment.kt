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
import com.facebook.AccessToken
import com.facebook.login.LoginManager
import kotlinx.android.synthetic.main.fragment_profile.view.*
import com.facebook.GraphRequest
import com.facebook.HttpMethod
import kotlinx.android.synthetic.main.fragment_profile.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.oceanshare.oceanshare.authentication.AuthenticationActivity
import com.oceanshare.oceanshare.authentication.GoogleAuthentication
import com.oceanshare.oceanshare.authentication.User
import kotlinx.android.synthetic.main.dialog_not_implemented.view.*

class ProfileFragment : Fragment() {

    private var fbAuth = FirebaseAuth.getInstance()
    private var mDatabase: DatabaseReference = FirebaseDatabase.getInstance().reference

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_profile, container, false)

        setupProfilePage(rootView)

        return rootView
    }

    @SuppressLint("InflateParams")
    private fun setupProfilePage(view: View) {
        val userListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val user = dataSnapshot.getValue(User::class.java)
                if (user?.name != null) {
                    username_text_view.text = user.name
                }
                if (user?.shipName != null) {
                    ship_name_text_view.text = user.shipName
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

        view.logout_button.setOnClickListener {
            logout()
        }

        view.settings_button.setOnClickListener {
            val mDialogView = LayoutInflater.from(context).inflate(R.layout.dialog_not_implemented, null)
            val mBuilder = AlertDialog.Builder(context!!, R.style.DialogTheme)
                    .setView(mDialogView)
            val  mAlertDialog = mBuilder.show()
            mDialogView.dialogCancelBtn.setOnClickListener {
                mAlertDialog.dismiss()
            }
        }

        view.add_media_button.setOnClickListener {
            val mDialogView = LayoutInflater.from(context).inflate(R.layout.dialog_not_implemented, null)
            val mBuilder = AlertDialog.Builder(context!!, R.style.DialogTheme)
                    .setView(mDialogView)
            val  mAlertDialog = mBuilder.show()
            mDialogView.dialogCancelBtn.setOnClickListener {
                mAlertDialog.dismiss()
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

    private fun logout() {
        logout_button.startAnimation()
        val account = GoogleSignIn.getLastSignedInAccount(activity)
        fbAuth.signOut()

        when {
            AccessToken.getCurrentAccessToken() != null -> logoutFromFacebook()
            account != null -> logoutFromGoogle()
            else -> redirectToConnection()
        }
    }

    private fun logoutFromGoogle() {
        GoogleAuthentication.logout()
        redirectToConnection()
    }

    private fun logoutFromFacebook() {
        GraphRequest(AccessToken.getCurrentAccessToken(), "/me/permissions/", null, HttpMethod.DELETE, GraphRequest.Callback {
            LoginManager.getInstance().logOut()
            AccessToken.setCurrentAccessToken(null)
            redirectToConnection()
        }).executeAsync()
    }

    private fun redirectToConnection() {
        val authenticationIntent = Intent(activity, AuthenticationActivity::class.java)
        startActivity(authenticationIntent)
        logout_button.dispose()
        activity?.finish()
    }
}
