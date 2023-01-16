package com.bitpunchlab.android.pawsgo.messages

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.bitpunchlab.android.pawsgo.AppState
import com.bitpunchlab.android.pawsgo.R
import com.bitpunchlab.android.pawsgo.database.PawsGoDatabase
import com.bitpunchlab.android.pawsgo.databinding.FragmentReadMessagesBinding
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModel
import com.bitpunchlab.android.pawsgo.firebase.FirebaseClientViewModelFactory
import com.bitpunchlab.android.pawsgo.modelsRoom.MessageRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ReadMessagesFragment : Fragment() {

    private var _binding : FragmentReadMessagesBinding? = null
    private val binding get() = _binding!!
    private lateinit var messagesAdapter: MessagesAdapter
    private lateinit var messagesViewModel : MessagesViewModel
    private lateinit var localDatabase : PawsGoDatabase
    private var coroutineScope = CoroutineScope(Dispatchers.IO)
    private lateinit var firebaseClient : FirebaseClientViewModel
    private var receivedOrSent : Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    @SuppressLint("NotifyDataSetChanged")
    @OptIn(InternalCoroutinesApi::class)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentReadMessagesBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        receivedOrSent = requireArguments().getBoolean("receivedOrSent")

        if (receivedOrSent == null) {
            findNavController().popBackStack()
        }

        messagesViewModel = ViewModelProvider(requireActivity(),
            MessagesViewModelFactory(requireActivity().application))
            .get(MessagesViewModel::class.java)
        localDatabase = PawsGoDatabase.getInstance(requireContext())
        firebaseClient = ViewModelProvider(requireActivity(),
            FirebaseClientViewModelFactory(requireActivity().application))
            .get(FirebaseClientViewModel::class.java)

        // we set the userID in the messagesVM here, to trigger the live data to retrieve the user
        // object, and hence the messages received
        messagesViewModel.userID.value = firebaseClient.auth.currentUser!!.uid

        messagesAdapter = MessagesAdapter(MessageOnClickListener { message ->
            messagesViewModel.onMessageClicked(message)
        }, receivedOrSent!!)
        binding.messagesRecycler.adapter = messagesAdapter

        displayCorrespondingIconAndTitle()

        messagesViewModel.chosenMessage.observe(viewLifecycleOwner, Observer { message ->
            message?.let {
                // that mean the user touched reply
                Log.i("chosen message", "reply clicked")
                // alert for entering message
                enterMessageAlert(message)
                messagesViewModel.onFinishedMessage()
            }
        })

        firebaseClient.appState.observe(viewLifecycleOwner, Observer { appState ->
            when (appState) {
                AppState.MESSAGE_SENT_SUCCESS -> {
                    // success alert
                    messageSentSuccessAlert()
                    firebaseClient._appState.value = AppState.NORMAL
                }
                AppState.MESSAGE_SENT_ERROR -> {
                    // error alert
                    messageSentErrorAlert()
                    firebaseClient._appState.value = AppState.NORMAL
                }
                else -> 0
            }
        })

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun displayCorrespondingIconAndTitle() {
        // here, we set the messages received list or the messages sent list,
        // depends on the true or false we got from receivedOrSent
        if (receivedOrSent == true) {
            // we set the title and the icon art here too
            binding.textviewTitle.text = getString(R.string.messages_received)
            binding.artReadMessages.setImageResource(R.drawable.chat)
            messagesViewModel.messagesReceived.observe(viewLifecycleOwner, Observer { messages ->
                Log.i("messages view model", "got back user and messages, size: ${messages.size}")
                messages?.let {
                    val orderedMessages = orderByDate(it)
                    messagesAdapter.submitList(orderedMessages)
                    messagesAdapter.notifyDataSetChanged()
                }
            })
        } else if (receivedOrSent == false) {
            binding.textviewTitle.text = getString(R.string.messages_sent)
            binding.artReadMessages.setImageResource(R.drawable.sent)
            messagesViewModel.messagesSent.observe(viewLifecycleOwner, Observer { messages ->
                Log.i("messages view model", "got back user and messages, size: ${messages.size}")
                messages?.let {
                    val orderedMessages = orderByDate(it)
                    messagesAdapter.submitList(orderedMessages)
                    messagesAdapter.notifyDataSetChanged()
                }
            })
        }
    }

    private fun orderByDate(messages: List<MessageRoom>) : List<MessageRoom> {
        return messages.sortedByDescending { convertToDate(it.date) }
    }

    private fun convertToDate(dateString: String) : Date? {
        //val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy")
        val dateFormat = SimpleDateFormat("yyyy/MM/dd hh:mm:ss")

        try {
            //val formatterOut = SimpleDateFormat("dd MMM yyyy  HH:mm:ss")
            return dateFormat.parse(dateString)
        } catch (e: java.lang.Exception) {
            Log.i("orderByDate", "parsing error")
            return null
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

    private fun processMessage(message: String, targetName: String, targetEmail: String) {
        val currentDate = Date()
        val dateFormat = SimpleDateFormat("yyyy/MM/dd hh:mm:ss")
        val dateString = dateFormat.format(currentDate)
        Log.i("date string", dateString)

        val messageRoom = createMessageRoom(userEmail = firebaseClient.auth.currentUser!!.email!!,
            userName = firebaseClient.currentUserRoomLiveData.value!!.userName,
            targetEmail = targetEmail, targetName = targetName, message = message,
            date = dateString)
        coroutineScope.launch {
            if (firebaseClient.sendMessageToFirestoreMessaging(messageRoom)) {
                saveMessageRoom(messageRoom)
                firebaseClient._appState.postValue(AppState.MESSAGE_SENT_SUCCESS)
            } else {
                firebaseClient._appState.postValue(AppState.MESSAGE_SENT_ERROR)
            }
        }
    }

    private fun enterMessageAlert(message: MessageRoom) {
        val enterAlert = AlertDialog.Builder(context)
        val messageEditText = EditText(context)

        var name = ""
        var targetEmail = ""

        if (receivedOrSent == true) {
            name = message.senderName
            targetEmail = message.senderEmail
        } else {
            name = message.targetName
            targetEmail = message.targetEmail
        }

        with(enterAlert) {
            setTitle(getString(R.string.enter_message_alert))
            setCancelable(false)
            setMessage("Please enter the message you want to send to $name.")
            setView(messageEditText)
            setPositiveButton(getString(R.string.send),
                DialogInterface.OnClickListener { dialog, button ->
                    val messageContent = messageEditText.text.toString()
                    // get back message, checked not empty, send to firestore
                    if (messageContent != "" && messageContent != " ") {
                        processMessage(messageContent, name, targetEmail)
                    }
                })
            setNegativeButton(getString(R.string.cancel),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
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
            setTitle(getString(R.string.message_was_not_sent_alert))
            setMessage(getString(R.string.message_not_sent_alert_desc))
            setPositiveButton(getString(R.string.ok),
                DialogInterface.OnClickListener { dialog, button ->
                    // do nothing
                })
            show()
        }
    }
}