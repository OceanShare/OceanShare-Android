package com.oceanshare.oceanshare

import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class WeatherConverter {
    fun getTemperature(temp: Double?): String {
        temp?.let {
            return ((it - 273.15).roundToInt().toString() + "°C")
        }
        return "-- °C"
    }

    fun getFTemperature(temp: Double?): String {
        temp?.let {
            return (it.roundToInt().toString() + "°F")
        }
        return "-- °F"
    }

    fun getTime(timestamp: Long?): String {
        timestamp?.let {
            val sdf = SimpleDateFormat("HH:mm")
            val date = Date((it * 1000.00).toLong())
            return sdf.format(date)
        }
        return "--:--"
    }

    fun getCloudyValue(value: Int?): String {
        return value.toString() + " %"
    }

    private fun getWindDirection(windDegree: Double?): String {
        var orientation = "--"
        windDegree?.let {
            when (it.toInt()) {
                in 23..68 -> {
                    orientation = "NE"
                }
                in 69..112 -> {
                    orientation = "E"
                }
                in 113..158 -> {
                    orientation = "SE"
                }
                in 159..202 -> {
                    orientation = "S"
                }
                in 203..248 -> {
                    orientation = "SO"
                }
                in 249..292 -> {
                    orientation = "O"
                }
                in 293..338 -> {
                    orientation = "NO"
                }
                else -> {
                    orientation = "N"
                }
            }
        }
        return orientation
    }

    private fun getWindSpeed(speed: Double?): String {
        speed?.let {
            return " " + (it * 1.609344).roundToInt().toString() + " km/h"
        }
        return "-- km/h"
    }

    fun getWindData(direction: Double?, speed: Double?): String {
        return (getWindDirection(direction) + getWindSpeed(speed))
    }

    fun getHumidity(humidity: Int?): String {
        return "$humidity %"
    }

    fun getVisibility(visibility: Int?): String {
        visibility?.let {
            return (it / 1000).toString() + " km"
        }
        return "-- km"
    }

    fun getUv(uv: Double?): String {
        var suffix: String
        uv?.let {
            suffix = when {
                it <= 2 -> {
                    " (Faible)"
                }
                it >= 6 -> {
                    " (Haut)"
                }
                else -> {
                    " (Moyen)"
                }
            }
            return it.toString() + suffix
        }
        return "--"
    }

    fun getWeatherIcon(weatherId: Int): Int {
        val choosenOne: Int
        val id = weatherId

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
}