package com.bitpunchlab.android.pawsgo.dogsDisplay

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bitpunchlab.android.pawsgo.database.PawsGoDatabase
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModel
import com.bitpunchlab.android.pawsgo.modelsRoom.DogRoom
import kotlinx.coroutines.InternalCoroutinesApi

// we will load all lost dogs and found dogs in Firebase client
// and save to local database, then, we retrieve them here as there
// is any change
class DogsViewModel(application: Application) : AndroidViewModel(application) {

    //private val context = getApplication<Application>().applicationContext

    @OptIn(InternalCoroutinesApi::class)
    val localDatabase = PawsGoDatabase.getInstance(application.applicationContext)
    var lostDogs = localDatabase.pawsDAO.getAllLostDogs()

    var foundDogs = localDatabase.pawsDAO.getAllFoundDogs()

    var _dogReports = MutableLiveData<List<DogRoom>>()
    val dogReports get() = _dogReports

    var _chosenDog = MutableLiveData<DogRoom?>()
    val chosenDog get() = _chosenDog

    var _dogMessage = MutableLiveData<DogRoom?>()
    val dogMessage get() = _dogMessage

    // let seperate the variables
    var petName = MutableLiveData<String>()
    //val petName get() = _petName

    var petType = MutableLiveData<String>()
    //val petType get() = _petType

    var petBreed = MutableLiveData<String>()
    //val petBreed get() = _petBreed

    var petGender = MutableLiveData<Int>(0)
    //val petGender get() = _petGender

    var petAgeString = MutableLiveData<String>()
    //val petAgeString get() = _petAgeString

    var petAge = MutableLiveData<Int>()
    //val petAge get() = _petAge

    var dateLastSeen = MutableLiveData<String>()
    //val dateLastSeen get() = _dateLastSeen

    var lostHour = MutableLiveData<Int>()
    //val lostHour get() = _lostHour

    var lostMinute = MutableLiveData<Int>()
    //val lostMinute get() = _lostMinute

    var placeLastSeen = MutableLiveData<String>()
    //val placeLastSeen get() = _placeLastSeen

    var petNotes = MutableLiveData<String>()
    //val petNotes get() = _petNotes

    var lat = MutableLiveData<Double>()
    var lng = MutableLiveData<Double>()

    // these 2 variables hold the info entered in pet form for the report a pet fragment to use
    var tempPet = MutableLiveData<DogRoom?>()
    var tempImage : Bitmap? = null
    var tempImageByteArray : ByteArray? = null
    var _readyProcessReport = MutableLiveData<Boolean>(false)
    val readyProcessReport get() = _readyProcessReport
    var uploadClicked = false
    var choosedPicture = false
    var shouldDeleteReport = MutableLiveData<Boolean>(false)

    fun onDogChosen(dog: DogRoom) {
        _chosenDog.value = dog
    }

    fun finishedDogChosen() {
        _chosenDog.value = null
    }

    // when the dog message variable has a dog in it,
    // we'll start the messaging function
    fun onDogMessageClicked(dog: DogRoom) {
        _dogMessage.value = dog
    }

    fun finishedDogMessage() {
        _dogMessage.value = null
    }

    // retrieve the current user's profile in local database
    // check the lostDogs list to get all dogs

    fun prepareDogReports(userID: String) {
        //dogReports = localDatabase
    }

}

class DogsViewModelFactory(private val application: Application)
    : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DogsViewModel::class.java)) {
            return DogsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}