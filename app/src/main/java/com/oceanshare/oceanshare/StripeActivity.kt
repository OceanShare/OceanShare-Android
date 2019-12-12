package com.oceanshare.oceanshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import com.google.gson.GsonBuilder
import com.stripe.android.ApiResultCallback
import com.stripe.android.PaymentConfiguration
import com.stripe.android.PaymentIntentResult
import com.stripe.android.Stripe
import com.stripe.android.model.ConfirmPaymentIntentParams
import com.stripe.android.model.StripeIntent
import com.stripe.android.model.Token
import com.stripe.android.view.CardInputWidget
import kotlinx.android.synthetic.main.activity_stripe.*
import java.lang.ref.WeakReference
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException


class StripeActivity : AppCompatActivity() {

    private val backendUrl = "https://us-central1-oceanshare-1519985626980.cloudfunctions.net/"
    private val httpClient = OkHttpClient()
    private lateinit var stripe: Stripe

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stripe)

        // Configure the SDK with your Stripe publishable key so that it can make requests to the Stripe API
        // ⚠️ Don't forget to switch this to your live-mode publishable key before publishing your app
        PaymentConfiguration.init("pk_test_aKG5XmyrMWd17loRBt4W45Vd00nDvn7UF1") // Get your key here: https://stripe.com/docs/keys#obtain-api-keys

        // Hook up the pay button to the card widget and stripe instance
        val payButton: Button = findViewById(R.id.payButton)
        val weakActivity = WeakReference<Activity>(this)
        payButton.setOnClickListener {
            // Get the card details from the card widget
            val cardInputWidget =
                    findViewById<CardInputWidget>(R.id.cardInputWidget)
            cardInputWidget.card?.let { card ->
                // Create a Stripe Token from the card details
                stripe = Stripe(applicationContext, PaymentConfiguration.getInstance().publishableKey)
                stripe.createToken(card, object: ApiResultCallback<Token> {
                    override fun onSuccess(result: Token) {
                        // Send the Token identifier to the server
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val json = """
                            {
                                "token": "${result.id}",
                                "amount":3555,
                                "currency":"EUR"
                            }
                            """
                        val body = json.toRequestBody(mediaType)
                        val request = Request.Builder()
                                .url(backendUrl + "charge/")
                                .post(body)
                                .build()
                        httpClient.newCall(request)
                                .enqueue(object: Callback {
                                    override fun onFailure(call: Call, e: IOException) {
                                        displayAlert(weakActivity.get(), "Failed to decode response from server", "Error: $e")
                                    }

                                    override fun onResponse(call: Call, response: Response) {
                                        if (!response.isSuccessful) {
                                            displayAlert(weakActivity.get(), "Failed to decode response from server", "Error: $response")
                                        } else {
                                            val responseData = response.body?.string()
                                            var responseJSON = JSONObject(responseData)
                                            val error = responseJSON.optString("error", null)
                                            if (error != null) {
                                                displayAlert(weakActivity.get(), "Payment failed", error)
                                            } else {
                                                displayAlert(weakActivity.get(), "Success", "Payment succeeded!", true)
                                            }
                                        }
                                    }
                                })
                    }

                    override fun onError(e: java.lang.Exception) {
                        displayAlert(weakActivity.get(), "Error", e.localizedMessage)
                    }
                })
            }

        }
    }

    private fun displayAlert(activity: Activity?, title: String, message: String, restartDemo: Boolean = false) {
        if (activity == null) {
            return
        }
        runOnUiThread {
            val builder = AlertDialog.Builder(activity)
            builder.setTitle(title)
            builder.setMessage(message)
            if (restartDemo) {
                builder.setPositiveButton("Restart demo") { _, _ ->
                    val cardInputWidget =
                            findViewById<CardInputWidget>(R.id.cardInputWidget)
                    cardInputWidget.clear()
                }
            }
            else {
                builder.setPositiveButton("Ok", null)
            }
            val dialog = builder.create()
            dialog.show()
        }
    }

}