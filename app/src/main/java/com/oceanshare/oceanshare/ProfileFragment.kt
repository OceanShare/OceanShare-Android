package com.oceanshare.oceanshare

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
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
import com.oceanshare.oceanshare.authentication.AuthenticationActivity
import com.oceanshare.oceanshare.authentication.GoogleAuthentication

class ProfileFragment : Fragment() {

    private var fbAuth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_profile, container, false)

        setupProfilePage(rootView)

        return rootView
    }

    private fun setupProfilePage(view: View) {
        view.logout_button.setOnClickListener {
            logout()
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
