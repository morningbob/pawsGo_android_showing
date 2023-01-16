package com.bitpunchlab.android.pawsgo.dogsDisplay

import android.net.Uri
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat

@BindingAdapter("loadImage")
fun fetchImage(view: ImageView, src: String?) {
    var uri : Uri? = null
    src?.let {
        uri = src.toUri()
    }
    uri?.let {
        Glide
            .with(view)
            .asBitmap()
            .load(uri)
            .into(view)
    }
}

@BindingAdapter("formatDateNoTime")
fun parseDateNoTime(view: TextView, dateString: String)  {
    val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy")
    //val formatterOut = SimpleDateFormat("dd MMM yyyy")

    try {
        val date = dateFormat.parse(dateString)
        val formatterOut = SimpleDateFormat("dd MMM yyyy")
        view.text = formatterOut.format(date)
    } catch (e: java.lang.Exception) {
        Log.i("parse date", "parsing error")
        view.text = "Not Available"
    }
}