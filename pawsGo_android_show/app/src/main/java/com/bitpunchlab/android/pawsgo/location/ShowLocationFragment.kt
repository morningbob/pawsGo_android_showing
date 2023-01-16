package com.bitpunchlab.android.pawsgo.location

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.bitpunchlab.android.pawsgo.R
import com.bitpunchlab.android.pawsgo.databinding.FragmentShowLocationBinding
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModel
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModelFactory
import com.bitpunchlab.android.pawsgo.modelsRoom.DogRoom
import com.google.android.gms.maps.model.LatLng


class ShowLocationFragment : Fragment() {

    private var _binding : FragmentShowLocationBinding? = null
    private val binding get() = _binding!!
    private lateinit var firebaseClient : FirebaseClientViewModel
    private var dog : DogRoom? = null
    private lateinit var locationViewModel : LocationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentShowLocationBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        dog = requireArguments().getParcelable("dog")
        firebaseClient = ViewModelProvider(requireActivity(),
            FirebaseClientViewModelFactory(requireActivity().application))
            .get(FirebaseClientViewModel::class.java)
        locationViewModel = ViewModelProvider(requireActivity())
            .get(LocationViewModel::class.java)
        insertMapFragment()

        // retrieve lat lng point from the dog object
        // parse it to lat lng point and put it in locationVM
        // map fragment observed the variable and show it
        locationViewModel._showLostDogLocation.value = getLocationLatLng(dog!!)

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun insertMapFragment() {
        val mapFragment = MapFragment()
        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(R.id.show_map_container, mapFragment).commit()
    }

    private fun getLocationLatLng(dog: DogRoom) : LatLng? {
        if (dog.locationLat != null && dog.locationLng != null) {
            return LatLng(dog.locationLat!!, dog.locationLng!!)
        }
        return null
    }
}