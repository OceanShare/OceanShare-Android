package com.oceanshare.oceanshare

class UserData(var markerId: Long ?= null, var name: String ?= null, var latitude: Double,
               var longitude: Double, var ship_name : String ?="", user_active : Boolean = false)