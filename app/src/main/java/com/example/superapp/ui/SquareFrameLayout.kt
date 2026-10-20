package com.example.superapp.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** A FrameLayout whose height always matches its width (home grid tiles). */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
