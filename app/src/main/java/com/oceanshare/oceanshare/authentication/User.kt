package com.oceanshare.oceanshare.authentication

class Preferences {
    var boatId: Int = 0
    var ghost_mode: Boolean = false
    var show_picture: Boolean = false
    var user_active: Boolean = false

    constructor() {}

    constructor(boatId: Int = 0, ghostMode: Boolean = false, showPicture: Boolean = false, userActive: Boolean = false) {
        this.boatId = boatId
        this.ghost_mode = ghostMode
        this.show_picture = showPicture
        this.user_active = userActive
    }
}

class User {
    var name: String? = null
    var email: String? = null
    var ship_name: String? = null
    var preferences: Preferences? = null

    constructor() {}

    constructor(name: String?, email: String?, shipName: String?) {
        this.name = name
        this.email = email
        this.ship_name = shipName
    }
}