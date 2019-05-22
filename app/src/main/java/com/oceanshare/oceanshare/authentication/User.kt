package com.oceanshare.oceanshare.authentication

class User {
    var name: String? = null
    var email: String? = null
    var shipName: String? = null

    constructor() {}

    constructor(name: String?, email: String?, shipName: String?) {
        this.name = name
        this.email = email
        this.shipName = shipName
    }
}