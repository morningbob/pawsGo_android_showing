package com.bitpunchlab.android.pawsgo.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.bitpunchlab.android.pawsgo.modelsRoom.DogRoom
import com.bitpunchlab.android.pawsgo.modelsRoom.MessageRoom
import com.bitpunchlab.android.pawsgo.modelsRoom.UserRoom
import com.bitpunchlab.android.pawsgo.modelsRoom.UserWithMessages

@Dao
interface PawsDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUser(user: UserRoom)

    @Query("SELECT * FROM user_table WHERE :id == userID LIMIT 1")
    fun getUser(id: String) : LiveData<UserRoom>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDogs(vararg dog: DogRoom)

    @Query("SELECT * FROM dog_table WHERE :id == dogID LIMIT 1")
    fun getDog(id: String) : LiveData<DogRoom>

    @Query("SELECT * FROM dog_table WHERE isLost == 1")
    fun getAllLostDogs() : LiveData<List<DogRoom>>

    @Query("SELECT * FROM dog_table WHERE isLost == 0")
    fun getAllFoundDogs() : LiveData<List<DogRoom>>

    @Query("SELECT * FROM dog_table")
    fun getAllDogs() : List<DogRoom>

    @Delete
    fun deleteDogs(vararg dog: DogRoom)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessages(vararg message: MessageRoom)

    @Query("SELECT * FROM message_table WHERE :email == targetEmail")
    fun getAllMessagesReceived(email: String) : LiveData<List<MessageRoom>>

    @Query("SELECT * FROM message_table WHERE :email == senderEmail")
    fun getAllMessagesSent(email: String) : LiveData<List<MessageRoom>>

    @Transaction
    @Query("SELECT * FROM user_table")
    fun getUsersWithMessages() : List<UserWithMessages>

    @Query("SELECT * FROM user_table WHERE :id == userID")
    fun getUserWithMessages(id: String) : LiveData<UserWithMessages>

    //@Query("DELETE * FROM dog_table WHERE :id == dogID")
    //fun deletePetByID(id: String)

}