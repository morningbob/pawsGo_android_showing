package com.bitpunchlab.android.pawsgo.modelsRoom

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.util.*

@Entity(tableName = "user_table")
@Parcelize
data class UserRoom(
    @PrimaryKey
    var userID: String,
    var userName: String,
    var userEmail: String,
    var lostDogs: List<DogRoom>,
    var dogs: List<DogRoom>,
    var dateCreated: String,
) : Parcelable