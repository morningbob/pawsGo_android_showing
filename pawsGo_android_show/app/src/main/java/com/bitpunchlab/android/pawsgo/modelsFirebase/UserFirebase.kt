package com.bitpunchlab.android.pawsgo.modelsFirebase

import java.util.*
import kotlin.collections.HashMap

class UserFirebase {

    var userID: String = ""
    var userName: String = ""
    var userEmail: String = ""
    var lostDogs = HashMap<String, DogFirebase>()
    var dogs = HashMap<String, DogFirebase>()
    var dateCreated: String = ""
    var messagesReceived = HashMap<String, MessageFirebase>()
    var messagesSent = HashMap<String, MessageFirebase>()

    constructor()

    constructor(id: String, name: String, email: String,
                lost: HashMap<String, DogFirebase>, dog: HashMap<String, DogFirebase>,
                date: String,
                allMessagesReceived: HashMap<String, MessageFirebase>,
                allMessagesSent: HashMap<String, MessageFirebase>) : this() {
                    userID = id
                    userName = name
                    userEmail = email
                    lostDogs = lost
                    dogs = dog
                    dateCreated = date
                    messagesReceived = allMessagesReceived
                    messagesSent = allMessagesSent
                }
}