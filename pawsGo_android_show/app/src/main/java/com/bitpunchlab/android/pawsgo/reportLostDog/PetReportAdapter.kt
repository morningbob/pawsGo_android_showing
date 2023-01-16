package com.bitpunchlab.android.pawsgo.reportLostDog

import android.view.View
import androidx.databinding.ViewDataBinding
import com.bitpunchlab.android.pawsgo.R
import com.bitpunchlab.android.pawsgo.base.BaseRecyclerViewAdapter
import com.bitpunchlab.android.pawsgo.base.GenericListener
import com.bitpunchlab.android.pawsgo.base.GenericRecyclerBindingInterface
import com.bitpunchlab.android.pawsgo.databinding.ItemDogBinding
import com.bitpunchlab.android.pawsgo.dogsDisplay.DogOnClickListener
import com.bitpunchlab.android.pawsgo.modelsRoom.DogRoom

class PetReportAdapter(clickListener: DogOnClickListener) : BaseRecyclerViewAdapter<DogRoom>(
    clickListener = clickListener,
    messageClickListener = null,
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
            (binding as ItemDogBinding).dog = item
            binding.clickListener = clickListener
            binding.buttonMessage.visibility = View.GONE
            binding.executePendingBindings()
        }

    }
) {
    override fun getLayoutRes(viewType: Int) = R.layout.item_dog
}