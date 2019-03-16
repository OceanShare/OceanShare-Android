package com.oceanshare.oceanshare.authentication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import com.facebook.CallbackManager
import com.oceanshare.oceanshare.MainActivity
import com.oceanshare.oceanshare.R
import com.oceanshare.oceanshare.utils.hideKeyboard
import kotlinx.android.synthetic.main.fragment_login.*
import kotlinx.android.synthetic.main.fragment_login.view.*
import com.facebook.FacebookException
import com.facebook.login.LoginResult
import com.facebook.FacebookCallback
import com.google.android.gms.auth.api.Auth
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {
    private var listener: OnFragmentInteractionListener? = null

    private var mCallback: Callback? = null

    private var fbAuth = FirebaseAuth.getInstance()

    interface Callback {
        fun showRegistrationPage()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_login, container, false)

        // Setup the login form
        setupLoginForm(rootView)
        setupFacebookConnection(rootView)
        setupGoogleConnection(rootView)

        mCallback = activity as Callback?

        return rootView
    }

    private fun setupGoogleConnection(rootView: View) {
        rootView.google_login_button.setOnClickListener {
            val signInIntent = GoogleAuthentication.mGoogleSignInClient?.signInIntent
            startActivityForResult(signInIntent, GoogleAuthentication.RC_SIGN_IN)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        FacebookAuthentication.callbackManager?.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GoogleAuthentication.RC_SIGN_IN) {
            val result = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
            if (result.isSuccess) {
                connectUserAndRedirectToHomePage()
            } else {
                Toast.makeText(activity, "Authentication failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupFacebookConnection(rootView: View) {
        FacebookAuthentication.callbackManager = CallbackManager.Factory.create()
        rootView.facebook_login_button.setReadPermissions("email", "public_profile", "user_friends")
        rootView.facebook_login_button.fragment = this

        rootView.facebook_login_button.registerCallback(FacebookAuthentication.callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(loginResult: LoginResult) {
                connectUserAndRedirectToHomePage()
            }

            override fun onCancel() {}
            override fun onError(exception: FacebookException) {}
        })
    }

    fun connectUserAndRedirectToHomePage() {
        email_login_button.dispose()
        val mainActivityIntent = Intent(activity, MainActivity::class.java)
        startActivity(mainActivityIntent)
        activity?.finish()
    }

    private fun setupLoginForm(rootView: View) {
        rootView.password.setOnEditorActionListener(TextView.OnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                attemptLogin()
                return@OnEditorActionListener true
            }
            false
        })

        rootView.email_login_button.setOnClickListener {
            attemptLogin()
        }

        rootView.true_facebook_login_button.setOnClickListener {
            rootView.facebook_login_button.performClick()
        }

        rootView.password.background.alpha = 80
        rootView.email.background.alpha = 80

        rootView.login_form.isVerticalScrollBarEnabled = false

        rootView.swap_to_register_button.setOnClickListener {
            mCallback?.showRegistrationPage()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException(context.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    private fun attemptLogin() {
        view?.hideKeyboard()

        resetFieldsErrors()
        // Store values at the time of the login attempt.
        val emailStr = email.text.toString()
        val passwordStr = password.text.toString()

        var cancel = false
        var focusView: View? = null

        // Check for a valid email address.
        AuthenticationHelper.isEmailValid(context, emailStr)?.also { error ->
            if (!cancel) { focusView = email }
            email_til.error = error
            cancel = true
        }

        // Check for a valid password, if the user entered one.
        AuthenticationHelper.isPasswordValid(context, passwordStr)?.also { error ->
            if (!cancel) { focusView = password }
            password_til.error = error
            cancel = true
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first field with an error.
            focusView?.requestFocus()
        } else {
            email_login_button.startAnimation()

            fbAuth.signInWithEmailAndPassword(emailStr, passwordStr).addOnCompleteListener(activity as Activity) { task ->
                if (task.isSuccessful) {
                    connectUserAndRedirectToHomePage()
                } else {
                    Toast.makeText(context, task.exception?.message, Toast.LENGTH_LONG).show()
                    email_login_button.revertAnimation()
                }
            }
        }

    }

    private fun resetFieldsErrors() {
        email_til.error = null
        password_til.error = null
    }

    interface OnFragmentInteractionListener {
        fun onFragmentInteraction(uri: Uri)
    }

    companion object {
        @JvmStatic
        fun newInstance() = LoginFragment().apply {}
    }
}
