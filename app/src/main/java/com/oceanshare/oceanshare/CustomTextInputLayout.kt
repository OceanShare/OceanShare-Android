package com.oceanshare.oceanshare

import android.content.Context
import android.support.v4.graphics.drawable.DrawableCompat
import android.graphics.ColorFilter
import android.support.annotation.Nullable
import android.support.design.widget.TextInputLayout
import android.util.AttributeSet

class CustomTextInputLayout : TextInputLayout {

    private val backgroundDefaultColorFilter: ColorFilter?
        @Nullable
        get() {
            var defaultColorFilter: ColorFilter? = null
            if (editText != null && editText!!.background != null)
                defaultColorFilter = DrawableCompat.getColorFilter(editText!!.background)
            return defaultColorFilter
        }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun setError(@Nullable error: CharSequence?) {
        val defaultColorFilter = backgroundDefaultColorFilter
        super.setError(error)
        //Reset EditText's background color to default.
        updateBackgroundColorFilter(defaultColorFilter)
    }

    override fun drawableStateChanged() {
        val defaultColorFilter = backgroundDefaultColorFilter
        super.drawableStateChanged()
        //Reset EditText's background color to default.
        updateBackgroundColorFilter(defaultColorFilter)
    }

    private fun updateBackgroundColorFilter(colorFilter: ColorFilter?) {
        if (editText != null && editText!!.background != null)
            editText!!.background.colorFilter = colorFilter
    }
}