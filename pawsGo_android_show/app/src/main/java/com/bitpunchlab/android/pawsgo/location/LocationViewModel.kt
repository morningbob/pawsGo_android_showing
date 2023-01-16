package com.bitpunchlab.android.pawsgo.location

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker

class LocationViewModel : ViewModel() {

    var _navigateToPlace = MutableLiveData<LatLng>()
    val navigateToPlace get() = _navigateToPlace

    var _markerPlace = MutableLiveData<LatLng>()
    val markerPlace get() = _markerPlace

    var placeMarker : Marker? = null

    var lostDogLocationLatLng = MutableLiveData<LatLng?>()
    var lostDogLocationAddress = MutableLiveData<List<String>>()

    var displayAddress = MutableLiveData<String>()

    var _showLostDogLocation = MutableLiveData<LatLng?>()
    val showLostDogLocation get() = _showLostDogLocation

    var shouldNavigateChooseLocation = MutableLiveData<Boolean>(false)

    fun finishNavigation() {
        navigateToPlace.value = null
    }

    fun finishedShowLocation() {
        _showLostDogLocation.value = null
    }
}