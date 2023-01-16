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
import android.widget.EditText
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.bitpunchlab.android.pawsgo.AppState
import com.bitpunchlab.android.pawsgo.R
import com.bitpunchlab.android.pawsgo.databinding.FragmentLoginBinding
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModel
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var firebaseClient : FirebaseClientViewModel
    private var coroutineScope = CoroutineScope(Dispatchers.IO)
    private var recoverEmail = MutableLiveData<String?>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        firebaseClient = ViewModelProvider(requireActivity(),
            FirebaseClientViewModelFactory(requireActivity().application))
            .get(FirebaseClientViewModel::class.java)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.firebaseClient = firebaseClient

        binding.buttonSend.setOnClickListener {
            // display progress bar when login is clicked
            startProgressBar()
            coroutineScope.launch {
                if (!firebaseClient.loginUserOfAuth()) {
                    firebaseClient._appState.postValue(AppState.INCORRECT_CREDENTIALS)
                }
            }
        }

        binding.textviewResetPassword?.setOnClickListener {
            enterEmailAlert()
        }

        binding.buttonCreateAccount.setOnClickListener {
            findNavController().navigate(R.id.action_LoginFragment_to_createAccountFragment)
        }

        firebaseClient.readyLoginLiveData.observe(viewLifecycleOwner, Observer<Boolean> { value ->
            value?.let {
                if (value) {
                    Log.i("login fragment", "ready login true")
                    binding.buttonSend.visibility = View.VISIBLE
                } else {
                    Log.i("login fragment", "ready login false")
                    binding.buttonSend.visibility = View.INVISIBLE
                }
            }})

        firebaseClient.appState.observe(viewLifecycleOwner, appStateObserver)

        recoverEmail.observe(viewLifecycleOwner, Observer { email ->
            if (!email.isNullOrEmpty()) {
                coroutineScope.launch {
                    val result = firebaseClient.generatePasswordResetEmail(email)
                    when (result) {
                        1 -> firebaseClient._appState.postValue(AppState.RESET_EMAIL_SENT)
                        2 -> firebaseClient._appState.postValue(AppState.RESET_EMAIL_ERROR)
                        0 -> firebaseClient._appState.postValue(AppState.EMAIL_INVALID)
                    }
                    recoverEmail.value = null
                }
            }
        })

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

    private val appStateObserver = Observer<AppState> { state ->
        when (state) {
            AppState.LOGGED_IN -> {
                // remove progress bar when the login is successful
                stopProgressBar()
                findNavController().navigate(R.id.action_LoginFragment_to_MainFragment)
            }
            AppState.INCORRECT_CREDENTIALS -> {
                stopProgressBar()
                incorrectCredentialsAlert()
                firebaseClient._appState.value = AppState.NORMAL
            }
            AppState.RESET_EMAIL_SENT -> {
                resetEmailSentAlert()
                firebaseClient._appState.value = AppState.NORMAL
            }
            AppState.RESET_EMAIL_ERROR -> {
                resetEmailErrorAlert()
                firebaseClient._appState.value = AppState.NORMAL
            }
            AppState.EMAIL_INVALID -> {
                emailInvalidAlert()
                firebaseClient._appState.value = AppState.NORMAL
            }
            else -> 0
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun incorrectCredentialsAlert() {
        val incorrectAlert = AlertDialog.Builder(context)

        with(incorrectAlert) {
            setTitle(getString(R.string.incorrect_login_info_alert))
            setMessage(getString(R.string.incorrect_login_info_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }

    private fun enterEmailAlert() {
        val enterAlert = AlertDialog.Builder(context)
        val emailEditText = EditText(context)

        with(enterAlert) {
            setTitle(getString(R.string.password_reset_alert))
            setMessage(getString(R.string.password_reset_alert_desc))
            setView(emailEditText)
            setPositiveButton(getString(R.string.send),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                    val email = emailEditText.text.toString()
                    if (email != null && email != "") {
                        recoverEmail.value = email
                    }
                })
            setNegativeButton(getString(R.string.cancel),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }

    private fun resetEmailSentAlert() {
        val sentAlert = AlertDialog.Builder(context)

        with(sentAlert) {
            setTitle(getString(R.string.reset_email_sent_alert))
            setMessage(getString(R.string.reset_email_sent_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }

    private fun resetEmailErrorAlert() {
        val sentAlert = AlertDialog.Builder(context)

        with(sentAlert) {
            setTitle(getString(R.string.reset_email_sent_alert))
            setMessage(getString(R.string.reset_email_error_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }

    private fun emailInvalidAlert() {
        val notExistAlert = AlertDialog.Builder(context)

        with(notExistAlert) {
            setTitle(getString(R.string.reset_email_sent_alert))
            setMessage(getString(R.string.email_invalid_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }
}