package com.bitpunchlab.android.pawsgo.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.bitpunchlab.android.pawsgo.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMapClickListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.*


class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var supportMapFragment: SupportMapFragment
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationProviderClient : FusedLocationProviderClient
    private var coroutineScope = CoroutineScope(Dispatchers.IO)
    private lateinit var locationViewModel : LocationViewModel
    private var deviceLocation : Location? = null
    private var onMapReadyBoolean = MutableLiveData<Boolean>(false)
    private var isFragmentAttached = MutableLiveData<Boolean>(false)
    private val readyToLocateDevice = MediatorLiveData<Boolean>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_map, container, false)
        locationViewModel = ViewModelProvider(requireActivity()).get(LocationViewModel::class.java)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        supportMapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        supportMapFragment.getMapAsync(this)

        readyToLocateDevice.addSource(onMapReadyBoolean) { value ->
            readyToLocateDevice.value = value && isFragmentAttached.value == true
        }
        readyToLocateDevice.addSource(isFragmentAttached) { value ->
            readyToLocateDevice.value = value && onMapReadyBoolean.value == true

        }

        readyToLocateDevice.observe(viewLifecycleOwner, readyToLocateDeviceObserver)

        // choose location fragment gets the place from auto complete fragment
        // and put it in location VM.
        // the map fragment gets it from location VM and navigate to it
        locationViewModel.navigateToPlace.observe(viewLifecycleOwner, androidx.lifecycle.Observer { place ->
            place?.let {
                locationViewModel.placeMarker = showUserLocation(place)
                locationViewModel.finishNavigation()
            }
        })
/*
        locationViewModel.showLostDogLocation.observe(viewLifecycleOwner, Observer { place ->
            place?.let {
                showUserLocation(place)
                locationViewModel.finishedShowLocation()
            }
        })
*/

        return view
    }

    // this observer decides when to call findDeviceLocation
    // it makes sure the map fragment is attached to the activity, even when orientation changed
    // it also makes sure the map is ready too
    private val readyToLocateDeviceObserver = Observer<Boolean> { ready ->
        Log.i("ready to locate device mediator", "ready? $ready")
        if (ready) {
            if (locationViewModel.showLostDogLocation.value != null) {
                // show lost dog location
                showUserLocation(locationViewModel.showLostDogLocation.value!!)
                locationViewModel.finishedShowLocation()
            } else {
                // place a marker in where user clicks
                map.setOnMapClickListener(onMapOnClickListener)
                // if the user already got the device location
                // we will use it, and won't get it again
                coroutineScope.launch {
                    if (deviceLocation == null) {
                        val locationDeferred = coroutineScope.async {
                            findDeviceLocation()
                        }
                        deviceLocation = locationDeferred.await()
                        deviceLocation?.let {
                            val locationLatLng =
                                LatLng(deviceLocation!!.latitude, deviceLocation!!.longitude)
                            CoroutineScope(Dispatchers.Main).launch {
                                locationViewModel.placeMarker = showUserLocation(locationLatLng)
                            }
                        }
                        // tried but couldn't find the device location
                        if (deviceLocation == null) {
                            CoroutineScope(Dispatchers.Main).launch {
                                locationViewModel.placeMarker =
                                    showUserLocation(LatLng(43.651070, -79.347015))
                            }
                        }
                    } else {
                        // display the existing location
                        Log.i("find device location", "displaying the old location")
                        locationViewModel.placeMarker = showUserLocation(
                            LatLng(
                                deviceLocation!!.latitude,
                                deviceLocation!!.longitude
                            )
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {

        Log.i("map fragment", "on map ready")
        map = googleMap
        // enable zoom function
        map.uiSettings.isZoomControlsEnabled = true
        map.isMyLocationEnabled = true;
        map.uiSettings.isMyLocationButtonEnabled = true

        onMapReadyBoolean.value = true
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Log.i("onAttached", "set to true")
        isFragmentAttached.value = true
    }

    override fun onDetach() {
        super.onDetach()
        Log.i("onDetached", "set to false")
        isFragmentAttached.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("onDestroyed", "on map set to false")
        onMapReadyBoolean.value = false
    }


    private val onMapOnClickListener = object : OnMapClickListener {
        // upon click, remove the previous marker
        // add a new marker in the place
        override fun onMapClick(place: LatLng) {
            Log.i("on map clicked", "Place: ${place.latitude}, ${place.longitude}")
            locationViewModel.placeMarker?.remove()
            locationViewModel.placeMarker = map.addMarker(
                MarkerOptions().position(
                    place).title("New location"))
        }
    }


    @SuppressLint("MissingPermission")
    private suspend fun findDeviceLocation() : Location? =
        suspendCancellableCoroutine<Location?> { cancellableContinuation ->
            Log.i("device location", "finding")
            val lastKnownLocation = fusedLocationProviderClient.lastLocation
            // let's check if the activity exists before we require it,
            // if it doesn't exist, we don't do the operation
            //val activity = requireActivity()
            //if (activity != null) {
                lastKnownLocation.addOnCompleteListener(requireActivity()) { task ->
                    if (task.isSuccessful) {
                        if (task.result != null) {
                            Log.i("found location", task.result.latitude.toString())
                            cancellableContinuation.resume(task.result) {}
                        } else {
                            cancellableContinuation.resume(null) {}
                        }
                    } else {
                        Log.i("error in finding location", "true")
                        cancellableContinuation.resume(null) {}
                    }
                }
            //} else {
                //Log.i("find device location", "activity is null")
            //}
    }

    private fun showUserLocation(locationLatLng: LatLng) : Marker? {
        val marker = map.addMarker(
            MarkerOptions().position(
            locationLatLng).title("Current location"))
        map.moveCamera(CameraUpdateFactory.newLatLng(locationLatLng))

        val cameraPosition = CameraPosition.Builder()
            .target(locationLatLng)
            .zoom(18f)
            .bearing(90f)
            .tilt(30f)
            .build()

        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        Log.i("showed location", "location: lat ${locationLatLng.latitude} long ${locationLatLng.longitude}")
        return marker
    }
}