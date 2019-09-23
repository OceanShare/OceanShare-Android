package com.oceanshare.oceanshare

class MarkerData(var id: Long ?= null, var latitude: Double, var longitude: Double,
                 var groupId: Int, var description: String, var time: String, var user: String,
                 var timestamp: Long, var markerIcon: Int ?= null, var upvote: Int = 0,
                 var downvote: Int = 0, var vote: MutableList<MarkerVote> ?= null)