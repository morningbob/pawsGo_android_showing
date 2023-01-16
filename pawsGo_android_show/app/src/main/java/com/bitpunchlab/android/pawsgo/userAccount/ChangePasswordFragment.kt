package com.bitpunchlab.android.pawsgo.userAccount

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.bitpunchlab.android.pawsgo.AppState
import com.bitpunchlab.android.pawsgo.R
import com.bitpunchlab.android.pawsgo.databinding.FragmentChangePasswordBinding
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModel
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class ChangePasswordFragment : Fragment() {

    private var _binding : FragmentChangePasswordBinding? = null
    private val binding get() = _binding!!
    private lateinit var firebaseClient : FirebaseClientViewModel
    private var coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentChangePasswordBinding.inflate(inflater, container, false)
        firebaseClient = ViewModelProvider(requireActivity(),
            FirebaseClientViewModelFactory(requireActivity().application))
            .get(FirebaseClientViewModel::class.java)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.firebaseClient = firebaseClient

        binding.buttonSend.setOnClickListener {
            // if it is visible, everything is ready
            // check here if the current password and the new password are the same
            // if so, alert user and don't process
            if (firebaseClient.currentPassword.value == firebaseClient.userPassword.value) {
                samePasswordAlert()
            } else {
                startProgressBar()
                processChangePassword()
            }
        }

        firebaseClient.readyChangePasswordLiveData.observe(viewLifecycleOwner, Observer { ready ->
            ready?.let {
                if (ready) {
                    binding.buttonSend.visibility = View.VISIBLE
                } else {
                    binding.buttonSend.visibility = View.INVISIBLE
                }
            }
        })

        firebaseClient.appState.observe(viewLifecycleOwner, Observer { appState ->
            when (appState) {
                AppState.CHANGE_PASSWORD_SUCCESS -> {
                    stopProgressBar()
                    changePasswordSuccessAlert()
                    firebaseClient._appState.value = AppState.RESET
                }
                AppState.CHANGE_PASSWORD_INCORRECT -> {
                    stopProgressBar()
                    passwordIncorrectAlert()
                    firebaseClient._appState.value = AppState.NORMAL
                }
                AppState.CHANGE_PASSWORD_ERROR -> {
                    stopProgressBar()
                    changePasswordErrorAlert()
                    firebaseClient._appState.value = AppState.RESET
                }
                else -> {
                    stopProgressBar()
                }
            }
        })

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

    private fun processChangePassword() {
        coroutineScope.launch {
            val result = firebaseClient.changePasswordFirebaseAuth()
            when (result) {
                1 -> firebaseClient._appState.postValue(AppState.CHANGE_PASSWORD_SUCCESS)
                0 -> {
                    firebaseClient._appState.postValue(AppState.CHANGE_PASSWORD_INCORRECT)
                    // clear only the current password field
                    firebaseClient._currentPassword.postValue("")
                }
                2 -> firebaseClient._appState.postValue(AppState.CHANGE_PASSWORD_ERROR)
            }
        }
    }

    private fun samePasswordAlert() {
        val sameAlert = AlertDialog.Builder(context)

        with(sameAlert) {
            setTitle(getString(R.string.same_passwords_alert))
            setMessage(getString(R.string.same_password_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }

    private fun changePasswordSuccessAlert() {
        val successAlert = AlertDialog.Builder(context)

        with(successAlert) {
            setTitle(getString(R.string.change_password))
            setMessage(getString(R.string.password_success_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }

    private fun passwordIncorrectAlert() {
        val incorrectAlert = AlertDialog.Builder(context)

        with(incorrectAlert) {
            setTitle(getString(R.string.change_password))
            setMessage(getString(R.string.password_incorrect_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }

    private fun changePasswordErrorAlert() {
        val errorAlert = AlertDialog.Builder(context)

        with(errorAlert) {
            setTitle(getString(R.string.change_password))
            setMessage(getString(R.string.password_error_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }
}