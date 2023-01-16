package com.bitpunchlab.android.pawsgo.reportLostDog

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.bitpunchlab.android.pawsgo.AppState
import com.bitpunchlab.android.pawsgo.R
import com.bitpunchlab.android.pawsgo.databinding.FragmentEditReportBinding
import com.bitpunchlab.android.pawsgo.dogsDisplay.DogsViewModel
import com.bitpunchlab.android.pawsgo.dogsDisplay.DogsViewModelFactory
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModel
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModelFactory
import com.bitpunchlab.android.pawsgo.location.LocationViewModel
import com.bitpunchlab.android.pawsgo.modelsRoom.DogRoom
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.HashMap


class EditReportFragment : Fragment() {

    private var _binding : FragmentEditReportBinding? = null
    private val binding get() = _binding!!
    private lateinit var firebaseClient : FirebaseClientViewModel
    private var pet : DogRoom? = null
    private lateinit var locationViewModel : LocationViewModel
    private lateinit var dogsViewModel : DogsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentEditReportBinding.inflate(inflater, container, false)
        firebaseClient = ViewModelProvider(requireActivity(),
            FirebaseClientViewModelFactory(requireActivity().application))
            .get(FirebaseClientViewModel::class.java)
        locationViewModel = ViewModelProvider(requireActivity()).get(LocationViewModel::class.java)
        dogsViewModel = ViewModelProvider(requireActivity(), DogsViewModelFactory(requireActivity().application))
            .get(DogsViewModel::class.java)
        pet = requireArguments().getParcelable<DogRoom>("pet")

        setupPetFormFragment()
        binding.lifecycleOwner = viewLifecycleOwner
        // prepare the old lat lng info, if it is not renewed, it will be saved again in new update
        if (pet!!.locationLat != null) {
            locationViewModel.lostDogLocationLatLng.value =
                LatLng(pet!!.locationLat!!, pet!!.locationLng!!)
        }

        locationViewModel.shouldNavigateChooseLocation.observe(viewLifecycleOwner, Observer { should ->
            Log.i("should navigate", should.toString())
            should?.let {
                if (should) {
                    Log.i("edit report", "navigating")
                    findNavController().navigate(R.id.chooseLocationAction)
                    // reset
                    locationViewModel.shouldNavigateChooseLocation.value = false
                }
            }
        })

        dogsViewModel.readyProcessReport.observe(viewLifecycleOwner, Observer { ready ->
            ready?.let {
                if (ready) {
                    processUpdateReport()
                    startProgressBar()
                    dogsViewModel.readyProcessReport.value = false
                }
            }
        })

