package com.bitpunchlab.android.pawsgo.base

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

abstract class BaseRecyclerViewAdapter<T : Any>(
    private val clickListener: GenericListener<T>?,
    private val messageClickListener: GenericListener<T>?,
    private val messageBoolean: Boolean?,
    compareItems: (old: T, new: T) -> Boolean,
    compareContents: (old: T, new: T) -> Boolean,
    private val bindingInter: GenericRecyclerBindingInterface<T>,
) :
    ListAdapter<T, GenericViewHolder>(GenericDiffCallback(compareItems, compareContents)){

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenericViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)

        val binding = DataBindingUtil
            .inflate<ViewDataBinding>(layoutInflater, getLayoutRes(viewType), parent, false)

        binding.lifecycleOwner = getLifecycleOwner()

        return GenericViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GenericViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, clickListener, messageClickListener, messageBoolean, bindingInter)
    }

    @LayoutRes
    abstract fun getLayoutRes(viewType: Int): Int

    open fun getLifecycleOwner(): LifecycleOwner? {
        return null
    }
}

class GenericViewHolder(val binding: ViewDataBinding) : RecyclerView.ViewHolder(binding.root) {
    fun <T : Any> bind(item: T,
                       clickListener: GenericListener<T>?,
                       messageClickListener: GenericListener<T>?,
                       messageBoolean: Boolean?,
                       bindingInterface: GenericRecyclerBindingInterface<T>) {
        bindingInterface.bindData(item, binding, clickListener, messageClickListener, messageBoolean)
    }
}

interface GenericRecyclerBindingInterface<T: Any> {
    fun bindData(item: T, binding: ViewDataBinding, onClickListener: GenericListener<T>?,
                 messageClickListener: GenericListener<T>?,
                 messageBoolean: Boolean?,
    )
}

class GenericDiffCallback<T>(
    private val compareItems: (old: T, new: T) -> Boolean,
    private val compareContents: (old: T, new: T) -> Boolean
) : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(old: T, new: T): Boolean
            = compareItems(old, new)

    override fun areContentsTheSame(old: T, new: T): Boolean
            = compareContents(old, new)
}

open abstract class GenericListener<T>(open val clickListener: (T) -> Unit) {
}