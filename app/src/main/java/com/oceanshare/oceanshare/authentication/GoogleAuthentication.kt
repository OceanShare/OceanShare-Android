package com.oceanshare.oceanshare.authentication

import android.app.Activity
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

class GoogleAuthentication {

    companion object {
        const val RC_SIGN_IN = 1
        var mGoogleSignInClient: GoogleSignInClient? = null

        fun isConnected(context: Context): Boolean {
            return GoogleSignIn.getLastSignedInAccount(context) != null
        }

        fun instantiateGoogleSignInClient(activity: Activity) {
            val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .build()
            mGoogleSignInClient = GoogleSignIn.getClient(activity, googleSignInOptions)
        }

        fun logout() {
            val googleSignInClient = mGoogleSignInClient
            googleSignInClient?.signOut()
        }
    }
}