package com.bitpunchlab.android.pawsgo.firebase

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.telephony.PhoneNumberUtils
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import com.bitpunchlab.android.pawsgo.AppState
import com.bitpunchlab.android.pawsgo.database.PawsGoDatabase
import com.bitpunchlab.android.pawsgo.modelsFirebase.DogFirebase
import com.bitpunchlab.android.pawsgo.modelsFirebase.MessageFirebase
import com.bitpunchlab.android.pawsgo.modelsFirebase.UserFirebase
import com.bitpunchlab.android.pawsgo.modelsRoom.DogRoom
import com.bitpunchlab.android.pawsgo.modelsRoom.MessageRoom
import com.bitpunchlab.android.pawsgo.modelsRoom.UserRoom
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.HashMap

@OptIn(InternalCoroutinesApi::class)
class FirebaseClientViewModel(application: Application) : AndroidViewModel(application) {

    var auth : FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore = Firebase.firestore

    private var _userName = MutableLiveData<String>()
    val userName get() = _userName

    private var _userEmail = MutableLiveData<String>()
    val userEmail get() = _userEmail

    private var _userPassword = MutableLiveData<String>()
    val userPassword get() = _userPassword

    var _currentPassword = MutableLiveData<String>()
    val currentPassword get() = _currentPassword

    private var _userConfirmPassword = MutableLiveData<String>()
    val userConfirmPassword get() = _userConfirmPassword

    private var _nameError = MutableLiveData<String>()
    val nameError get() = _nameError

    private var _emailError = MutableLiveData<String>()
    val emailError get() = _emailError

    private var _passwordError = MutableLiveData<String>()
    val passwordError get() = _passwordError

    private var _newPasswordError = MutableLiveData<String>()
    val newPasswordError get() = _newPasswordError

    private var _confirmPasswordError= MutableLiveData<String>()
    val confirmPasswordError get() = _confirmPasswordError

    private var fieldsValidArray = ArrayList<Int>()

    private var coroutineScope = CoroutineScope(Dispatchers.IO)

    var isCreatingUserAccount = false

    private var localDatabase : PawsGoDatabase

    var _appState = MutableLiveData<AppState>(AppState.NORMAL)
    val appState get() = _appState

    // this is the trigger live data that trigger the fetch of a user object
    var userIDLiveData  = MutableLiveData<String>()
    // whenever the userIDLiveData changed, the currentUserLiveData's transformation
    // will be triggered and retrieve the user from local database
    // now, we can observe this variable to update the UI.
    val currentUserRoomLiveData = Transformations.switchMap(userIDLiveData) { id ->
        Log.i("current user live data", "retrieving user")
        retrieveUserRoom(id)
    }

    var currentUserID : String = ""
    var currentUserEmail : String = ""

    var _currentUserFirebaseLiveData = MutableLiveData<UserFirebase>()
    val currentUserFirebaseLiveData get() = _currentUserFirebaseLiveData

    val storageRef = Firebase.storage.reference

    val ONE_MEGABYTE: Long = 1024 * 1024

    private var authStateListener = FirebaseAuth.AuthStateListener { auth ->
        if (auth.currentUser != null) {
            _appState.postValue(AppState.LOGGED_IN)
            Log.i("auth", "changed state to login")
            // as soon as we got the auth current user, we use its uid to retrieve the
            // user room in local database
            userIDLiveData.postValue(auth.currentUser!!.uid)
            // we also need to retrieve the user firebase from Firestore
            // to get the most updated user profile,
            // we will save it to local room database too
            coroutineScope.launch {
                var userFirebaseDeferred = coroutineScope.async { retrieveUserFirebase() }
                var userFirebase = userFirebaseDeferred.await()
                userFirebase?.let {
                    _currentUserFirebaseLiveData.postValue(userFirebase!!)
                    // update local database
                    localDatabase = PawsGoDatabase.getInstance(application)
                    localDatabase.pawsDAO.insertUser(convertUserFirebaseToUserRoom(userFirebase))
                    processMessagesReceivedFromFirebase(userFirebase)
                }
                // we also retrieve the lost dogs and the found dogs from Firestore,
                // save to the local database, the local database changes, the data will
                // be retrieved in dogs view model.
                updateDogsList()
            }

        } else {
            _appState.postValue(AppState.LOGGED_OUT)
            Log.i("auth", "changed state to logout")
        }
    }

    private val nameValid: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(userName) { name ->
            if (name.isNullOrEmpty()) {
                nameError.value = "Name must not be empty."
                value = false
            } else {
                value = true
                nameError.value = ""
            }
        }
    }

    private val emailValid: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(userEmail) { email ->
            if (!email.isNullOrEmpty()) {
                if (!isEmailValid(email)) {
                    emailError.value = "Please enter a valid email."
                    value = false
                } else {
                    value = true
                    emailError.value = ""
                }
            } else {
                value = false
            }
            Log.i("email valid? ", value.toString())
        }
    }
