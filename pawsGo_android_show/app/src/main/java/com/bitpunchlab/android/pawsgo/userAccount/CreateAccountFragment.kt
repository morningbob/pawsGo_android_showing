package com.bitpunchlab.android.pawsgo.userAccount

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.bitpunchlab.android.pawsgo.AppState
import com.bitpunchlab.android.pawsgo.R
import com.bitpunchlab.android.pawsgo.databinding.FragmentCreateAccountBinding
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModel
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch


class CreateAccountFragment : Fragment() {

    private var _binding : FragmentCreateAccountBinding? = null
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
        _binding = FragmentCreateAccountBinding.inflate(inflater, container, false)
        firebaseClient = ViewModelProvider(requireActivity(),
            FirebaseClientViewModelFactory(requireActivity().application))
            .get(FirebaseClientViewModel::class.java)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.firebaseClient = firebaseClient

        binding.buttonSend.setOnClickListener {
            // display progress bar
            startProgressBar()
            // we check firestore, if the email already exists
            // if it exists, we notify the user and don't process the registration
            // if it doesn't exist, we continue the registration
            coroutineScope.launch {
                val firestoreResultDeferred = coroutineScope.async {
                    firebaseClient.checkEmailExistFirestore(firebaseClient.userEmail.value!!)
                }
                val firebaseAuthResultDeferred = coroutineScope.async {
                    firebaseClient.checkEmailExistFirebaseAuth(firebaseClient.userEmail.value!!)
                }
                val firestoreResult = firestoreResultDeferred.await()
                val firebaseAuthResult = firebaseAuthResultDeferred.await()

                when (firestoreResult) {
                    1 -> {
                        if (!firebaseAuthResult) {
                            firebaseClient.isCreatingUserAccount = true
                            firebaseClient._appState.postValue(AppState.READY_CREATE_USER_AUTH)
                        } else {
                            // email exists in auth
                            firebaseClient._appState.postValue(AppState.EMAIL_ALREADY_EXISTS)
                        }
                    }
                    // email exists in firestore
                    2 -> firebaseClient._appState.postValue(AppState.EMAIL_ALREADY_EXISTS)
                    0 -> firebaseClient._appState.postValue(AppState.EMAIL_SERVER_ERROR)
                }
            }

        }

        firebaseClient.readyRegisterLiveData.observe(viewLifecycleOwner, Observer { value ->
            value?.let {
                if (value) {
                    // display send button
                    binding.buttonSend.visibility = View.VISIBLE
                } else {
                    binding.buttonSend.visibility = View.INVISIBLE
                }
            }
        })

        firebaseClient.appState.observe(viewLifecycleOwner, appStateObserver)

        return binding.root
    }

    private fun startProgressBar() {
        binding.progressBar?.visibility = View.VISIBLE

        requireActivity().window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private fun stopProgressBar() {
        binding.progressBar?.visibility = View.GONE
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private val appStateObserver = Observer<AppState> { state ->
        when (state) {
            AppState.LOGGED_IN -> {
                Log.i("create account", "login state detected")
                findNavController().navigate(R.id.action_createAccountFragment_to_MainFragment)
            }
            AppState.EMAIL_ALREADY_EXISTS -> {
                emailExistsAlert()
                stopProgressBar()
                firebaseClient._appState.value = AppState.NORMAL
            }
            AppState.EMAIL_SERVER_ERROR -> {
                serverErrorAlert()
                stopProgressBar()
                firebaseClient._appState.value = AppState.NORMAL
            }
            AppState.ERROR_CREATE_USER_AUTH -> {
                authErrorAlert()
                stopProgressBar()
                firebaseClient._appState.value = AppState.NORMAL
            }
            AppState.SUCCESS_CREATED_USER_ACCOUNT -> {
                //createSuccessAlert()
                stopProgressBar()
                //firebaseClient._appState.value = AppState.NORMAL
            }
            AppState.ERROR_CREATE_USER_ACCOUNT -> {
                createErrorAlert()
                stopProgressBar()
                firebaseClient._appState.value = AppState.NORMAL
            }
            else -> 0
        }
    }

    private fun authErrorAlert() {
        val authAlert = AlertDialog.Builder(context)

        with(authAlert) {
            setTitle(getString(R.string.authentication_service_alert))
            setMessage(getString(R.string.authentication_service_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }

    private fun createErrorAlert() {
        val createAlert = AlertDialog.Builder(context)

        with(createAlert) {
            setTitle(getString(R.string.create_error_alert))
            setMessage(getString(R.string.create_error_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }

    private fun createSuccessAlert() {
        val successAlert = AlertDialog.Builder(context)

        with(successAlert) {
            setTitle("Create User Account")
            setMessage("Your account was successfully created.")
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }

    private fun emailExistsAlert() {
        val existAlert = AlertDialog.Builder(context)

        with(existAlert) {
            setTitle(getString(R.string.account_registration))
            setMessage(getString(R.string.email_exists_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }

    private fun serverErrorAlert() {
        val errorAlert = AlertDialog.Builder(context)

        with(errorAlert) {
            setTitle(getString(R.string.account_registration))
            setMessage(getString(R.string.server_error_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }
}