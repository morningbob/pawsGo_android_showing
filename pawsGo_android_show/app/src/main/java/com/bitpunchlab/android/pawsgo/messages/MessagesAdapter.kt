package com.bitpunchlab.android.pawsgo.messages

import androidx.databinding.ViewDataBinding
import com.bitpunchlab.android.pawsgo.R
import com.bitpunchlab.android.pawsgo.base.BaseRecyclerViewAdapter
import com.bitpunchlab.android.pawsgo.base.GenericListener
import com.bitpunchlab.android.pawsgo.base.GenericRecyclerBindingInterface
import com.bitpunchlab.android.pawsgo.databinding.ItemMessageBinding
import com.bitpunchlab.android.pawsgo.modelsRoom.MessageRoom

class MessagesAdapter(var clickListener: MessageOnClickListener,
                      //var replyClickListener: MessageOnClickListener,
        var receivedOrSent: Boolean) : BaseRecyclerViewAdapter<MessageRoom>(
    clickListener = clickListener,
    messageClickListener = null,
    messageBoolean = receivedOrSent,
    compareItems = { old, new ->  old.messageID == new.messageID },
    compareContents = { old, new ->  old.date == new.date },
    bindingInter = object : GenericRecyclerBindingInterface<MessageRoom> {
        override fun bindData(
            item: MessageRoom,
            binding: ViewDataBinding,
            onClickListener: GenericListener<MessageRoom>?,
            messageClickListener: GenericListener<MessageRoom>?,
            messageBoolean: Boolean?

        ) {
            (binding as ItemMessageBinding).message = item
            binding.clickListener = clickListener

            if (messageBoolean == true) {
                binding.userName.text = item.senderName
                binding.entitle!!.text = "From"
            } else {
                binding.userName.text = item.targetName
                binding.entitle!!.text = "To"
            }
            binding.executePendingBindings()
        }

    }
) {
    override fun getLayoutRes(viewType: Int): Int {
        return R.layout.item_message
    }
}

class MessageOnClickListener(override val clickListener: (MessageRoom) -> Unit) :
    GenericListener<MessageRoom>(clickListener) {

        fun onClick(message: MessageRoom) = clickListener(message)
}