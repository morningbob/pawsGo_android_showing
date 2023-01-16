package com.bitpunchlab.android.pawsgo.messages

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.bitpunchlab.android.pawsgo.database.PawsGoDatabase
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModel
import com.bitpunchlab.android.pawsgo.modelsRoom.MessageRoom
import kotlinx.coroutines.InternalCoroutinesApi

class MessagesViewModel(application: Application) : AndroidViewModel(application) {

    var userID = MutableLiveData<String>()

    @OptIn(InternalCoroutinesApi::class)
    var localDatabase = PawsGoDatabase.getInstance(application)

    var user = Transformations.switchMap(userID) { id ->
        localDatabase.pawsDAO.getUserWithMessages(id)
    }

    var messagesReceived = Transformations.switchMap(user) { user ->
        // we separate out the messagesReceived from all the messages in user
        val messagesGot = ArrayList<MessageRoom>()
        for (message in user.messages) {
            if (message.targetEmail == user.user.userEmail) {
                messagesGot.add(message)
            }
        }
        MutableLiveData<List<MessageRoom>>(messagesGot)
    }

    var messagesSent = Transformations.switchMap(user) { user ->
        val messagesGot = ArrayList<MessageRoom>()
        for (message in user.messages) {
            if (message.senderEmail == user.user.userEmail) {
                messagesGot.add(message)
            }
        }
        MutableLiveData<List<MessageRoom>>(messagesGot)
    }



    var _chosenMessage = MutableLiveData<MessageRoom?>()
    val chosenMessage get() = _chosenMessage

    fun onMessageClicked(message: MessageRoom) {
        _chosenMessage.value = message
    }

    fun onFinishedMessage() {
        _chosenMessage.value = null
    }

}

class MessagesViewModelFactory(private val application: Application)
    : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessagesViewModel::class.java)) {
            return MessagesViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}