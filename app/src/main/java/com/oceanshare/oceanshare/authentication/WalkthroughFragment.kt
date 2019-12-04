package com.oceanshare.oceanshare.authentication

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.view.PagerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.oceanshare.oceanshare.R
import kotlinx.android.synthetic.main.fragment_walkthrough.view.*
import java.util.*

class WalkthroughFragment : Fragment() {
    private var listener: OnFragmentInteractionListener? = null
    private var mCallback: Callback? = null

    interface Callback {
        fun showLoginPage()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val inflatedLayout = inflater.inflate(R.layout.fragment_walkthrough, container, false)

        mCallback = activity as Callback?

        val walkthroughImages: IntArray
        if (Locale.getDefault().language == "fr") {
            walkthroughImages = intArrayOf(R.drawable.walkthrough_1_fr, R.drawable.walkthrough_2_fr, R.drawable.walkthrough_3_fr)
        } else {
            walkthroughImages = intArrayOf(R.drawable.walkthrough_1_en, R.drawable.walkthrough_2_en, R.drawable.walkthrough_3_en)
        }

        inflatedLayout.walkthroughPager.adapter = context?.let { WalkthroughPagerAdapter(it, walkthroughImages) }

        inflatedLayout.textAlreadyKnowOceanshare.setOnClickListener {
            inflatedLayout.walkthroughContainer.visibility = View.GONE
            inflatedLayout.buttonStart.visibility = View.VISIBLE
        }

        inflatedLayout.buttonStart.setOnClickListener {
            inflatedLayout.walkthroughContainer.visibility = View.VISIBLE
            inflatedLayout.buttonStart.visibility = View.GONE
        }

        inflatedLayout.buttonOk.setOnClickListener {
            if (inflatedLayout.walkthroughPager.adapter != null && inflatedLayout.walkthroughPager.adapter?.count != null)
            if (inflatedLayout.walkthroughPager.currentItem < inflatedLayout.walkthroughPager.adapter?.count?.minus(1)!!) {
                inflatedLayout.walkthroughPager.setCurrentItem(inflatedLayout.walkthroughPager.currentItem + 1, true)
            } else {
                inflatedLayout.walkthroughPager.setCurrentItem(0, false)
                inflatedLayout.walkthroughContainer.visibility = View.GONE
                inflatedLayout.buttonStart.visibility = View.VISIBLE
                mCallback?.showLoginPage()
            }
            inflatedLayout.walkthroughPager
        }

        return inflatedLayout
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    interface OnFragmentInteractionListener {
        fun onFragmentInteraction(uri: Uri)
    }

    companion object {
        @JvmStatic
        fun newInstance() = WalkthroughFragment().apply {}
    }
}

class WalkthroughPagerAdapter(mContext: Context, private val mResources: IntArray) : PagerAdapter() {
    private val mLayoutInflater: LayoutInflater = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    override fun getCount(): Int {
        return mResources.size
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val itemView: View = mLayoutInflater.inflate(R.layout.walkthrough_pager_item, container, false)
        val imageView: ImageView = itemView.findViewById<View>(R.id.walkthroughImage) as ImageView
        imageView.setImageResource(mResources[position])
        container.addView(itemView)
        return itemView
    }

    override fun isViewFromObject(p0: View, p1: Any): Boolean {
        return p0 === p1
    }

    override fun destroyItem(container: ViewGroup, position: Int, view: Any) {
        container.removeView(view as View?)
    }
}
