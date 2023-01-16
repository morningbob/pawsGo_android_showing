package com.bitpunchlab.android.pawsgo.reportLostDog

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.navigation.fragment.findNavController
import androidx.room.InvalidationTracker
import com.bitpunchlab.android.pawsgo.AppState
import com.bitpunchlab.android.pawsgo.R
import com.bitpunchlab.android.pawsgo.database.PawsGoDatabase
import com.bitpunchlab.android.pawsgo.databinding.FragmentReportLostDogBinding
import com.bitpunchlab.android.pawsgo.dogsDisplay.DogsViewModel
import com.bitpunchlab.android.pawsgo.dogsDisplay.DogsViewModelFactory
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModel
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModelFactory
import com.bitpunchlab.android.pawsgo.location.LocationViewModel
import com.bitpunchlab.android.pawsgo.modelsRoom.DogRoom
import com.google.android.gms.tasks.Task
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.firebase.database.collection.LLRBNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class ReportLostDogFragment : Fragment() {

    private var _binding : FragmentReportLostDogBinding? = null
    private val binding get() = _binding!!
    private lateinit var firebaseClient : FirebaseClientViewModel
    private var timePicker : MaterialTimePicker? = null
    private var datePicker : MaterialDatePicker<Long>? = null
    private var lostDate : String? = null
    private var lostHour : Int? = null
    private var lostMinute : Int? = null
    private var gender : Int = 0
    private var animalType : String? = null
    //private var allPermissionGranted = MutableLiveData<Boolean>(true)
    private var coroutineScope = CoroutineScope(Dispatchers.IO)
    private lateinit var localDatabase : PawsGoDatabase
    private var lostOrFound : Boolean? = null
    private lateinit var locationViewModel : LocationViewModel
    val ONE_DAY_IN_MILLIS = 86400000
    private var isPermissionGranted = false
    private lateinit var dogsViewModel : DogsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentReportLostDogBinding.inflate(inflater, container, false)
        lostOrFound = requireArguments().getBoolean("lostOrFound")
        firebaseClient = ViewModelProvider(requireActivity(),
            FirebaseClientViewModelFactory(requireActivity().application))
            .get(FirebaseClientViewModel::class.java)
        dogsViewModel = ViewModelProvider(requireActivity(), DogsViewModelFactory(requireActivity().application))
            .get(DogsViewModel::class.java)
        binding.lifecycleOwner = viewLifecycleOwner
        localDatabase = PawsGoDatabase.getInstance(requireContext())
        locationViewModel = ViewModelProvider(requireActivity())
            .get(LocationViewModel::class.java)

        setupPetFormFragment()

        binding.locationVM = locationViewModel

        dogsViewModel.tempPet.observe(viewLifecycleOwner, androidx.lifecycle.Observer { pet ->
            pet?.let {
                Log.i("report fragment", "pet ${pet.dogName}")
            }
        })

        dogsViewModel.readyProcessReport.observe(viewLifecycleOwner, androidx.lifecycle.Observer { ready ->
            if (ready) {
                processReportPet()
                startProgressBar()
                dogsViewModel.readyProcessReport.value = false
            }
        })

        locationViewModel.shouldNavigateChooseLocation.observe(viewLifecycleOwner, androidx.lifecycle.Observer { should ->
            should?.let {
                if (should) {
                    findNavController().navigate(R.id.showMapAction)
                    locationViewModel.shouldNavigateChooseLocation.value = false
                }
            }
        })

        firebaseClient.appState.observe(viewLifecycleOwner, appStateObserver)

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun startProgressBar() {
        binding.progressBar.visibility = View.VISIBLE

        requireActivity().window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private fun stopProgressBar() {
        binding.progressBar.visibility = View.GONE
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private val appStateObserver = androidx.lifecycle.Observer<AppState> { appState ->
        when (appState) {
            AppState.LOST_DOG_REPORT_SENT_SUCCESS -> {
                stopProgressBar()
                sentReportSuccessAlert()
                firebaseClient._appState.value = AppState.NORMAL
            }
            AppState.LOST_DOG_REPORT_SENT_ERROR -> {
                stopProgressBar()
                sentReportFailureAlert()
                firebaseClient._appState.value = AppState.NORMAL
            }
            else -> {
                stopProgressBar()
            }
        }
    }

    private fun setupPetFormFragment() {
        val petFormFragment = PetFormFragment()
        val bundle = Bundle()
        bundle.putParcelable("pet", null)
        bundle.putBoolean("lostOrFound", lostOrFound!!)
        petFormFragment.arguments = bundle
        val fragmentTransaction = childFragmentManager.beginTransaction()
        fragmentTransaction.add(R.id.petFormContainerReport, petFormFragment)
        fragmentTransaction.commit()
    }

    private fun processReportPet() {
        Log.i("pet name", "name " + dogsViewModel.petName.value)
        Log.i("pet breed", "breed " + dogsViewModel.petBreed.value)
        Log.i("pet type", "type " + dogsViewModel.petType.value)
        Log.i("pet gender", "gender " + dogsViewModel.petGender.value)
        Log.i("pet age", "age " + dogsViewModel.petAge.value)
        Log.i("lost date", "date " + dogsViewModel.dateLastSeen)
        Log.i("lost time", "time " + dogsViewModel.lostHour.value + ":" + dogsViewModel.lostMinute.value)
        Log.i("lost place", "place " + dogsViewModel.placeLastSeen.value)
        Log.i("notes", "notes " + dogsViewModel.petNotes.value)

        var gender = 0
        if (dogsViewModel.petGender.value != null) {
            gender = dogsViewModel.petGender.value!!
        }
        var age : Int? = null
        if (dogsViewModel.petAge.value != null) {
            age = dogsViewModel.petAge.value
        }

        val dogRoom = createDogRoom(
            id = UUID.randomUUID().toString(),
            name = dogsViewModel.petName.value!!,
            animal = dogsViewModel.petType.value,
            breed = dogsViewModel.petBreed.value,
            gender = gender,
            age = age,
            date = dogsViewModel.dateLastSeen.value!!,
            hour = dogsViewModel.lostHour.value,
            minute = dogsViewModel.lostMinute.value,
            note = dogsViewModel.petNotes.value,
            place = dogsViewModel.placeLastSeen.value!!,
            lost = lostOrFound!!,
            found = false,
            lat = locationViewModel.lostDogLocationLatLng.value?.latitude,
            lng = locationViewModel.lostDogLocationLatLng.value?.longitude,
            address = locationViewModel.lostDogLocationAddress.value?.get(0)
        )

        // reset locationVM, latlng and address,
        // so next time user clicks in it, won't get the old info
        locationViewModel.lostDogLocationLatLng.value = null
        locationViewModel.lostDogLocationAddress.value = null

        coroutineScope.launch {
            //dogsViewModel.tempPet.value!!.dogID = UUID.randomUUID().toString()
            if (firebaseClient.processDogReport(
                dogRoom,
                dogsViewModel.tempImageByteArray)) {
                    //sentReportSuccessAlert()
                    firebaseClient._appState.postValue(AppState.LOST_DOG_REPORT_SENT_SUCCESS)

                } else {
                    //sentReportFailureAlert()
                    firebaseClient._appState.postValue(AppState.LOST_DOG_REPORT_SENT_ERROR)
            }
        }
    }
/*


    private fun processReportInputs() {
        var processedAge : Int? = null
        if (binding.edittextDogAge.text != null && binding.edittextDogAge.text.toString() != "") {
            try {
                processedAge = binding.edittextDogAge.text.toString().toInt()
            } catch (e: java.lang.NumberFormatException) {
                Log.i("processing dog age", "error converting to number")
            }
        }
        // we know we need to get the type from the edittext
        if (animalType == "Other") {
            val type = binding.edittextOtherType.text.toString()
            if (type != null && type != "") {
                animalType = type
            }
        }

        if (verifyLostDogData(binding.edittextDogName.text.toString(),
                lostDate,
                binding.edittextPlaceLost.text.toString())) {
            val dogRoom = createDogRoom(
                name = binding.edittextDogName.text.toString(),
                animal = animalType,
                breed = binding.edittextDogBreed.text.toString(),
                gender = gender,
                age = processedAge,
                date = lostDate!!,
                hour = lostHour,
                minute = lostMinute,
                note = binding.edittextNotes.text.toString(),
                place = binding.edittextPlaceLost.text.toString(),
                lost = lostOrFound!!,
                found = !lostOrFound!!,
                lat = locationViewModel.lostDogLocationLatLng.value?.latitude,
                lng = locationViewModel.lostDogLocationLatLng.value?.longitude,
                address = locationViewModel.lostDogLocationAddress.value?.get(0))

            // check if imageview is empty
            // if it is not, save the image to cloud storage
            var dataByteArray : ByteArray? = null
            if (binding.previewUpload.drawable != null) {
                Log.i("check image", "image is not null")
                val imageBitmap = getBitmapFromView(binding.previewUpload)
                dataByteArray = convertImageToBytes(imageBitmap)
            }
            coroutineScope.launch {
                if (firebaseClient.handleNewDog(dogRoom, dataByteArray)) {
                    firebaseClient._appState.postValue(AppState.LOST_DOG_REPORT_SENT_SUCCESS)
                } else {
                    firebaseClient._appState.postValue(AppState.LOST_DOG_REPORT_SENT_ERROR) }
            }
            clearForm()
        } else {
            invalidDogDataAlert()
        }
    }

    private fun setupLostOrFoundFields() {
        if (lostOrFound == false) {
            binding.artReport.setImageResource(R.drawable.footprint)
            binding.textviewIntro.text = getString(R.string.found_dog_intro)
            binding.textviewDogAge.visibility = View.GONE
            binding.edittextDogAge.visibility = View.GONE
            binding.textviewDateLost.text = "When did you find the pet?"
            binding.textviewPlaceLost.text = "The place the pet is found: "
        }
    }

    private val selectImageFromGalleryResult =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                // preview
                binding.previewUpload.setImageURI(uri)
                binding.previewUpload.visibility = View.VISIBLE
            }
        }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val choice = parent!!.getItemAtPosition(position)
        when (choice) {
            "Male" -> {
                gender = 1
                Log.i("spinner", "set gender male")
            }
            "Female" -> {
                gender = 2
                Log.i("spinner", "set gender female")
            }
            "Dog" -> {
                animalType = "Dog"
                Log.i("spinner", "set type dog")
            }
            "Cat" -> {
                animalType = "Cat"
                Log.i("spinner", "set type cat")
            }
            "Bird" -> {
                animalType = "Bird"
                Log.i("spinner", "set type bird")
            }
            "Other" -> {
                animalType = "Other"
                Log.i("spinner", "set type other")
            }
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {

    }

    private fun setupGenderSpinner() {
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.gender_array,
            R.layout.spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            binding.genderSpinner.adapter = adapter
        }

        binding.genderSpinner.onItemSelectedListener= this
    }

    private fun setupPetSpinner() {
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.animalType_array,
            R.layout.spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            binding.petSpinner!!.adapter = adapter
        }

        binding.petSpinner!!.onItemSelectedListener= this
    }

    private fun showDatePicker() {
        val today = MaterialDatePicker.todayInUtcMilliseconds() + ONE_DAY_IN_MILLIS
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ENGLISH)

        calendar.timeInMillis = today
        calendar[Calendar.YEAR] = 2012
        val startDate = calendar.timeInMillis

        calendar.timeInMillis = today
        //calendar[Calendar.YEAR] = 2022
        val endDate = calendar.timeInMillis

        val constraints: CalendarConstraints = CalendarConstraints.Builder()
            .setOpenAt(endDate)
            .setStart(startDate)
            .setEnd(endDate)
            .build()

        datePicker = MaterialDatePicker
            .Builder
            .datePicker()
            .setCalendarConstraints(constraints)
            .setTitleText(getString(R.string.select_a_date))
            .build()

        datePicker!!.show(childFragmentManager, "DATE_PICKER")

        datePicker!!.addOnPositiveButtonClickListener { data ->
            if (data < today) {
                val simpleDate = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)

                val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                utc.timeInMillis = data
                val dayOfMonth = utc.get(Calendar.DAY_OF_MONTH)
                val month = utc.get(Calendar.MONTH)
                val year = utc.get(Calendar.YEAR)
                // then, we put the day, month and year to calendar
                val calendarChosen = Calendar.getInstance()
                calendarChosen.set(year, month, dayOfMonth)

                lostDate = simpleDate.format(calendarChosen.time)
                //Log.i("got back date", "day: $dayOfMonth, month: $month, year: $year")
                Log.i("got back date", lostDate.toString())
                binding.textviewDateLostData.text = lostDate
                binding.textviewDateLostData.visibility = View.VISIBLE
            } else {
                coroutineScope.launch(Dispatchers.Main) {
                    invalidDateAlert()
                }
            }
        }
    }

    private fun showTimePicker() {
        timePicker = MaterialTimePicker
            .Builder()
            .setTitleText(getString(R.string.select_a_time))
            .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
            .build()

        timePicker!!.show(childFragmentManager, "TIME_PICKER")

        timePicker!!.addOnPositiveButtonClickListener {
            lostHour = timePicker!!.hour
            lostMinute = timePicker!!.minute
            Log.i("got time back", "hour: ${lostHour.toString()} minute: ${lostMinute.toString()}")
            binding.textviewTimeLostData.text = "$lostHour hour and $lostMinute minutes"
            binding.textviewTimeLostData.visibility = View.VISIBLE
        }
    }

    private var permissions =
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            )

    private val permissionResultLauncher =
        registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
            var result = true
            //allPermissionGranted = true
            permissions.entries.forEach {
                if (!it.value) {
                    Log.e("result launcher", "Permission ${it.key} not granted : ${it.value}")
                    //allPermissionGranted.value = false
                    result = false
                }
            }
            isPermissionGranted = result
        }

    private fun checkPermission() : Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) !=
                PackageManager.PERMISSION_GRANTED) return false
        }
        return true
    }

    private fun verifyLostDogData(name: String?, date: String?, place: String?) : Boolean {
        var nameValidity = true
        var dateValidity = true
        var placeValidity = true
        if (!(name != null && name != "") && lostOrFound == true) {
            nameValidity = false
            binding.textviewDogName.setTextColor(resources.getColor(R.color.error_red))
        } else {
            binding.textviewDogName.setTextColor(resources.getColor(R.color.black))
        }
        if (!(date != null && date != "")) {
            binding.textviewDateLost.setTextColor(resources.getColor(R.color.error_red))
            dateValidity = false
        } else {
            binding.textviewDateLost.setTextColor(resources.getColor(R.color.black))
        }
        if (!(place != null && place != "")) {
            binding.textviewPlaceLost.setTextColor(resources.getColor(R.color.error_red))
            placeValidity = false
        } else {
            binding.textviewPlaceLost.setTextColor(resources.getColor(R.color.black))
        }
        return nameValidity && dateValidity && placeValidity
    }
*/
    private fun createDogRoom(id: String, name: String, animal: String?, breed: String?,
                              gender: Int, age: Int?, date: String,
                        hour: Int?, minute: Int?, note: String?, place: String, lost: Boolean, found: Boolean,
                        lat: Double?, lng: Double?, address: String?): DogRoom {
        return DogRoom(dogID = id, dogName = name,
            animalType = animal, dogBreed = breed,
            dogGender = gender, dogAge = age, isLost = lost, isFound = found,
            dateLastSeen = date, hour = hour, minute = minute, notes = note,
            placeLastSeen = place, ownerID = firebaseClient.currentUserID,
            ownerEmail = firebaseClient.currentUserEmail,
            ownerName = firebaseClient.currentUserFirebaseLiveData.value!!.userName,
            locationLat = lat, locationLng = lng,
            locationAddress = address)
    }
/*
    private fun getBitmapFromView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(
            view.width, view.height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun convertImageToBytes(bitmap: Bitmap) : ByteArray {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        return baos.toByteArray()
    }

    private fun clearForm() {
        binding.edittextDogName.text = null
        binding.edittextDogBreed.text = null
        binding.edittextDogAge.text = null
        binding.textviewDateLostData.text = null
        binding.textviewDateLostData.visibility = View.GONE
        binding.textviewTimeLostData.text = null
        binding.textviewTimeLostData.visibility = View.GONE
        binding.edittextPlaceLost.text = null
        binding.previewUpload.visibility = View.GONE
        binding.edittextNotes.text = null
        lostDate = null
        lostHour = null
        lostMinute = null
        gender = 0
        setupGenderSpinner()
    }

 */

    private fun permissionsRequiredAlert() {
        val permissionAlert = AlertDialog.Builder(context)

        with(permissionAlert) {
            setTitle(getString(R.string.location_permissions_alert))
            setMessage(getString(R.string.location_permissions_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    dialog.dismiss()
                    //permissionResultLauncher.launch(permissions)

                })
            //setNegativeButton(getString(R.string.cancel),
            //    DialogInterface.OnClickListener { dialog, button ->
            //        dialog.dismiss()
            //    })
            show()
        }
    }

    private fun invalidDogDataAlert() {
        val invalidAlert = AlertDialog.Builder(context)

        with(invalidAlert) {
            setTitle(getString(R.string.missing_data_alert))
            setMessage(getString(R.string.missing_data_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    dialog.dismiss()
                })
            show()
        }
    }

    private fun sentReportSuccessAlert() {
        val successAlert = AlertDialog.Builder(context)

        with(successAlert) {
            setTitle(getString(R.string.lost_dog_report))
            setMessage(getString(R.string.lost_report_success_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    dialog.dismiss()
                })
            show()
        }
    }

    private fun sentReportFailureAlert() {
        val failureAlert = AlertDialog.Builder(context)

        with(failureAlert) {
            setTitle(getString(R.string.lost_dog_report))
            setMessage(getString(R.string.lost_report_failure_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    dialog.dismiss()
                })
            show()
        }
    }

    private fun invalidDateAlert() {
        val dateAlert = AlertDialog.Builder(context)

        with(dateAlert) {
            setTitle(getString(R.string.lost_dog_report))
            setMessage(getString(R.string.invalid_date_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    dialog.dismiss()
                })
            show()
        }
    }

    private fun permissionsNeededAlert() {
        val permissionAlert = AlertDialog.Builder(context)

        with(permissionAlert) {
            setTitle(getString(R.string.location_permissions))
            setMessage(getString(R.string.location_permissions_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    dialog.dismiss()
                })
            show()
        }
    }
}
/*
// we turn the data's millis into utc time zone to get the day, month
// and year
val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
utc.timeInMillis = data
val dayOfMonth = utc.get(Calendar.DAY_OF_MONTH)
val month = utc.get(Calendar.MONTH) + 1
val year = utc.get(Calendar.YEAR)
// then, we put the day, month and year to calendar
//val calendar = Calendar.getInstance()
//calendar.set(year, month, dayOfMonth)

allPermissionGranted.observe(viewLifecycleOwner, androidx.lifecycle.Observer { value ->
            if (!value) {
        //        permissionsRequiredAlert()
            }
        })

if (!checkPermission()) {
            permissionResultLauncher.launch(permissions)
        } else {
            isPermissionGranted = true
        }
        //setupLostOrFoundFields()
        //setupGenderSpinner()
        //setupPetSpinner()

        binding.buttonChooseDate.setOnClickListener {
            showDatePicker()
        }

        binding.buttonChooseTime.setOnClickListener {
            showTimePicker()
        }

        binding.buttonShowMap.setOnClickListener {
            if (!isPermissionGranted) {
                permissionsNeededAlert()
            } else {
                findNavController().navigate(R.id.showMapAction)
            }
        }

        binding.buttonUpload.setOnClickListener {
            selectImageFromGalleryResult.launch("image/*")
        }

        binding.buttonSend.setOnClickListener {
            // display progress bar
            startProgressBar()
            processReportInputs()
        }

        firebaseClient.appState.observe(viewLifecycleOwner, appStateObserver)

        locationViewModel.lostDogLocationAddress.observe(viewLifecycleOwner, androidx.lifecycle.Observer { address ->
            locationViewModel.displayAddress.value = address?.get(0) ?: ""
        })

/*
        dogsViewModel.petName.observe(viewLifecycleOwner, androidx.lifecycle.Observer { name ->
            Log.i("name", name)
        })
        dogsViewModel.petType.observe(viewLifecycleOwner, androidx.lifecycle.Observer { type ->
            Log.i("type", type)
        })
        dogsViewModel.petBreed.observe(viewLifecycleOwner, androidx.lifecycle.Observer { breed ->
            Log.i("breed", breed)
        })
        dogsViewModel.petAge.observe(viewLifecycleOwner, androidx.lifecycle.Observer { age ->
            Log.i("age", age.toString())
        })
        dogsViewModel.petGender.observe(viewLifecycleOwner, androidx.lifecycle.Observer { gender ->
            Log.i("gender", gender.toString())
        })
        dogsViewModel.dateLastSeen.observe(viewLifecycleOwner, androidx.lifecycle.Observer { date ->
            Log.i("date", date)
        })
        dogsViewModel.lostHour.observe(viewLifecycleOwner, androidx.lifecycle.Observer { hour ->
            Log.i("time", hour.toString())
        })
        dogsViewModel.placeLastSeen.observe(viewLifecycleOwner, androidx.lifecycle.Observer { place ->
            Log.i("place", place)
        })
        dogsViewModel.petNotes.observe(viewLifecycleOwner, androidx.lifecycle.Observer { notes ->
            Log.i("notes", notes)
        })
*/
 */