package com.oceanshare.oceanshare

import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class WeatherConverter {
    fun getTemperature(temp: Double): String {
        return ((temp - 32) / 1.8).roundToInt().toString() + "°C"
    }

    fun getSunriseTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm")
        val date = Date((timestamp * 1000.00).toLong())
        return sdf.format(date)
    }

    fun getSunsetTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm")
        val date = Date((timestamp * 1000.00).toLong())
        return sdf.format(date)
    }

    fun getCloudyValue(value: Int): String {
        return value.toString() + " %"
    }

    fun getWindDirection(windDegree: Double): String {
        lateinit var orientation: String
        var direction = windDegree.toInt()
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
        } else {
            orientation = "N"
        }
        return orientation
    }

    fun getWindSpeed(speed: Double): String {
        return " " + (speed * 1.609344).roundToInt().toString() + " km/h"
    }

    fun getWindData(direction: Double, speed: Double): String {
        return (getWindDirection(direction) + getWindSpeed(speed))
    }

    fun getHumidity(humidity: Int): String {
        return humidity.toString() + " %"
    }

    fun getUv(uv: Double): String {
        return uv.toString()
    }
}