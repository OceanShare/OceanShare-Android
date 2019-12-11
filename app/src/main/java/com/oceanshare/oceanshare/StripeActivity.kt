package com.oceanshare.oceanshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import com.google.gson.GsonBuilder
import com.stripe.android.ApiResultCallback
import com.stripe.android.PaymentConfiguration
import com.stripe.android.PaymentIntentResult
import com.stripe.android.Stripe
import com.stripe.android.model.ConfirmPaymentIntentParams
import com.stripe.android.model.StripeIntent
import com.stripe.android.view.CardInputWidget
import kotlinx.android.synthetic.main.activity_stripe.*
import java.lang.ref.WeakReference
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException


class StripeActivity : AppCompatActivity() {

    private var context: Context? = null

    private val backendUrl = "https://us-central1-oceanshare-1519985626980.cloudfunctions.net/"
    private val publishableKey = "pk_test_aKG5XmyrMWd17loRBt4W45Vd00nDvn7UF1"
    private val httpClient = OkHttpClient()
    private lateinit var paymentIntentClientSecret: String
    private lateinit var stripe: Stripe

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = this
        PaymentConfiguration.init(publishableKey)
        startCheckout()
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
                    startCheckout()
                }
            } else {
                builder.setPositiveButton("Ok", null)
            }
            val dialog = builder.create()
            dialog.show()
        }
    }

    private fun startCheckout() {
        val weakActivity = WeakReference<Activity>(this)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val json = """
            {
                "currency":"euro",
                "items": [
                    {"id":"photo_subscription"}
                ]
            }
            """
        val body = json.toRequestBody(mediaType)
        val request = Request.Builder()
                .url(backendUrl + "create-payment-intent")
                .post(body)
                .build()
        httpClient.newCall(request)
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        displayAlert(weakActivity.get(), "Failed to load page", "Error: $e")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!response.isSuccessful) {
                            displayAlert(weakActivity.get(), "Failed to load page", "Error: $response")
                        } else {
                            val responseData = response.body?.string()
                            var json = JSONObject(responseData)

                            // The response from the server includes the Stripe publishable key and
                            // PaymentIntent details.
                            // For added security, our sample app gets the publishable key from the server
                            paymentIntentClientSecret = json.getString("clientSecret")

                            // Configure the SDK with your Stripe publishable key so that it can make requests to the Stripe API
                            stripe = Stripe(applicationContext, publishableKey)
                        }
                    }
                })

        payButton.setOnClickListener {
            val cardInputWidget =
                    findViewById<CardInputWidget>(R.id.cardInputWidget)
            val params = cardInputWidget.paymentMethodCreateParams
            if (params != null) {
                val confirmParams = ConfirmPaymentIntentParams
                        .createWithPaymentMethodCreateParams(params, paymentIntentClientSecret)
                stripe.confirmPayment(this, confirmParams)
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val weakActivity = WeakReference<Activity>(this)

        // Handle the result of stripe.confirmPayment
        stripe.onPaymentResult(requestCode, data, object : ApiResultCallback<PaymentIntentResult> {
            override fun onSuccess(result: PaymentIntentResult) {
                val paymentIntent = result.intent
                val status = paymentIntent.status
                if (status == StripeIntent.Status.Succeeded) {
                    val gson = GsonBuilder().setPrettyPrinting().create()
                    displayAlert(weakActivity.get(), "Payment succeeded", gson.toJson(paymentIntent), restartDemo = true)
                } else if (status == StripeIntent.Status.RequiresPaymentMethod) {
                    displayAlert(weakActivity.get(), "Payment failed", paymentIntent.lastPaymentError?.message ?: "")
                }
            }

            override fun onError(e: Exception) {
                displayAlert(weakActivity.get(), "Payment failed", e.toString())
            }
        })
    }

}