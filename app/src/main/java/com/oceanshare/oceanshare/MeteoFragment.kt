package com.oceanshare.oceanshare

import android.location.Location
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_meteo.*
import java.lang.Math.round
import java.sql.Timestamp
import java.util.*
import kotlin.math.roundToLong

class MeteoFragment : Fragment() {
    private var apiService = IWeatherApi()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_meteo, container, false)
    }

    suspend fun fetchMeteo(location: Location) {
        val meteo = apiService.getWeather(location.latitude.toString(), location.longitude.toString())
        meteoImage.setImageResource(analyseDescription(meteo.weather!!.weather!![0]))
        meteoTemp.text = (meteo.weather!!.main!!.temp!! - 273.15).toInt().toString() + " °C"
        meteoDescription.text = meteo.weather!!.weather!![0].description!!.capitalize()
        meteoLongitute.text = location.longitude.toString()
        meteoLatitude.text = location.latitude.toString()
        val sunriseTimestamp = Timestamp(meteo.weather!!.sys!!.sunrise!!)
        val sunrise = Date(sunriseTimestamp.time)
        meteoSunrise.text = "HH:MM"//sunrise.toString()
        val sunsetTimestamp = Timestamp(meteo.weather!!.sys!!.sunset!!)
        val sunset = Date(sunsetTimestamp.time)
        meteoSunset.text = "HH:MM"//sunset.minutes.toString()
        meteoRainRisk.text = meteo.weather!!.clouds!!.all.toString() + " %"
        meteoWaterTemp.text = "--"
        meteoWind.text = analyseWindDirection(meteo.weather!!.wind!!.deg!!) + " " + meteo.weather!!.wind!!.speed.toString() + "km/h"
        meteoHumidity.text = meteo.weather!!.main!!.humidity.toString() + " %"
        meteoVisibility.text = (meteo.weather!!.visibility!! / 1000).toString() + " km"
        meteoUVIndice.text = meteo.uv!!.value.toString() + " (" + getUVSuffix(meteo.uv!!.value!!) + ")"
    }

    fun analyseDescription(weather: WeatherData2): Int {
        var choosenOne: Int
        val id = weather.id!!

        if (id in 0..232) {
            choosenOne = R.drawable.storm
        } else if (id in 300..321 || id in 520..531) {
            choosenOne = R.drawable.light_rain
        } else if (id in 500..504) {
            choosenOne = R.drawable.rain
        } else if (id == 511 || id in 600..601 || id in 615..622) {
            choosenOne = R.drawable.snowflake
        } else if (id in 611..613) {
            choosenOne = R.drawable.hail
        } else if (id in 701..771) {
            choosenOne = R.drawable.cloud
        } else if (id == 781) {
            choosenOne = R.drawable.tornado
        } else if (id == 800) {
            choosenOne = R.drawable.sun
        } else if (id == 801 || id == 802) {
            choosenOne = R.drawable.cloudy
        } else if (id == 803 || id == 804) {
            choosenOne = R.drawable.clouds
        } else {
            choosenOne = R.drawable.thermometer
        }
        return choosenOne

    }

    private fun getUVSuffix(uv: Double): String {
        if (uv <= 2) {
            return "Bas"
        } else if (uv >= 6) {
            return "Haut"
        }
        return "Moyen"
    }

    private fun analyseWindDirection(degrees: Double): String {
        var windDirection: String

        if (degrees in 348.75..360.0) {
            windDirection = "N "
        } else if (degrees in 0.0..11.25) {
            windDirection = "N "
        } else if (11.25 < degrees && degrees <= 33.75) {
            windDirection = "NNE "
        } else if (33.75 < degrees && degrees <= 56.25) {
            windDirection = "NE "
        } else if (56.25 < degrees && degrees <= 78.75) {
            windDirection = "ENE "
        } else if (78.75 < degrees && degrees <= 101.25) {
            windDirection = "E "
        } else if (101.25 < degrees && degrees <= 123.75) {
            windDirection = "ESE "
        } else if (123.75 < degrees && degrees <= 146.25) {
            windDirection = "SE "
        } else if (146.25 < degrees && degrees <= 168.75) {
            windDirection = "SSE "
        } else if (168.75 < degrees && degrees <= 191.25) {
            windDirection = "S "
        } else if (191.25 < degrees && degrees <= 213.75) {
            windDirection = "SSW "
        } else if (213.75 < degrees && degrees <= 236.25) {
            windDirection = "SW "
        } else if (236.25 < degrees && degrees <= 258.75) {
            windDirection = "WSW "
        } else if (258.75 < degrees && degrees <= 281.25) {
            windDirection = "W "
        } else if (281.25 < degrees && degrees <= 303.75) {
            windDirection = "WNW "
        } else if (303.75 < degrees && degrees <= 326.25) {
            windDirection = "NW "
        } else if (326.25 < degrees && degrees < 348.75) {
            windDirection = "NNW "
        } else {
            windDirection  = ""
        }
        return windDirection
    }
}
