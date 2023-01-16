package com.bitpunchlab.android.pawsgo.database

import android.util.Log
import androidx.room.TypeConverter
import com.bitpunchlab.android.pawsgo.modelsRoom.DogRoom
import com.bitpunchlab.android.pawsgo.modelsRoom.MessageRoom
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

class Converters {

    @TypeConverter
    fun dateFromString(dateString: String) : Date {
        var date : Date = Calendar.getInstance().time
        val simpleDateFormat : SimpleDateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy")
        try {
            date = simpleDateFormat.parse(dateString)
        } catch (e: Error){
            Log.i("error parsing date", e.toString())
        }
        return date
    }

    @TypeConverter
    fun dateToString(date: Date) : String {
        return date.toString()
    }
/*
    @TypeConverter
    fun fromMessageRoomToJSON(messageList: List<MessageRoom>) : String {
        return Json.encodeToString(messageList)
    }

    @TypeConverter
    fun fromJSONToMessageRoom(messageJSON: String) : List<MessageRoom> {
        return Json.decodeFromString(messageJSON)
    }
*/
    @TypeConverter
    fun fromMessagesJSONToMessages(messagesJSON: String) : List<MessageRoom> {
        val objectType = object : TypeToken<List<MessageRoom>>() { }.type
        return Gson().fromJson<List<MessageRoom>>(messagesJSON, objectType)
    }

    @TypeConverter
    fun fromMessagesToMessagesJSON(messages: List<MessageRoom>) : String {
        return Gson().toJson(messages)
    }

    @TypeConverter
    fun fromDogRoomToJSON(dogList: List<DogRoom>) : String {
        return Json.encodeToString(dogList)
    }

    @TypeConverter
    fun fromJSONToDogRoom(dogJSON: String) : List<DogRoom> {
        return Json.decodeFromString<List<DogRoom>>(dogJSON)
    }

    @TypeConverter
    fun fromStringListToJSON(imageList: List<String>) : String {
        return Json.encodeToString(imageList)
    }

    @TypeConverter
    fun fromJSONToStringList(imageJSON: String) : List<String> {
        return Json.decodeFromString(imageJSON)
    }
}