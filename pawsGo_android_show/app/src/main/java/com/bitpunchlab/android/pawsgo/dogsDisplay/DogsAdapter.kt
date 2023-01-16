package com.bitpunchlab.android.pawsgo.dogsDisplay

import androidx.databinding.ViewDataBinding
import com.bitpunchlab.android.pawsgo.R
import com.bitpunchlab.android.pawsgo.base.BaseRecyclerViewAdapter
import com.bitpunchlab.android.pawsgo.base.GenericListener
import com.bitpunchlab.android.pawsgo.base.GenericRecyclerBindingInterface
import com.bitpunchlab.android.pawsgo.databinding.ItemDogBinding
import com.bitpunchlab.android.pawsgo.modelsRoom.DogRoom

class DogsAdapter(var clickListener: DogOnClickListener,
    var messageClickListener: MessageClickListener) : BaseRecyclerViewAdapter<DogRoom>(
        clickListener = clickListener,
        messageClickListener = messageClickListener,
        messageBoolean = null,
        compareItems = { old, new ->  old.dogID == new.dogID },
        compareContents = { old, new ->  old.ownerEmail == new.ownerEmail },
        bindingInter = object : GenericRecyclerBindingInterface<DogRoom> {
            override fun bindData(
                item: DogRoom,
                binding: ViewDataBinding,
                onClickListener: GenericListener<DogRoom>?,
                messageClickListener: GenericListener<DogRoom>?,
                messageBoolean: Boolean?
            ) {
                (binding as ItemDogBinding).clickListener = clickListener
                binding.messageClickListener = messageClickListener as MessageClickListener
                binding.dog = item
                binding.executePendingBindings()
            }

        }
){
    override fun getLayoutRes(viewType: Int) = R.layout.item_dog
}

class DogOnClickListener(override val clickListener: (DogRoom) -> Unit) :
    GenericListener<DogRoom>(clickListener) {
    fun onClick(dog: DogRoom) = clickListener(dog)
}

class MessageClickListener(override val clickListener: (DogRoom) -> Unit) :
    GenericListener<DogRoom>(clickListener) {
    fun onClick(dog: DogRoom) = clickListener(dog)
}