package com.oceanshare.oceanshare

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import kotlinx.android.synthetic.main.marker_entry.view.*


class MarkerAdapter(context: Context, foodsList: ArrayList<Marker>) : BaseAdapter() {
    private var markersList = foodsList
    var context: Context? = context

    override fun getCount(): Int {
        return markersList.size
    }

    override fun getItem(position: Int): Any {
        return markersList[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    @SuppressLint("ViewHolder", "InflateParams")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val marker = this.markersList[position]

        val inflater = context!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val markerView = inflater.inflate(R.layout.marker_entry, null)
        markerView.markerImage.setImageResource(marker.image!!)
        markerView.markerName.text = marker.name!!

        return markerView
    }
}