        dogsViewModel.shouldDeleteReport.observe(viewLifecycleOwner, Observer { should ->
            should?.let {
                if (should) {
                    startProgressBar()
                    Log.i("edit report", "should delete report detected")
                    CoroutineScope(Dispatchers.IO).launch {
                        if (firebaseClient.processDeleteReport(pet!!)) {
                            firebaseClient._appState.postValue(AppState.DELETE_REPORT_SUCCESS)
                        } else {
                            firebaseClient._appState.postValue(AppState.DELETE_REPORT_ERROR)
                        }
                    }
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

    private fun setupPetFormFragment() {
        val petFormFragment = PetFormFragment()
        val bundle = Bundle()
        bundle.putParcelable("pet", pet)
        bundle.putBoolean("lostOrFound", pet!!.isLost!!)
        petFormFragment.arguments = bundle
        val fragmentTransaction = childFragmentManager.beginTransaction()
        fragmentTransaction.add(R.id.editFragmentContainer, petFormFragment)
        fragmentTransaction.commit()
    }

    private fun processUpdateReport() {
        // general data is verified in pet form
        // we get all the info that we prefilled and user changed in the liva data variables
        // create a new object with the same dogID, and new info
        var name = ""
        if (dogsViewModel.petName.value != null) {
            name = dogsViewModel.petName.value!!
        }
        var gender = 0
        if (dogsViewModel.petGender.value != null) {
            gender = dogsViewModel.petGender.value!!
        }
        var date = ""
        if (dogsViewModel.dateLastSeen.value != null) {
            date = dogsViewModel.dateLastSeen.value!!
        }
        var place = ""
        if (dogsViewModel.placeLastSeen.value != null) {
            place = dogsViewModel.placeLastSeen.value!!
        }


        val updatePet = createDogRoom(id = pet!!.dogID, name = name, animal = dogsViewModel.petType.value,
        gender = gender, age = dogsViewModel.petAge.value, breed = dogsViewModel.petBreed.value,
        date = date, hour = dogsViewModel.lostHour.value, minute = dogsViewModel.lostMinute.value,
        place = place, note = dogsViewModel.petNotes.value, lost = pet!!.isLost!!, lat = locationViewModel.lostDogLocationLatLng.value?.latitude,
        lng = locationViewModel.lostDogLocationLatLng.value?.longitude, address = place, found = false)

        // reset locationVM address and latlng
        Log.i("edit report", "update pet ${updatePet}")

        // we need to pass the old image into the new object first
        // if there is image byte array, the new image url will replace the new one
        // if there is no image byte array, the old image url will pass to the updated object
        updatePet.dogImages = pet!!.dogImages

        // reset locationVM, latlng and address,
        // so next time user clicks in it, won't get the old info
        locationViewModel.lostDogLocationLatLng.value = null
        locationViewModel.lostDogLocationAddress.value = null


        CoroutineScope(Dispatchers.IO).launch {
            // so, here, if we sure the picture is a new picture in tempImageByteArray
            // (the uploadClicked and choosedpicture are true)
            // we pass the image to processDogReport, else, we pass null
            var picturePassed : ByteArray? = null
            if (dogsViewModel.tempImageByteArray != null && dogsViewModel.uploadClicked &&
                    dogsViewModel.choosedPicture) {
                picturePassed = dogsViewModel.tempImageByteArray
            }
            if (firebaseClient.processDogReport(updatePet, picturePassed)) {
                // alert success
                firebaseClient._appState.postValue(AppState.LOST_DOG_REPORT_SENT_SUCCESS)
            } else {
                // alert failure
                firebaseClient._appState.postValue(AppState.LOST_DOG_REPORT_SENT_ERROR)
            }

        }
    }

    private val appStateObserver = androidx.lifecycle.Observer<AppState> { appState ->
        when (appState) {
            AppState.LOST_DOG_REPORT_SENT_SUCCESS -> {
                stopProgressBar()
                updateReportSuccessAlert()
                firebaseClient._appState.value = AppState.NORMAL
            }
            AppState.LOST_DOG_REPORT_SENT_ERROR -> {
                stopProgressBar()
                updateReportFailureAlert()
                firebaseClient._appState.value = AppState.NORMAL
            }
            AppState.DELETE_REPORT_SUCCESS -> {
                stopProgressBar()
                deleteReportSuccessAlert()
            }
            AppState.DELETE_REPORT_ERROR -> {
                stopProgressBar()
                deleteReportFailureAlert()
            }
            else -> {
                stopProgressBar()
            }
        }
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

    private fun updateReportSuccessAlert() {
        val successAlert = AlertDialog.Builder(context)

        with(successAlert) {
            setTitle(getString(R.string.pet_report_update))
            setMessage(getString(R.string.update_report_success_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    dialog.dismiss()
                })
            show()
        }
    }

    private fun updateReportFailureAlert() {
        val failureAlert = AlertDialog.Builder(context)

        with(failureAlert) {
            setTitle(getString(R.string.pet_report_update))
            setMessage(getString(R.string.lost_report_failure_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    dialog.dismiss()
                })
            show()
        }
    }

    private fun deleteReportSuccessAlert() {
        val successAlert = AlertDialog.Builder(context)

        with(successAlert) {
            setTitle(getString(R.string.delete_report_alert))
            setMessage(getString(R.string.delete_report_success_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    dialog.dismiss()
                })
            show()
        }
    }

    private fun deleteReportFailureAlert() {
        val failureAlert = AlertDialog.Builder(context)

        with(failureAlert) {
            setTitle(getString(R.string.delete_report_alert))
            setMessage(getString(R.string.delete_report_failure_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    dialog.dismiss()
                })
            show()
        }
    }
}