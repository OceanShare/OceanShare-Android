package com.oceanshare.oceanshare.authentication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.Auth
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.oceanshare.oceanshare.R
import com.oceanshare.oceanshare.utils.hideKeyboard
import kotlinx.android.synthetic.main.fragment_register.*
import kotlinx.android.synthetic.main.fragment_register.view.*

class RegisterFragment : Fragment() {
    private var listener: OnFragmentInteractionListener? = null

    private var fbAuth = FirebaseAuth.getInstance()
    private var mCallback: Callback? = null

    interface Callback {
        fun showLoginPage()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        // Inflate the layout for this fragment
        val rootView = inflater.inflate(R.layout.fragment_register, container, false)

        // Set up the register form
        setupRegistrationForm(rootView)
        setupGoogleConnection(rootView)
        setupFacebookConnection(rootView)
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
                Toast.makeText(activity, "Google ça marche fdp", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, R.string.error_auth_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupFacebookConnection(rootView: View) {
        FacebookAuthentication.callbackManager = CallbackManager.Factory.create()
        rootView.facebook_login_button.setReadPermissions("email", "public_profile", "user_friends")
        rootView.facebook_login_button.fragment = this

        rootView.facebook_login_button.registerCallback(FacebookAuthentication.callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(loginResult: LoginResult) {
                Toast.makeText(activity, "Facebook ça marche fdp", Toast.LENGTH_SHORT).show()
            }

            override fun onCancel() {}
            override fun onError(exception: FacebookException) {}
        })
    }

    private fun setupRegistrationForm(rootView: View) {
        rootView.password_confirmation.setOnEditorActionListener(TextView.OnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                attemptRegister()
                return@OnEditorActionListener true
            }
            false
        })

        rootView.email_register_button.setOnClickListener {
            attemptRegister()
        }

        rootView.true_facebook_login_button.setOnClickListener {
            rootView.facebook_login_button.performClick()
        }

        // rootView.name.background.alpha = 80
        rootView.email.background.alpha = 80
        rootView.password.background.alpha = 80
        rootView.password_confirmation.background.alpha = 80

        rootView.register_form.isVerticalScrollBarEnabled = false

        rootView.password_til.isPasswordVisibilityToggleEnabled = false
        rootView.password_confirmation_til.isPasswordVisibilityToggleEnabled = false
        rootView.password.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {}
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                rootView.password_til.isPasswordVisibilityToggleEnabled = rootView.password.text.isNotEmpty()
            }
        })
        rootView.password_confirmation.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {}
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                rootView.password_confirmation_til.isPasswordVisibilityToggleEnabled = rootView.password_confirmation.text.isNotEmpty()
            }
        })
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    private fun attemptRegister() {
        view?.hideKeyboard()

        resetFieldsErrors()
        // Store values at the time of the register attempt.
        val emailStr = email.text.toString()
        val passwordStr = password.text.toString()
        val passwordConfirmationStr = password_confirmation.text.toString()

        var cancel = false
        var focusView: View? = null

        // Check for a valid email address.
        AuthenticationHelper.isEmailValid(context, emailStr)?.also { error ->
            if (!cancel) {
                focusView = email
            }
            email_til.error = error
            cancel = true
        }

        // Check for a valid password.
        AuthenticationHelper.isPasswordValid(context, passwordStr)?.also { error ->
            if (!cancel) {
                focusView = password
            }
            password_til.error = error
            cancel = true
        }

        // Check for a valid password confirmation.
        AuthenticationHelper.isPasswordConfirmationValid(context, passwordStr, passwordConfirmationStr)?.also { error ->
            if (!cancel) {
                focusView = password_confirmation
            }
            password_confirmation_til.error = error
            cancel = true
        }

        if (cancel) {
            email_register_button.revertAnimation()
            focusView?.requestFocus()
        } else {
            email_register_button.startAnimation()

            fbAuth.createUserWithEmailAndPassword(emailStr, passwordStr).addOnCompleteListener(activity as Activity) { task ->
                if (task.isSuccessful) {
                    val user = fbAuth?.currentUser
                    user?.sendEmailVerification()?.addOnCompleteListener(activity as Activity) { mailTask ->
                        if (mailTask.isSuccessful) {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            val database = FirebaseDatabase.getInstance().reference
                            if (uid != null) {
                                database.child("users").child(uid).child("preferences").child("boatId").setValue(0)
                                database.child("users").child(uid).child("preferences").child("ghost_mode").setValue(false)
                                database.child("users").child(uid).child("preferences").child("show_picture").setValue(false)
                                database.child("users").child(uid).child("preferences").child("user_active").setValue(false)
                            }
                            Toast.makeText(context, R.string.info_mail_confirm, Toast.LENGTH_LONG).show()
                            email_register_button.dispose()
                            fbAuth.signOut()
                            mCallback?.showLoginPage()
                        } else {
                            Toast.makeText(context, task.exception?.message, Toast.LENGTH_LONG).show()
                        }
                        email_register_button.revertAnimation()
                    }
                } else {
                    Toast.makeText(context, task.exception?.message, Toast.LENGTH_LONG).show()
                    email_register_button.revertAnimation()
                }
            }
        }
    }

    private fun resetFieldsErrors() {
        // name_til.error = null
        email_til.error = null
        password_til.error = null
        password_confirmation_til.error = null
    }

    interface OnFragmentInteractionListener {
        fun onFragmentInteraction(uri: Uri)
    }

    companion object {
        @JvmStatic
        fun newInstance() =
                RegisterFragment().apply {}
    }
}