/*
    // should be at least 10 number and max 15, I think
    private val phoneValid: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(userPhone) { phone ->
            if (phone.isNullOrEmpty()) {
                phoneError.value = "Phone number must not be empty."
                value = false
            } else if (phone.count() < 10 || phone.count() > 13)
                phoneError.value = "Phone number should be at least \n 10 number and maximum 13 number."
            else if (!isPhoneValid(phone)) {
                phoneError.value = "Phone number is not valid."
            } else {
                value = true
                phoneError.value = ""
            }
        }
    }
*/
    private val passwordValid: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(userPassword) { password ->
            if (!password.isNullOrEmpty()) {
                if (isPasswordContainSpace(password)) {
                    passwordError.value = "Password cannot has space."
                    value = false
                } else if (password.count() < 8) {
                    passwordError.value = "Password should be at least 8 characters."
                    value = false
                } else if (!isPasswordValid(password)) {
                    passwordError.value = "Password can only be composed of letters and numbers."
                    value = false
                } else {
                    passwordError.value = ""
                    value = true
                }
            } else {
                value = false
            }
            Log.i("password valid? ", value.toString())
        }
    }

    private val confirmPasswordValid: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(userConfirmPassword) { confirmPassword ->
            if (!confirmPassword.isNullOrEmpty()) {
                if (!isConfirmPasswordValid(userPassword.value!!, confirmPassword)) {
                    confirmPasswordError.value = "Passwords must be the same."
                    value = false
                } else {
                    confirmPasswordError.value = ""
                    value = true
                }
            } else {
                value = false
            }
            Log.i("confirm valid? ", value.toString())
        }
    }

    private val currentPasswordValid: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(currentPassword) { password ->
            if (!password.isNullOrEmpty()) {
                if (isPasswordContainSpace(password)) {
                    newPasswordError.value = "Password cannot has space."
                    value = false
                } else if (password.count() < 8) {
                    newPasswordError.value = "Password should be at least 8 characters."
                    value = false
                } else if (!isPasswordValid(password)) {
                    newPasswordError.value = "Password can only be composed of letters and numbers."
                    value = false
                } else {
                    newPasswordError.value = ""
                    value = true
                }
            } else {
                value = false
            }
            Log.i("password valid? ", value.toString())
        }
    }

    private fun isEmailValid(email: String) : Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isPhoneValid(phone: String) : Boolean {
        return PhoneNumberUtils.isGlobalPhoneNumber(phone)
    }

    private fun isPasswordValid(password: String) : Boolean {
        val passwordPattern = Pattern.compile("^[A-Za-z0-9]{8,20}$")
        return passwordPattern.matcher(password).matches()
    }

    private fun isPasswordContainSpace(password: String) : Boolean {
        return password.contains(" ")
    }

    private fun isConfirmPasswordValid(password: String, confirmPassword: String) : Boolean {
        //Log.i("confirming password: ", "password: $password, confirm: $confirmPassword")
        return password == confirmPassword
    }

    private fun sumFieldsValue() : Boolean {
        return fieldsValidArray.sum() == 4
    }
    var readyRegisterLiveData = MediatorLiveData<Boolean>()
    var readyLoginLiveData = MediatorLiveData<Boolean>()
    var readyChangePasswordLiveData = MediatorLiveData<Boolean>()


    init {
        // this live data stores 5 values,
        // it represents the validity of all the fields of the registration form
        // if the corresponding field is valid, the value in this array, the particular position
        // will be 1, otherwise, it will be 0
        // that makes checking validities of all fields more efficient,
        // by just summing up all 4 fields to see if it is 4, then it is ready
        fieldsValidArray = arrayListOf(0,0,0,0)
        auth.addAuthStateListener(authStateListener)
        localDatabase = PawsGoDatabase.getInstance(application)

        currentUserRoomLiveData.observeForever(Observer { user ->
            if (user != null) {
                Log.i("user room live data", "got back user")
                Log.i("user room live data", "userEmail: ${user.userEmail}")
                currentUserID = user.userID
                currentUserEmail = user.userEmail
            } else {
                Log.i("current user live data observed", "got back null")
            }
        })

        appState.observeForever( Observer { state ->
            when (state) {
                AppState.READY_CREATE_USER_AUTH -> {
                    coroutineScope.launch {
                        if (createUserOfAuth()) {
                            _appState.postValue(AppState.READY_CREATE_USER_FIREBASE)
                        } else {
                            _appState.postValue(AppState.ERROR_CREATE_USER_AUTH)
                        }
                    }
                }
                AppState.ERROR_CREATE_USER_AUTH -> {
                    resetAllFields()
                    _appState.value = AppState.NORMAL
                }
                AppState.READY_CREATE_USER_FIREBASE -> {
                    Log.i("ready create user firebase", "user id from auth ${auth.currentUser!!.uid}")
                    // we format the date created here
                    val currentDate = Date()
                    val dateFormat = SimpleDateFormat("yyyy/MM/dd hh:mm:ss")
                    //Calendar.DAY_OF_MONTH
                    val dateString = dateFormat.format(currentDate)
                    val user = createUserFirebase(
                        id = auth.currentUser!!.uid,
                        name = userName.value!!,
                        email = userEmail.value!!,
                        lost = HashMap<String, DogFirebase>(),
                        dogs = HashMap<String, DogFirebase>(),
                        dateCreated = dateString)
                    coroutineScope.launch {
                        if (saveUserFirebase(user) && saveEmailFirestore(userEmail.value!!)) {
                            // we also create the user room and save it here
                            val userRoom = convertUserFirebaseToUserRoom(user)

                            Log.i("creating and saving the user room", "userID: ${userRoom.userID}")
                            saveUserRoom(userRoom)
                            _appState.postValue(AppState.SUCCESS_CREATED_USER_ACCOUNT)
                        } else {
                            _appState.postValue(AppState.ERROR_CREATE_USER_ACCOUNT)
                        }

                    }
                }
                AppState.EMAIL_ALREADY_EXISTS -> {
                    userEmail.value = ""
                }
                AppState.EMAIL_SERVER_ERROR -> {
                    resetAllFields()
                }
                AppState.ERROR_CREATE_USER_ACCOUNT -> {
                    resetAllFields()
                }
                AppState.SUCCESS_CREATED_USER_ACCOUNT -> {
                    resetAllFields()
                }
                AppState.LOGGED_IN -> {
                    //resetAllFields()
                    Log.i("firebaseClient", "login state detected")
                    if (isCreatingUserAccount) {
                        _appState.postValue(AppState.READY_CREATE_USER_FIREBASE)
                    }
                }
                AppState.INCORRECT_CREDENTIALS -> {
                    resetAllFields()
                }
                AppState.RESET -> {
                    resetAllFields()
                    _appState.value = AppState.NORMAL
                }
                else -> 0
            }
        })


        // this live data observes all the validities of the fields' live data
        // it set the fieldsValidArray's value according to the validity.
        // whenever the sum of the fieldsValidArray is 4, this data returns true
        // else false
        readyRegisterLiveData.addSource(nameValid) { valid ->
            if (valid) {
                fieldsValidArray[0] = 1
                // check other fields validity
                readyRegisterLiveData.value = sumFieldsValue()
            } else {
                fieldsValidArray[0] = 0
                readyRegisterLiveData.value = false
            }
        }
        readyRegisterLiveData.addSource(emailValid) { valid ->
            if (valid) {
                fieldsValidArray[1] = 1
                // check other fields validity
                readyRegisterLiveData.value = sumFieldsValue()
            } else {
                fieldsValidArray[1] = 0
                readyRegisterLiveData.value = false
            }
        }

        readyRegisterLiveData.addSource(passwordValid) { valid ->
            if (valid) {
                fieldsValidArray[2] = 1
                // check other fields validity
                readyRegisterLiveData.value = sumFieldsValue()
            } else {
                fieldsValidArray[2] = 0
                readyRegisterLiveData.value = false
            }
        }
        readyRegisterLiveData.addSource(confirmPasswordValid) { valid ->
            if (valid) {
                fieldsValidArray[3] = 1
                // check other fields validity
                readyRegisterLiveData.value = sumFieldsValue()
            } else {
                fieldsValidArray[3] = 0
                readyRegisterLiveData.value = false
            }
        }
        readyLoginLiveData.addSource(emailValid) { valid ->
            readyLoginLiveData.value = (valid && passwordValid.value != null && passwordValid.value!!)

        }
        readyLoginLiveData.addSource(passwordValid) { valid ->
            readyLoginLiveData.value = (valid && emailValid.value != null && emailValid.value!!)

        }

        readyChangePasswordLiveData.addSource(passwordValid) { valid ->
            readyChangePasswordLiveData.value = currentPasswordValid.value != null &&
                    confirmPasswordValid.value != null &&
                valid && currentPasswordValid.value!! && confirmPasswordValid.value!!
        }
        readyChangePasswordLiveData.addSource(currentPasswordValid) { valid ->
            readyChangePasswordLiveData.value = valid &&
                    passwordValid.value != null && confirmPasswordValid.value != null &&
                    passwordValid.value!! && confirmPasswordValid.value!!
        }

        readyChangePasswordLiveData.addSource(confirmPasswordValid) { valid ->
            readyChangePasswordLiveData.value = valid &&
                    passwordValid.value != null &&
                    currentPasswordValid.value != null &&
                    passwordValid.value!!
                    && currentPasswordValid.value!!
        }
    }

    suspend fun loginUserOfAuth() : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            auth
                .signInWithEmailAndPassword(userEmail.value!!.lowercase(Locale.getDefault()), userPassword.value!!)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i("firebase auth, sign in", "success")
                        cancellableContinuation.resume(true){}
                    } else {
                        Log.i("firebase auth, sign in", "failed ${task.exception?.message}")
                        cancellableContinuation.resume(false){}
                    }
                }
        }

    fun logoutUser() {
        Log.i("logout", "logging out")
        auth.signOut()
        resetAllFields()
    }

    private fun resetAllFields() {
        userName.value = ""
        userEmail.value = ""
        userPassword.value = ""
        userConfirmPassword.value = ""
        currentPassword.value = ""
        nameError.value = ""
        emailError.value = ""
        passwordError.value = ""
        confirmPasswordError.value = ""
        isCreatingUserAccount = false
        currentUserID = ""
        currentUserEmail = ""
    }

    suspend fun checkEmailExistFirestore(newEmail: String) : Int =
        suspendCancellableCoroutine<Int> { cancellableContinuation ->
            firestore
                .collection("emails")
                .whereEqualTo("email", newEmail)
                .get()
                .addOnSuccessListener { docRef ->
                    Log.i("check email exists", "success")
                    if (docRef.isEmpty) {
                        // can proceed to registration
                        cancellableContinuation.resume(1) {}
                    } else {
                        // email already exists
                        cancellableContinuation.resume(2) {}
                    }
                }
                .addOnFailureListener { e ->
                    Log.i("check email exists", "failure: ${e.message}")
                    cancellableContinuation.resume(0) {}
                }
    }

    // we test if firebase auth already has the email by checking if the email signin exists
    // for the email.
    suspend fun checkEmailExistFirebaseAuth(email: String) : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            auth
                .fetchSignInMethodsForEmail(email)
                .addOnCompleteListener { task ->
                    if (task.result.signInMethods?.size == 0) {
                        // email not exist
                        Log.i("check firebase auth email exists", "email doesn't exist")
                        cancellableContinuation.resume(false) {}
                    } else {
                        // email exists
                        Log.i("check firebase auth email exists", "email exist")
                        cancellableContinuation.resume(true) {}
                    }
                }
    }

    private suspend fun saveEmailFirestore(email: String) : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            var data = java.util.HashMap<String, String>()
            data.put("email", email)

            firestore
                .collection("emails")
                .document(email)
                .set(data)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i("save email", "success")
                        cancellableContinuation.resume(true) {}
                    } else {
                        Log.i("save email", "failed")
                        cancellableContinuation.resume(false) {}
                    }
                }
    }

    // when we create user firebase, the messages is for sure empty,
    // so, I can just put an empty list
    private fun createUserFirebase(id: String, name: String, email: String, lost: HashMap<String, DogFirebase>,
                                   dogs: HashMap<String, DogFirebase>, dateCreated: String) : UserFirebase {
        return UserFirebase(id = id, name = name, email = email,
            lost = HashMap<String, DogFirebase>(), dog = HashMap<String, DogFirebase>(),
            allMessagesReceived = HashMap<String, MessageFirebase>(),
            allMessagesSent = HashMap<String, MessageFirebase>(),
            date = dateCreated)
    }

    private suspend fun saveUserFirebase(user: UserFirebase) =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            firestore
                .collection("users")
                .document(user.userID)
                .set(user)
                .addOnSuccessListener { docRef ->
                    Log.i("firestore", "save new user, success")
                    cancellableContinuation.resume(true){}
                }
                .addOnFailureListener { e ->
                    Log.i("firestore", "save user failed: ${e.message}")
                    cancellableContinuation.resume(false){}
                }
    }

    private fun convertUserFirebaseToUserRoom(userFirebase: UserFirebase) : UserRoom {
        return UserRoom(userID = userFirebase.userID, userName = userFirebase.userName,
            userEmail = userFirebase.userEmail, dateCreated = userFirebase.dateCreated,
            lostDogs = convertDogMapToDogList(userFirebase.lostDogs),
            dogs = convertDogMapToDogList(userFirebase.dogs),
            //messages = convertMessageMapToMessageList(userFirebase.messages)
        )
            //messages = convertMessageMapToMessageList(userFirebase.messages))
    }

    private fun saveUserRoom(user: UserRoom) {
        coroutineScope.launch {
            localDatabase.pawsDAO.insertUser(user)
        }
    }

    private suspend fun retrieveUserFirebase() : UserFirebase? =
        suspendCancellableCoroutine<UserFirebase?> { cancellableContinuation ->
            firestore
                .collection("users")
                .document(auth.currentUser!!.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (!document.exists()) {
                        Log.i("retrieve user firebase", "can't find the user")
                        cancellableContinuation.resume(null) {}
                    } else {
                        Log.i("retrieve user firebase", "found the user")
                        val user = document.toObject(UserFirebase::class.java)!!
                        Log.i("retrieve user firebase", user.userName)
                        cancellableContinuation.resume(user) {}
                    }
                }
        }


    private fun retrieveUserRoom(id: String) : LiveData<UserRoom> {
        return localDatabase.pawsDAO.getUser(id)
    }

    private suspend fun retrieveAllDogsFirebase(lostOrFound: Boolean) : List<DogFirebase> =
        suspendCancellableCoroutine<List<DogFirebase>> { cancellableContinuation ->
            var collectionName = ""
            if (lostOrFound) {
                collectionName = "lostDogs"
            } else {
                collectionName = "foundDogs"
            }
            firestore
                .collection(collectionName)
                .get()
                .addOnSuccessListener { documents ->
                    Log.i("retrieve all dogs", "success")
                    val dogsList = ArrayList<DogFirebase>()
                    if (!documents.isEmpty) {
                        documents.map { doc ->
                            val dog = doc.toObject(DogFirebase::class.java)
                            dogsList.add(dog)
                            Log.i("retrieve all dogs", "added a dog ${dog.dogName}")
                        }
                    }
                    cancellableContinuation.resume(dogsList) {}
                }
                .addOnFailureListener { e ->
                    Log.i("retrieve all dogs", "failed: ${e.message}")
                    cancellableContinuation.resume(ArrayList()) {}
                }

    }

    private suspend fun retrieveDogsFromFirebase() : List<DogFirebase> {
        var lostDogList = ArrayList<DogFirebase>()
        var foundDogList = ArrayList<DogFirebase>()
        //var dogRoomList = ArrayList<DogRoom>()
        return withContext(Dispatchers.IO) {
            lostDogList.addAll(retrieveAllDogsFirebase(true))
            lostDogList.addAll(retrieveAllDogsFirebase(false))
            //cancellableContinuation.resume() {}
            //var result = lostDogList.addAll(foundDogList)
            return@withContext lostDogList
        }
    }

    private fun convertDogMapToDogList(dogHashmap: HashMap<String, DogFirebase>) : List<DogRoom> {
        val list = ArrayList<DogRoom>()
        for ((key, value) in dogHashmap) {
            list.add(convertDogFirebaseToDogRoom(value))
        }
        return list
    }

    private fun convertMessageMapToMessageList(messageMap: HashMap<String, MessageFirebase>)
        : List<MessageRoom> {
        val list = ArrayList<MessageRoom>()
        for ((key, value) in messageMap) {
            list.add(convertMessageFirebaseToMessageRoom(value))
        }
        return list
    }

    private fun convertMessageFirebaseToMessageRoom(messageFirebase: MessageFirebase) : MessageRoom {
        return MessageRoom(messageID = messageFirebase.messageID,
            senderName = messageFirebase.senderName, senderEmail = messageFirebase.senderEmail,
            messageContent = messageFirebase.messageContent, date = messageFirebase.date,
            targetEmail = messageFirebase.targetEmail, targetName = messageFirebase.targetName,
            userCreatorID = auth.currentUser!!.uid)
    }

    private fun convertDogRoomToDogFirebase(dogRoom: DogRoom): DogFirebase {
        return DogFirebase(id = dogRoom.dogID, name = dogRoom.dogName, animal = dogRoom.animalType,
            gender = dogRoom.dogGender,
            breed = dogRoom.dogBreed, age = dogRoom.dogAge, date = dogRoom.dateLastSeen,
            hr = dogRoom.hour, min = dogRoom.minute, place = dogRoom.placeLastSeen,
            userID = dogRoom.ownerID, userName = dogRoom.ownerName, userEmail = dogRoom.ownerEmail,
            lost = dogRoom.isLost, found = dogRoom.isFound,
            latLngPoint = createLatLngHashmap(dogRoom.locationLat, dogRoom.locationLng),
            address = dogRoom.locationAddress, note = dogRoom.notes)
    }

    private fun convertDogFirebaseToDogRoom(dogFirebase: DogFirebase): DogRoom {
        return DogRoom(dogID = dogFirebase.dogID, dogName = dogFirebase.dogName,
            animalType = dogFirebase.animalType,
            dogBreed = dogFirebase.dogBreed, dogGender = dogFirebase.dogGender,
            dogAge = dogFirebase.dogAge, ownerID = dogFirebase.ownerID,
            ownerName = dogFirebase.ownerName,
            ownerEmail = dogFirebase.ownerEmail, isLost = dogFirebase.isLost,
            isFound = dogFirebase.isFound, dateLastSeen = dogFirebase.dateLastSeen,
            hour = dogFirebase.hour, minute = dogFirebase.minute,
            placeLastSeen = dogFirebase.placeLastSeen,
            locationLat = dogFirebase.locationLatLng.get("Lat"),
            locationLng = dogFirebase.locationLatLng.get("Lng"),
            locationAddress = dogFirebase.locationAddress,
            notes = dogFirebase.notes)
    }

    private fun createLatLngHashmap(lat: Double?, lng: Double?) : HashMap<String, Double> {
        val latLngHashmap = HashMap<String, Double>()
        lat?.let {
            latLngHashmap.put("Lat", lat)
        }
        lng?.let {
            latLngHashmap.put("Lng", lng)
        }
        return latLngHashmap
    }

    private fun processMessagesReceivedFromFirebase(user: UserFirebase) {
        // from firebase, we got the user object
        // we convert the user firebase to user room, with the exception of the messages list
        // we separate the message lists and create message room objects and save it
        // by the relations defined in the database classes, the messages will be assigned to
        // the user by the userCreatorID.  It is a one to many relationship
        Log.i("messages received from firebase", "start processing")

        val allMessagesRoom = ArrayList<MessageRoom>()
        Log.i("messages received from firebase", "messages received: ${user.messagesReceived.size}")
        Log.i("messages sent from firebase", "messages sent: ${user.messagesSent.size}")
        allMessagesRoom.addAll(convertMessageMapToMessageList(user.messagesReceived))
        allMessagesRoom.addAll(convertMessageMapToMessageList(user.messagesSent))
        Log.i("messages received from firebase", "all messages collect: ${allMessagesRoom.size}")
        // save to local database
        coroutineScope.launch {
            localDatabase.pawsDAO.insertMessages(*allMessagesRoom.toTypedArray())
            Log.i("messages received from firebase", "inserted messages: ${allMessagesRoom.size}")
        }
    }

    private fun updateDogsList() {
        var requestList = ArrayList<DogFirebase>()
        var lostDogs = ArrayList<DogFirebase>()
        var dogRooms = ArrayList<DogRoom>()
        coroutineScope.launch {
            val lostDogsDeferred = coroutineScope.async {
                retrieveDogsFromFirebase() as ArrayList<DogFirebase>
            }
            lostDogs = lostDogsDeferred.await()
            Log.i("update dogs list from firebase", "lost dogs: ${lostDogs.size}")
            val dogRoomsDeferred = coroutineScope.async {
                localDatabase.pawsDAO.getAllDogs() as ArrayList<DogRoom>
            }
            dogRooms = dogRoomsDeferred.await()

            requestList =
                getListOfDogsRequestImage(lostDogs, dogRooms) as ArrayList<DogFirebase>

            // now we can send request to Firestore
            lostDogs.map { dog ->
                Log.i("update dogs list from firebase", "requesting 1 dog: ${dog.dogName}")
                // we can start a coroutine here,
                // so, each dog is processed in a seperate coroutine
                // it won't need to wait for the response one by one
                coroutineScope.launch {
                    val dogRoom = convertDogFirebaseToDogRoom(dog)
                    if (dog.dogImages.values.isNotEmpty()) {
                        val byteArray = requestDogImage(dog)
                        byteArray?.let {
                            // update the corresponding dog room object, with the new image filename
                            // and save the dog object locally
                            val imageUri = saveBitmapInternal(it, dog.dogName!!)
                            updateDogRoomWithImageUri(dogRoom, imageUri)
                            saveDogRoom(dogRoom)
                        }
                    } else {
                        saveDogRoom(dogRoom)
                    }
                }
            }
        }
    }

    private fun retrieveDogsFromLocalDatabase() : LiveData<List<DogRoom>> {
        return localDatabase.pawsDAO.getAllLostDogs()
    }

    private fun getListOfDogsRequestImage(dogListFirestore: ArrayList<DogFirebase>, dogListLocal: ArrayList<DogRoom>)
        : List<DogFirebase> {
        var dogFirebaseList = ArrayList<DogFirebase>()
        if (!dogListFirestore.isNullOrEmpty() && !dogListLocal.isNullOrEmpty()) {
            // this new list is already a copy , not references
            dogFirebaseList = dogListFirestore.toList() as ArrayList
            dogListLocal.map { dogRoom ->
                val dogFirebase = dogFirebaseList.find { it.dogID == dogRoom.dogID }
                dogFirebase?.let {
                    // that means, there are some images that were not stored in
                    // the local storage yet, so the number is different.
                    if (dogRoom.dogImagesUri?.size == dogFirebase.dogImages.size) {
                        dogFirebaseList.remove(dogFirebase)
                        Log.i("get list of dog image request", "removed 1 dog")
                    }
                }
            }
        }
        return dogFirebaseList
    }

    // we send the request one by one,
    // we need to save the images in a place that the app can access
    // we store the local url in dog object and save to local database
    //
    private suspend fun requestDogImage(dog: DogFirebase) : ByteArray? =
        suspendCancellableCoroutine<ByteArray?> { cancellableContinuation ->
            // create a httpRef
            if (dog.dogImages.values.isNotEmpty()) {
                // in this case, we just randomly get one of the photo to display
                val httpsRef = Firebase.storage.getReferenceFromUrl(dog.dogImages.values.first())
                httpsRef
                    .getBytes(ONE_MEGABYTE)
                    .addOnSuccessListener { byteArray ->
                        Log.i("request dog image from cloud storage", "success")
                        cancellableContinuation.resume(byteArray) {}
                    }
                    .addOnFailureListener { e ->
                        Log.i("request dog image from cloud storage", "failed: ${e.message}")
                        cancellableContinuation.resume(null) {}
                    }
            }
    }

    private fun convertByteArrayToBitmap(byteArray: ByteArray) : Bitmap {
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }

    private fun saveBitmapInternal(byteArray: ByteArray, dogName: String) : URI {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss")
        val fileName = "$timeStamp$dogName.jpg"
        var imageUri : URI? = null
        getApplication<Application>().applicationContext.apply {
            openFileOutput(fileName, Context.MODE_PRIVATE).use {
                it.write(byteArray)
            }
            val file = getFileStreamPath(fileName)
            imageUri = file.toURI()
            Log.i("save bitmap internally", "uri: $imageUri")
        }

        return imageUri!!
    }

    private fun updateDogRoomWithImageUri(dog: DogRoom, imageUri: URI) {
        var arrayList = ArrayList<String>()
        if (!dog.dogImagesUri.isNullOrEmpty()) {
            arrayList = dog.dogImagesUri as ArrayList<String>
        }
        arrayList.add(imageUri.toString())
        dog.dogImagesUri = arrayList

        coroutineScope.launch {
            localDatabase.pawsDAO.insertDogs(dog)
        }
    }

    private suspend fun createUserOfAuth() : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            auth
                .createUserWithEmailAndPassword(userEmail.value!!, userPassword.value!!)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i("firebase auth", "create user success.")
                        cancellableContinuation.resume(true){}
                    } else {
                        Log.i("firebase auth", "create user failure: ${task.exception?.message}")
                        cancellableContinuation.resume(false){}
                    }
                }
    }

    // new dog reported is saved in different folders according the lostOrFound
    // lost is true, dog is saved in lostDogs, lost is false, dog is saved in foundDogs
    suspend fun processDogReport(dogRoom: DogRoom, data: ByteArray? = null) : Boolean =
        // refactor the convert method!!
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
        if (currentUserID != null && currentUserID != "") {
            // I'll see if there is image byte array in dogsVM.
            // if the user uploaded a photo, the byte array will be there
            // I'll replace the new byte array in the dogImages
            // if there is no byte array in dogsVM, I'll just pass the old dogsImages array to the updated
            // pet object.

            // first we upload the dog image
            // we do this first because we need the url string that comes back when we save
            // the image in the storage.
            val dogFirebase = convertDogRoomToDogFirebase(dogRoom)
            var imageUrl : String? = null
            if (data != null) {
                coroutineScope.launch {
                    val imageUriDeferred = coroutineScope.async {
                        uploadImageFirebase(data, dogRoom.dogID, dogRoom.isLost!!)
                    }
                    imageUrl = imageUriDeferred.await()
                    imageUrl?.let {
                        Log.i("handle lost dog report", "imageUrl: $imageUrl")
                        // update the dog firebase
                        dogFirebase.dogImages.put(dogFirebase.dogID, it)
                        var dogImages = dogRoom.dogImages
                        var tempImages = ArrayList<String>()
                        if (!dogImages.isNullOrEmpty()) {
                            tempImages = dogImages as ArrayList<String>
                        } else {
                            tempImages = ArrayList<String>()
                        }
                        tempImages.add(it)
                        dogRoom.dogImages = tempImages
                        // if there is image uploaded, we wait for the above process done
                        // before we send the dog info
                        saveDogFirebase(dogFirebase)
                        saveDogRoom(dogRoom)
                        cancellableContinuation.resume(updateUserLostDogFirebase(dogFirebase)) {}

                    }
                }
            } else {
                // if there is no image uploaded, we send the dog info immediately
                coroutineScope.launch {
                    saveDogFirebase(dogFirebase)
                    saveDogRoom(dogRoom)
                    cancellableContinuation.resume(updateUserLostDogFirebase(dogFirebase)) {}
                }
            }
        }  else {
            Log.i("handle lost dog report", "couldn't find current user")
            cancellableContinuation.resume(false) {}
        }
    }

    private suspend fun saveDogFirebase(dog: DogFirebase) : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            var collectionName = ""
            collectionName = if (dog.isLost!!) {
                "lostDogs"
            } else {
                "foundDogs"
            }
            firestore
                .collection(collectionName)
                .document(dog.dogID.toString())
                .set(dog)
                .addOnSuccessListener { docRef ->
                    Log.i("save dog firebase", "success")
                    cancellableContinuation.resume(true){}
                }
                .addOnFailureListener { e ->
                    Log.i("save dog firesbase", "failure: ${e.message}")
                    cancellableContinuation.resume(false){}
                }
    }

    private fun saveDogRoom(dog: DogRoom) {
        coroutineScope.launch {
            localDatabase.pawsDAO.insertDogs(dog)
        }
    }

    private suspend fun uploadImageFirebase(data: ByteArray, dogID: String, isLost: Boolean) : String? =
        suspendCancellableCoroutine<String?> { cancellableContinuation ->
            var folderName : String = ""
            folderName = if (isLost) {
                "lostDogs"
            } else {
                "foundDogs"
            }
            // create a ref for the image
            val imageRef = storageRef.child("$folderName/${dogID}.jpg")
            val uploadTask = imageRef.putBytes(data)
            uploadTask
                .addOnSuccessListener { taskSnapshot ->
                    Log.i("upload image", "success")
                    var imageUri : String? = null
                    taskSnapshot.storage.downloadUrl.addOnSuccessListener { uri ->
                        Log.i("upload image", "got back url - $uri")
                        imageUri = uri.toString()
                        cancellableContinuation.resume(imageUri) {}
                    }
                }
                .addOnFailureListener { e ->
                    Log.i("upload image", "failure")
                    cancellableContinuation.resume(null) {}
                }
        }

    // here we retrieve the most updated
    // user firebase from Firestore, and immediately add the lost dog
    // info in it and send it to Firestore.
    private suspend fun updateUserLostDogFirebase(dog: DogFirebase) : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            coroutineScope.launch {
                val userDeferred = coroutineScope.async {
                    retrieveUserFirebase()
                }
                var user = userDeferred.await()
                if (user != null) {
                    user.lostDogs.put(dog.dogID, dog)
                    // save user here
                    if (saveUserFirebase(user)) {
                        Log.i("update user object for lost dog", "success")
                        cancellableContinuation.resume(true) {}
                    } else {
                        Log.i("update user object for lost dog", "failed, maybe server error")
                        cancellableContinuation.resume(false) {}
                    }
                } else {
                    Log.i("update lost dog in user object", "can't find the user in firebase")
                    cancellableContinuation.resume(false){}
                }
            }
        }

    // we write to the messaging collection to trigger the cloud function to process
    // the delievery of the message.  It needs to write in both the messagesReceived in
    // the target user, and the messagesSent in this user's object in firestore.
    suspend fun sendMessageToFirestoreMessaging(messageRoom: MessageRoom) : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            var data = HashMap<String, String>()
            data.put("senderEmail", messageRoom.senderEmail)
            data.put("senderName", messageRoom.senderName)
            data.put("targetEmail", messageRoom.targetEmail)
            data.put("targetName", messageRoom.targetName)
            data.put("messageID", messageRoom.messageID)
            data.put("message", messageRoom.messageContent)
            data.put("date", messageRoom.date)

            firestore
                .collection("messaging")
                .document()
                .set(data)
                .addOnSuccessListener { docRef ->
                    Log.i("send to Messaging", "success")
                    cancellableContinuation.resume(true) {}
                }
                .addOnFailureListener { e ->
                    Log.i("send to Messaging", "failed: ${e.message}")
                    cancellableContinuation.resume(false) {}
                }
    }

    suspend fun changePasswordFirebaseAuth() : Int =
        suspendCancellableCoroutine<Int> { cancellableContinuation ->
            auth.signInWithEmailAndPassword(auth.currentUser!!.email!!, currentPassword.value!!)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i("change password", "first step, sign in successful")
                        auth.currentUser!!.updatePassword(userPassword.value!!)
                            .addOnCompleteListener { updateTask ->
                                if (updateTask.isSuccessful) {
                                    Log.i("change password", "successfully updated password")
                                    cancellableContinuation.resume(1) {}
                                } else {
                                    Log.i("change password", "update password failed")
                                    cancellableContinuation.resume(2) {}
                                }
                            }
                    } else {
                        Log.i("change password", "first step, sign in failed")
                        cancellableContinuation.resume(0) {}
                    }
                }
        }

    suspend fun generatePasswordResetEmail(email: String) : Int =
        suspendCancellableCoroutine<Int> { cancellableContinuation ->
        if (isEmailValid(email)) {
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i("password reset email", "success")
                        cancellableContinuation.resume(1) {}
                    } else {
                        Log.i("password reset email", "failed")
                        cancellableContinuation.resume(2) {}
                    }
                }
        } else if (!isEmailValid(email)) {
            cancellableContinuation.resume(0) {}
        }
    }

    // to delete a report, need to go to either lost or found collection
    // also need to update the user's lost dogs array to remove it
    // also need to update local database
    suspend fun processDeleteReport(pet: DogRoom) : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            coroutineScope.launch {
                var resultCollectionDeferred = coroutineScope.async { deleteReportInCollection(pet) }
                var resultUpdateUserDeferred = coroutineScope.async { deletePetUserFirebase(pet) }
                var resultInCollection = resultCollectionDeferred.await()
                var resultUpdateUser = resultUpdateUserDeferred.await()
                deletePetLocalDatabase(pet)
                deletePetUserLocal(pet)
                cancellableContinuation.resume(resultInCollection && resultUpdateUser) {}
            }
        }



    private suspend fun deleteReportInCollection(pet: DogRoom) : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            var collectionName = ""
            if (pet.isLost!!) {
                collectionName = "lostDogs"
            } else {
                collectionName = "foundDogs"
            }
            firestore
                .collection(collectionName)
                .document(pet.dogID)
                .delete()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i("delete pet from collection", "success")
                        cancellableContinuation.resume(true) {}
                    } else {
                        Log.i("delete pet from collection", "failed")
                        cancellableContinuation.resume(false) {}
                    }
                }
        }

    private suspend fun deletePetUserFirebase(pet: DogRoom) : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            firestore
                .collection("users")
                .document(pet.ownerID)
                .get()
                .addOnSuccessListener { docSnapshot ->
                    if (docSnapshot.exists()) {
                        Log.i("delete pet from user", "got user object")
                        var user = docSnapshot.toObject(UserFirebase::class.java)!!
                        user.lostDogs.remove(pet.dogID)
                        // save user in firebase
                        coroutineScope.launch {
                            if (saveUserFirebase(user)) {
                                Log.i("delete pet from user", "updated user")
                                cancellableContinuation.resume(true) {}
                            } else {
                                Log.i("delete pet from user", "couldn't save user")
                                cancellableContinuation.resume(false) {}
                            }
                        }
                        // save user in room
                    } else {
                        Log.i("delete pet from user", "failed to get user object")
                        cancellableContinuation.resume(false) {}
                    }
                }
        }

    private fun deletePetLocalDatabase(pet: DogRoom) {
        localDatabase.pawsDAO.deleteDogs(pet)
    }

    private fun deletePetUserLocal(petRoom: DogRoom) {
        val pet = currentUserRoomLiveData.value?.lostDogs!!.find { pet -> pet.dogID == petRoom.dogID }
        var petList = ArrayList<DogRoom>()
        if (pet != null) {
            petList = currentUserRoomLiveData.value!!.lostDogs as ArrayList
        }
        petList.remove(pet)
        val user = currentUserRoomLiveData.value!!
        user.lostDogs = petList
        // save user
        coroutineScope.launch {
            localDatabase.pawsDAO.insertUser(user)
        }
    }
}

