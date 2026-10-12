package com.adamglassner.daybreak.ui

import android.content.Context
import android.widget.Toast

/** Short confirmation messages (shown as toasts on Android). */
object Notices {
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun show(text: String) {
        app?.let { Toast.makeText(it, text, Toast.LENGTH_SHORT).show() }
    }
}
