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
import com.google.firebase.auth.FirebaseAuth
import com.oceanshare.oceanshare.MainActivity
import com.oceanshare.oceanshare.R
import com.oceanshare.oceanshare.utils.hideKeyboard
import kotlinx.android.synthetic.main.fragment_register.*
import kotlinx.android.synthetic.main.fragment_register.view.*

class RegisterFragment : Fragment() {
    private var listener: OnFragmentInteractionListener? = null

    private var fbAuth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        // Inflate the layout for this fragment
        val rootView = inflater.inflate(R.layout.fragment_register, container, false)

        // Set up the register form
        setupRegistrationForm(rootView)

        return rootView
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
                rootView.password_til.isPasswordVisibilityToggleEnabled = !rootView.password.text.isEmpty()
                rootView.password_confirmation_til.isPasswordVisibilityToggleEnabled = !rootView.password.text.isEmpty()
            }
        })
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

    private fun attemptRegister() {
        view?.hideKeyboard()

        resetFieldsErrors()
        // Store values at the time of the register attempt.
        // val nameStr = name.text.toString()
        val emailStr = email.text.toString()
        val passwordStr = password.text.toString()
        val passwordConfirmationStr = password_confirmation.text.toString()

        var cancel = false
        var focusView: View? = null

        // Check for valid names
        /*if (nameStr.isEmpty()) {
            name_til.error = getString(R.string.error_field_required)
            if (!cancel) { focusView = name }
            cancel = true
        }*/

        // Check for a valid email address.
        AuthenticationHelper.isEmailValid(context, emailStr)?.also { error ->
            if (!cancel) { focusView = email }
            email_til.error = error
            cancel = true
        }

        // Check for a valid password.
        AuthenticationHelper.isPasswordValid(context, passwordStr)?.also { error ->
            if (!cancel) { focusView = password }
            password_til.error = error
            cancel = true
        }

        // Check for a valid password confirmation.
        AuthenticationHelper.isPasswordConfirmationValid(context, passwordStr, passwordConfirmationStr)?.also { error ->
            if (!cancel) { focusView = password_confirmation }
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
                    connectUserAndRedirectToHomePage()
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

    private fun connectUserAndRedirectToHomePage() {
        email_register_button.dispose()
        val mainActivityIntent = Intent(activity, MainActivity::class.java)
        startActivity(mainActivityIntent)
        activity?.finish()
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
