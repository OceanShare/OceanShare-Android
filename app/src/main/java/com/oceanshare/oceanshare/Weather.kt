package com.oceanshare.oceanshare

import android.graphics.drawable.GradientDrawable
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class Weather {

    fun receiveWeatherData (lat : String, long : String){
        var client : OkHttpClient = OkHttpClient()
        var url = "https://oceanshare.cleverapps.io/api/weather?lat=2&lng=4"

        var request = Request.Builder()
                .url(url)
                .addHeader("Authorization",  "Bearer " + "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJVc2VySWQiOjF9.Vcp2grZ53t_OG3jwSXsRwfc_UUjboNgZarkAGiX0jgM")
                .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string().toString()
                try {
                    var json = JSONObject(responseData)
                    println("Request Successful!!")
                    println("toto" + json)
                } catch (e : JSONException) {
                    e.printStackTrace()
                }
            }
        })

    }

    private fun getTemprature(temp: Int) : Int {return ((temp - 32) / 1.8).toInt()}

    private fun getSunriseTime(timestamp: Int) : String {
        val sdf = SimpleDateFormat("HH:mm")
        val date = Date((timestamp *1000.00).toLong())
        return sdf.format(date)
    }

    private fun getSunsetTime(timestamp: Int) : String {
        val sdf = SimpleDateFormat("HH:mm")
        val date = Date((timestamp *1000.00).toLong())
        return sdf.format(date)
    }

    private fun getWindDirection(direction : Int) : String {
        lateinit var orientation : String
        if (direction in 23..68) {
            orientation = "NE"
        } else if (direction in 69..112) {
            orientation = "E"
        } else if (direction in 113..158) {
            orientation = "SE"
        } else if (direction in 159..202) {
            orientation = "S"
        } else if (direction in 203..248) {
            orientation = "SO"
        } else if (direction in 249..292) {
            orientation = "O"
        } else if (direction in 293..338) {
            orientation = "NO"
        }  else {
            orientation = "N"
        }
        return orientation
    }
}