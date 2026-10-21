package com.example.superapp.ui

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.widget.Switch
import com.example.superapp.R

/**
 * One of the Lite mode switches (top of the home screen and of the drawer): leaf chip, title,
 * what it does, and the switch itself, which the whole row toggles. [onChange] gets the user's
 * changes; [show] mirrors the setting without calling it back.
 */
internal class LiteToggle(private val switch: Switch, private val onChange: (Boolean) -> Unit) {
    private var showing = false

    init {
        val context = switch.context
        val text = SpannableStringBuilder(context.getString(R.string.lite_title)).append('\n')
        val detail = text.length
        text.append(context.getString(R.string.lite_detail))
        text.setSpan(AbsoluteSizeSpan(13, true), detail, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(ForegroundColorSpan(context.getColor(R.color.text_secondary)), detail, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        switch.text = text
        switch.setOnCheckedChangeListener { _, on ->
            switch.setCompoundDrawablesRelative(context.liteChip(on), null, null, null)
            if (!showing) onChange(on)
        }
    }

    fun show(on: Boolean) {
        showing = true
        switch.isChecked = on
        switch.setCompoundDrawablesRelative(switch.context.liteChip(on), null, null, null)
        showing = false
    }
}
