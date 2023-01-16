package com.bitpunchlab.android.pawsgo.messages

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.bitpunchlab.android.pawsgo.AppState
import com.bitpunchlab.android.pawsgo.R
import com.bitpunchlab.android.pawsgo.database.PawsGoDatabase
import com.bitpunchlab.android.pawsgo.databinding.FragmentSendMessageBinding
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModel
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModelFactory
import com.bitpunchlab.android.pawsgo.modelsRoom.DogRoom
import com.bitpunchlab.android.pawsgo.modelsRoom.MessageRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*


class SendMessageFragment : Fragment() {

    private var _binding : FragmentSendMessageBinding? = null
    private val binding get() = _binding!!
    private lateinit var firebaseClient : FirebaseClientViewModel
    private lateinit var localDatabase: PawsGoDatabase
    private var coroutineScope = CoroutineScope(Dispatchers.IO)
    private var dog : DogRoom? = null
    private var email: String? = null
    private var name: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSendMessageBinding.inflate(inflater, container, false)
        firebaseClient = ViewModelProvider(requireActivity(),
            FirebaseClientViewModelFactory(requireActivity().application))
            .get(FirebaseClientViewModel::class.java)
        localDatabase = PawsGoDatabase.getInstance(requireContext())
        binding.lifecycleOwner = viewLifecycleOwner
        dog = requireArguments().getParcelable("dog")
        if (dog == null && (email == null || name == null)) {
            findNavController().popBackStack()
        }
        binding.dog = dog

        binding.buttonSend.setOnClickListener {
            if (dog!!.ownerEmail == firebaseClient.auth.currentUser!!.email) {
                sendMessageSelfAlert()
            } else {
                startProgressBar()
                processSendMessage()
            }
        }

        firebaseClient.appState.observe(viewLifecycleOwner, messageSentObserver)

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

    private val messageSentObserver = androidx.lifecycle.Observer<AppState> { appState ->
        when (appState) {
            AppState.MESSAGE_SENT_SUCCESS -> {
                stopProgressBar()
                messageSentSuccessAlert()
                firebaseClient._appState.value = AppState.NORMAL
            }
            AppState.MESSAGE_SENT_ERROR -> {
                stopProgressBar()
                messageSentErrorAlert()
                firebaseClient._appState.value = AppState.NORMAL
            }
            else -> {
                stopProgressBar()
            }
        }
    }

    private fun createMessageRoom(userEmail: String, userName: String, targetEmail: String,
                                  targetName: String, message: String, date: String) : MessageRoom {
        return MessageRoom(messageID = UUID.randomUUID().toString(),
            senderEmail = userEmail, senderName = userName, targetEmail = targetEmail,
            targetName = targetName, messageContent = message,
            date = date,
            userCreatorID = firebaseClient.auth.currentUser!!.uid)
    }

    private fun saveMessageRoom(message: MessageRoom) {
        coroutineScope.launch {
            localDatabase.pawsDAO.insertMessages(message)
        }
    }

    private fun processSendMessage() {
        // we also check if the user is sending message to himself,
        // we don't allow that.
        val message = binding.edittextMessage.text.toString()
        if (message != null && message != "") {

            // we create a standard date string here, we need to be consistent
            // in both ios and android platform
            // so both sides can parse the same string format
            val currentDate = Date()
            val dateFormat = SimpleDateFormat("yyyy/MM/dd hh:mm:ss")
                //Calendar.DAY_OF_MONTH
            val dateString = dateFormat.format(currentDate)
            Log.i("date string", dateString)

            val messageRoom = createMessageRoom(
                userEmail = firebaseClient.currentUserFirebaseLiveData.value!!.userEmail,
                userName = firebaseClient.currentUserFirebaseLiveData.value!!.userName,
                targetEmail = dog!!.ownerEmail, targetName = dog!!.ownerName,
                message = message, date = dateString
            )
            coroutineScope.launch {
                if (firebaseClient.sendMessageToFirestoreMessaging(messageRoom)) {
                    // if saved to firestore successfully, we save it here
                    saveMessageRoom(messageRoom)
                    // display an alert
                    firebaseClient._appState.postValue(AppState.MESSAGE_SENT_SUCCESS)
                    // clear fields
                    binding.edittextMessage.text = null
                } else {
                    firebaseClient._appState.postValue(AppState.MESSAGE_SENT_ERROR)
                }
            }
        }
    }

    private fun messageSentSuccessAlert() {
        val successAlert = AlertDialog.Builder(context)

        with(successAlert) {
            setTitle(getString(R.string.message_sent_alert))
            setMessage(getString(R.string.message_sent_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }

    private fun messageSentErrorAlert() {
        val successAlert = AlertDialog.Builder(context)

        with(successAlert) {
            setTitle(getString(R.string.send_message_failure_alert))
            setMessage(getString(R.string.send_message_failure_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }

    private fun sendMessageSelfAlert() {
        val selfAlert = AlertDialog.Builder(context)

        with(selfAlert) {
            setTitle(getString(R.string.send_message_error_alert))
            setMessage(getString(R.string.send_message_error_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }
}