
package com.dn.mark1

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = TextView(this).apply {
            text = "D.N. MARK 1\n\nSYSTEM ONLINE\n\nHello! I am your AI Assistant."
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(10, 15, 30))
        }

        setContentView(screen)
    }
}
