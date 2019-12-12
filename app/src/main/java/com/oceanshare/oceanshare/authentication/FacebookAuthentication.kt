package com.oceanshare.oceanshare.authentication

import android.app.Activity
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.login.LoginManager

class FacebookAuthentication {

    companion object {
        var callbackManager: CallbackManager? = null

        fun isConnected(activity: Activity): Boolean {
            val accessToken = AccessToken.getCurrentAccessToken()

            if (accessToken != null && !accessToken.isExpired) {
                LoginManager.getInstance().logInWithReadPermissions(activity, listOf("public_profile"))
                return true
            }
            return false
        }
    }
}