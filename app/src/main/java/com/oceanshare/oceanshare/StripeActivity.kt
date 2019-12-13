package com.oceanshare.oceanshare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.stripe.android.ApiResultCallback
import com.stripe.android.PaymentConfiguration
import com.stripe.android.Stripe
import com.stripe.android.model.Token
import com.stripe.android.view.CardInputWidget
import kotlinx.android.synthetic.main.activity_stripe.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class SubscribeUser {
    var type : Int? = null
    var start : Long? = null
    var end : Long? = null
}

class StripeActivity : AppCompatActivity() {

    private val backendUrl = "https://us-central1-oceanshare-1519985626980.cloudfunctions.net/"
    private var price  = 0
    private var paymentType  = 0
    private val httpClient = OkHttpClient()
    private lateinit var stripe: Stripe
    private val subscribeUser = SubscribeUser()
    private val fbAuth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val currentUser = fbAuth.currentUser?.uid.toString()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stripe)
        getActualUserData()
        manageViews()
        payment()
    }

    private fun manageViews() {
        println("paymenType: " + paymentType.toString())
        if (paymentType != 0)
            successedPayment()
        else
            paymentPage()
    }

    private fun paymentPage() {
        one_day_offer.setOnClickListener {
            offer_page.visibility = View.GONE
            payment_page.visibility = View.VISIBLE
            custom_duration_message.text = getString(R.string.subscription_one_day_message)
            price = 2
            subscribeUser.type = 1
        }
        two_days_offer.setOnClickListener {
            offer_page.visibility = View.GONE
            payment_page.visibility = View.VISIBLE
            custom_duration_message.text = getString(R.string.subscription_two_days_message)
            price = 3
            subscribeUser.type = 2
        }
        one_month_offer.setOnClickListener {
            offer_page.visibility = View.GONE
            payment_page.visibility = View.VISIBLE
            custom_duration_message.text = getString(R.string.subscription_one_month_message)
            price = 10
            subscribeUser.type = 3
        }
    }

    private fun successedPayment() {

        runOnUiThread {
            offer_page.visibility = View.GONE
            payment_page.visibility = View.GONE
            payment_confirmation_message.visibility = View.VISIBLE

            if (subscribeUser.type != null) {
                val inputMethodManager = this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(View(this).windowToken, 0)
                cardInputWidget.clear()
                subscribeUser.start = System.currentTimeMillis() / 1000
                subscribeUser.end = System.currentTimeMillis() / 1000 + 2592000 //TODO Change to real month
                database.child("users").child(currentUser).child("sub").setValue(subscribeUser)

                Handler().postDelayed({
                    val mainActivityIntent = Intent(this, MainActivity::class.java)
                    startActivity(mainActivityIntent)
                }, 5000)

            }
        }
    }

    private fun getActualUserData() {
        database.child("users").child(currentUser).addChildEventListener(
                object : ChildEventListener {
                    override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                        println(p0.child("preferences").value)
                        if (p0.child("type").exists()) {
                            paymentType = p0.child("type").value.toString().toInt()
                        }
                    }

                    override fun onChildChanged(p0: DataSnapshot, s: String?) {
                        paymentType = p0.child("type").value.toString().toInt()
                    }
                    override fun onChildRemoved(p0: DataSnapshot) {}
                    override fun onChildMoved(p0: DataSnapshot, s: String?) {}
                    override fun onCancelled(p0: DatabaseError) {}

                })
    }

    private fun payment() {
        // Configure the SDK with your Stripe publishable key so that it can make requests to the Stripe API
        // ⚠️ Don't forget to switch this to your live-mode publishable key before publishing your app
        PaymentConfiguration.init("pk_test_aKG5XmyrMWd17loRBt4W45Vd00nDvn7UF1") // Get your key here: https://stripe.com/docs/keys#obtain-api-keys

        // Hook up the pay button to the card widget and stripe instance
        val payButton: Button = findViewById(R.id.payButton)
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
                                        println("Failed to decode response from server")
                                    }

                                    override fun onResponse(call: Call, response: Response) {
                                        if (!response.isSuccessful) {
                                            println("Failed to decode response from server")
                                        } else {
                                            val responseData = response.body?.string()
                                            var responseJSON = JSONObject(responseData)
                                            val error = responseJSON.optString("error", null)
                                            if (error != null) {
                                                println("Payment failed")
                                            } else {
                                                println("Payment succeeded!")
                                                successedPayment()
                                            }
                                        }
                                    }
                                })
                    }

                    override fun onError(e: java.lang.Exception) {
                        println("Error" + e.localizedMessage)
                    }
                })
            }

        }
    }
}