class FirebaseClientViewModelFactory(private val application: Application)
    : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FirebaseClientViewModel::class.java)) {
            return FirebaseClientViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
/*
    private fun saveBitmapExternal(dogBitmap: Bitmap, dogName: String) {
        try {
            val root: File? = getApplication<Application>()
                .applicationContext
                .getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val storageDir = File(root.toString() + "/paws_go_images")
            storageDir.mkdirs()
            val fname: String = Date().toString() + dogName + ".jpg"
            val file: File = File(storageDir, fname)
            val out = FileOutputStream(file)
            dogBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            out.flush()
            out.close()
            Log.i("save bitmap", "should have saved image")
            val fileUriString = file.toURI().toString()

        } catch (e: java.lang.Exception) {
            Log.i("save bitmap", "error")
        }
    }
    private fun <T: Any> convertMapToList(hashMap: HashMap<String, T>, type: Int) : List<T> {
        val list = ArrayList<T>()
        for ((key, value) in hashMap) {
            list.add(convertFirebaseModelToRoomModel(value, type))
        }
        return list
    }
    private fun <T: Any> convertFirebaseModelToRoomModel(item: T, type: Int) : T {
        if (type == 1) {
            return convertDogFirebaseToDogRoom(item as DogFirebase) as T
        } else if (type == 2) {
            return item
        } else {
            return item
        }
    }
 */