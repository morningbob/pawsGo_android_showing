package com.bitpunchlab.android.pawsgo.location

import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.bitpunchlab.android.pawsgo.BuildConfig.MAPS_API_KEY
import com.bitpunchlab.android.pawsgo.R
import com.bitpunchlab.android.pawsgo.databinding.FragmentChooseLocationBinding
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import kotlinx.coroutines.*
import java.io.IOException
import java.util.*


class ChooseLocationFragment : Fragment() {

    private var _binding : FragmentChooseLocationBinding? = null
    private val binding get() = _binding!!
    private lateinit var autoCompleteSupportFragment : AutocompleteSupportFragment
    private lateinit var locationViewModel: LocationViewModel
    private lateinit var geoCoder : Geocoder
    private var coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentChooseLocationBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        locationViewModel = ViewModelProvider(requireActivity())
            .get(LocationViewModel::class.java)
        // GeoCoder API is used to convert coordinates to addresses
        // I need it to make a description of the lost dogs location
        // of course, everything still base on the information in the marker of the map
        geoCoder = Geocoder(requireContext(), Locale.getDefault())

        //insertMapFragment()
        arrangeMapFragment()
        setupAutoCompleteFragment()

        binding.buttonSetLocation.setOnClickListener {
            // the place the marker is in, is the most current place user clicked
            // we need to get more info of the current place
            // like street name and number
            // save the point in Firebase
            // show to users when they are viewing the lost dog

            // display progress bar, it is going to take some time
            startProgressBar()

            locationViewModel.lostDogLocationLatLng.value = LatLng(
                locationViewModel.placeMarker?.position?.latitude!!,
                locationViewModel.placeMarker?.position?.longitude!!)
            if (locationViewModel.lostDogLocationLatLng.value != null) {
                coroutineScope.launch {
                    locationViewModel.lostDogLocationAddress.postValue(
                        searchPlaceAddress(locationViewModel.lostDogLocationLatLng.value!!))
                    withContext(Dispatchers.Main) {
                        // dismiss progress bar
                        stopProgressBar()
                        // reset
                        locationViewModel.shouldNavigateChooseLocation.value = false
                        // pop back stack doesn't work
                        Log.i("pop back stack", findNavController().popBackStack().toString())

                    }
                }
            } else {
                findNavController().popBackStack()
            }
        }

        binding.buttonCancel.setOnClickListener {
            // reset location
            locationViewModel.lostDogLocationLatLng.value = null
            locationViewModel.lostDogLocationAddress.value = null
            findNavController().popBackStack()
            //findNavController().navigate(R.id.action_chooseLocationFragment_to_editReportFragment)
        }
        return binding.root
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun arrangeMapFragment() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map)
        if (mapFragment != null) {
            val transaction = childFragmentManager.beginTransaction()
            transaction.replace(R.id.map_fragment_container, mapFragment).commit()
        } else {
            insertMapFragment()
        }
    }

    private fun insertMapFragment() {
        val mapFragment = MapFragment()
        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(R.id.map_fragment_container, mapFragment).commit()
    }

    private fun setupAutoCompleteFragment() {
        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), MAPS_API_KEY)
        }

        autoCompleteSupportFragment = childFragmentManager.findFragmentById(R.id.autocomplete_fragment)
                as AutocompleteSupportFragment

        autoCompleteSupportFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME,
            Place.Field.LAT_LNG))

        autoCompleteSupportFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onError(status: Status) {
                Log.i("auto complete fragment setup", "there is error: $status")
            }

            override fun onPlaceSelected(place: Place) {
                Log.i("auto complete fragment setup", "Place: ${place.id} ${place.name}")
                locationViewModel.navigateToPlace.value = place.latLng
            }

        })
    }

    // get from location is deprecated, replaced with a listener to listen for call back
    // but it need 33 to implement this method

    private suspend fun searchPlaceAddress(placeLatLng: LatLng) : List<String> {
        var address = ""
        var city = ""
        //val addressList = geoCoder.getFromLocation(placeLatLng.latitude, placeLatLng.longitude, 1)
        return withContext(Dispatchers.IO) {
            val addressListDeferred = coroutineScope.async {
                //try {
                    geoCoder.getFromLocation(placeLatLng.latitude, placeLatLng.longitude, 1)
                        //} catch (e: IOException){
                    //Log.i("choose location", "can't get address from geocoder")
                //}
            }
            val addressList = addressListDeferred.await()
            if (!addressList.isNullOrEmpty()) {
                addressList.get(0)?.getAddressLine(0)?.let {
                    address = it
                    Log.i("search place address", "address : $address")
                }
                city = addressList.get(0).locality
                Log.i("search place address", "city: $city")

            }
            return@withContext listOf(address, city)
        }
    }

    override fun onResume() {
        super.onResume()
        //insertMapFragment()

    }
}
