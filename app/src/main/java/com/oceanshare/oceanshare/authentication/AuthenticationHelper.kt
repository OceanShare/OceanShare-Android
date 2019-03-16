package com.oceanshare.oceanshare.authentication

import android.content.Context
import android.text.TextUtils
import com.oceanshare.oceanshare.R
import java.util.regex.Pattern

class AuthenticationHelper {
    companion object {
        fun isEmailValid(context: Context?, emailStr: String): String? {

            if (emailStr.isEmpty()) { return context?.getString(R.string.error_field_required) }

            if (!Pattern.compile("^(([\\w-]+\\.)+[\\w-]+|([a-zA-Z]|[\\w-]{2,}))@"
                            + "((([0-1]?[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\.([0-1]?"
                            + "[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\."
                            + "([0-1]?[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\.([0-1]?"
                            + "[0-9]{1,2}|25[0-5]|2[0-4][0-9]))|"
                            + "([a-zA-Z]+[\\w-]+\\.)+[a-zA-Z]{2,4})$"
                    ).matcher(emailStr).matches()) {
                return context?.getString(R.string.error_email_invalid)
            }

            return null
        }

        fun isPasswordValid(context: Context?, passwordStr: String): String? {

            if (TextUtils.isEmpty(passwordStr)) { return context?.getString(R.string.error_field_required) }
            if (passwordStr.length < 10) { return context?.getString(R.string.error_password_too_short) }

            return null
        }

        fun isPasswordConfirmationValid(context: Context?, passwordStr: String, passwordConfirmationStr: String): String? {

            if (TextUtils.isEmpty(passwordConfirmationStr)) { return context?.getString(R.string.error_field_required) }
            if (passwordConfirmationStr != passwordStr) { return context?.getString(R.string.error_password_confirmation_different) }

            return null
        }
    }
}