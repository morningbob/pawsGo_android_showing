package com.bitpunchlab.android.pawsgo.reportLostDog

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.bitpunchlab.android.pawsgo.R
import com.bitpunchlab.android.pawsgo.databinding.FragmentChooseReportBinding
import com.bitpunchlab.android.pawsgo.databinding.FragmentEditReportBinding
import com.bitpunchlab.android.pawsgo.dogsDisplay.DogOnClickListener
import com.bitpunchlab.android.pawsgo.dogsDisplay.DogsAdapter
import com.bitpunchlab.android.pawsgo.dogsDisplay.DogsViewModel
import com.bitpunchlab.android.pawsgo.dogsDisplay.DogsViewModelFactory


class ChooseReportFragment : Fragment() {

    private var _binding : FragmentChooseReportBinding? = null
    private val binding get() = _binding!!
    private lateinit var petsAdapter : PetReportAdapter
    private lateinit var dogsViewModel : DogsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentChooseReportBinding.inflate(inflater, container, false)
        //binding.lifecycleOwner = viewLifecycleOwner
        dogsViewModel = ViewModelProvider(requireActivity(), DogsViewModelFactory(requireActivity().application))
            .get(DogsViewModel::class.java)
        petsAdapter = PetReportAdapter(DogOnClickListener { dog ->
            dogsViewModel.onDogChosen(dog)
        })
        binding.dogReportsRecycler.adapter = petsAdapter

        dogsViewModel.dogReports.observe(viewLifecycleOwner, Observer { dogs ->
            dogs?.let {
                Log.i("choose report fragment", "got back dogs ${dogs.size}")
                petsAdapter.submitList(dogs)
                petsAdapter.notifyDataSetChanged()
            }
        })

        dogsViewModel.chosenDog.observe(viewLifecycleOwner, Observer { dog ->
            dog?.let {
                //val action = ChooseReportFragmentDirections.actionChooseReportFragmentToPetFormFragment(dog, true)
                val action = ChooseReportFragmentDirections.editReportAction(dog)
                findNavController().navigate(action)
                dogsViewModel.finishedDogChosen()
            }
        })

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}