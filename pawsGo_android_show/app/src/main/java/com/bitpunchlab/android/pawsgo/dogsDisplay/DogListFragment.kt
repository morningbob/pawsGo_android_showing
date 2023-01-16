package com.bitpunchlab.android.pawsgo.dogsDisplay

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.navigation.fragment.findNavController
import com.bitpunchlab.android.pawsgo.PetTypeMap
import com.bitpunchlab.android.pawsgo.R
import com.bitpunchlab.android.pawsgo.TypeOfPet
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModel
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModelFactory
import com.bitpunchlab.android.pawsgo.modelsRoom.DogRoom
import com.bitpunchlab.android.pawsgo.modelsRoom.MessageRoom
import com.bumptech.glide.Glide
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import com.bitpunchlab.android.pawsgo.databinding.FragmentDogListBinding


class DogListFragment : Fragment() {

    private var _binding : com.bitpunchlab.android.pawsgo.databinding.FragmentDogListBinding? = null
    private val binding get() = _binding!!
    private lateinit var firebaseClient : FirebaseClientViewModel
    private lateinit var dogsAdapter : DogsAdapter
    private lateinit var dogsViewModel : DogsViewModel
    private var lostOrFound : Boolean? = null
    private var petType = MutableLiveData<TypeOfPet>(TypeOfPet.All)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDogListBinding.inflate(inflater, container, false)
        firebaseClient = ViewModelProvider(requireActivity(),
                FirebaseClientViewModelFactory(requireActivity().application))
            .get(FirebaseClientViewModel::class.java)
        dogsViewModel = ViewModelProvider(requireActivity(),
            DogsViewModelFactory(requireActivity().application))
            .get(DogsViewModel::class.java)
        binding.lifecycleOwner = viewLifecycleOwner
        lostOrFound = requireArguments().getBoolean("lostOrFound")

        dogsAdapter = DogsAdapter( DogOnClickListener { dog ->
            dogsViewModel.onDogChosen(dog)
        },
        MessageClickListener
         { dog ->
            dogsViewModel.onDogMessageClicked(dog)
         })

        binding.dogListRecycler.adapter = dogsAdapter

        setupCorrespondingIconAndTitle()
        setupChoiceOfPetFunction()

        dogsViewModel.chosenDog.observe(viewLifecycleOwner, Observer { dog ->
            dog?.let {
                // navigate to the dog details fragment
                val action = DogListFragmentDirections.showDogAction(it)
                findNavController().navigate(action)
                dogsViewModel.finishedDogChosen()
            }
        })

        dogsViewModel.dogMessage.observe(viewLifecycleOwner, Observer { dog ->
            dog?.let {
                // navigate to send message fragment
                val action = DogListFragmentDirections.SendAMessageAction(dog, null, null)
                findNavController().navigate(action)
                dogsViewModel.finishedDogMessage()
            }
        })

        petType.observe(viewLifecycleOwner, Observer { type ->
            if (lostOrFound == true && !dogsViewModel.lostDogs.value.isNullOrEmpty()) {
                updatePetList(dogsViewModel.lostDogs.value!!, type)
            } else if (!dogsViewModel.foundDogs.value.isNullOrEmpty()){
                updatePetList(dogsViewModel.foundDogs.value!!, type)
            }
        })
        return binding.root
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupCorrespondingIconAndTitle() {
        if (lostOrFound == null) {
            findNavController().popBackStack()
        } else if (lostOrFound == true) {
            binding.artDogList.setImageResource(R.drawable.track)
            binding.textviewTitle.text = getString(R.string.lost_dogs)
            binding.textviewIntro.text = getString(R.string.lost_dogs_list_intro)
            dogsViewModel.lostDogs.observe(viewLifecycleOwner, Observer { dogs ->
                dogs?.let {
                    Log.i("dogsVM lost dogs", dogs.size.toString())
                    updatePetList(it, petType.value!!)
                }
            })
        } else if (lostOrFound == false) {
            binding.artDogList.setImageResource(R.drawable.laughing)
            binding.textviewTitle.text = getString(R.string.found_dogs)
            binding.textviewIntro.text = getString(R.string.found_dogs_list_intro)
            dogsViewModel.foundDogs.observe(viewLifecycleOwner, Observer { dogs ->
                dogs?.let {
                    updatePetList(it, petType.value!!)
                }
            })
        }
    }

    private fun setupChoiceOfPetFunction() {
        binding.textviewDogsOnly!!.setOnClickListener {
            petType.value = TypeOfPet.Dog
        }
        binding.textviewCatsOnly!!.setOnClickListener {
            petType.value = TypeOfPet.Cat
        }
        binding.textviewBirdsOnly!!.setOnClickListener {
            petType.value = TypeOfPet.Bird
        }
        binding.textviewOthersOnly!!.setOnClickListener {
            petType.value = TypeOfPet.Other
        }
        binding.textviewAll!!.setOnClickListener {
            petType.value = TypeOfPet.All
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updatePetList(pets: List<DogRoom>, petType: TypeOfPet) {
        var oneTypeOfPet = emptyList<DogRoom>()
        // we filter the pet list here, for showing just 1 type of pet
        if (petType == TypeOfPet.All) {
            oneTypeOfPet = pets
        } else if (petType != TypeOfPet.Other) {
            oneTypeOfPet = pets.filter { pet -> pet.animalType == PetTypeMap[petType] }
        } else {
            oneTypeOfPet = pets.filter { pet -> pet.animalType != PetTypeMap[TypeOfPet.Dog] &&
                pet.animalType != PetTypeMap[TypeOfPet.Cat] &&
                pet.animalType != PetTypeMap[TypeOfPet.Bird] }
        }
        val orderedDogs = orderByDate(oneTypeOfPet)
        dogsAdapter.submitList(orderedDogs)
        dogsAdapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun orderByDate(dogs: List<DogRoom>) : List<DogRoom> {
        return dogs.sortedByDescending { convertToDate(it.dateLastSeen) }
    }

    private fun convertToDate(dateString: String) : Date? {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd")

        try {
            //val formatterOut = SimpleDateFormat("dd MMM yyyy  HH:mm:ss")
            return dateFormat.parse(dateString)
        } catch (e: java.lang.Exception) {
            Log.i("orderByDate", "parsing error")
            return null
        }
    }


}