package com.example.stickermaker

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    const val GREEN = 0xFF075E54.toInt()
    const val ACCENT = 0xFF128C7E.toInt()
    const val BG = 0xFFF2F5F4.toInt()
    const val GRAY = 0xFFB0B8B5.toInt()

    fun dp(ctx: Context, n: Int) = (n * ctx.resources.displayMetrics.density).toInt()

    fun round(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
    }

    fun clipRound(v: View, radius: Float) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, o: Outline) { o.setRoundRect(0, 0, view.width, view.height, radius) }
        }
        v.clipToOutline = true
    }

    fun button(ctx: Context, text: String, color: Int = ACCENT, onClick: () -> Unit): Button =
        Button(ctx).apply {
            this.text = text
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            stateListAnimator = null
            background = round(color, dp(ctx, 14).toFloat())
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
            setOnClickListener { onClick() }
        }

    fun header(ctx: Context, title: String, left: View? = null, right: View? = null): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(GREEN)
            elevation = dp(ctx, 4).toFloat()
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
            left?.let { addView(it) }
            addView(TextView(ctx).apply {
                text = title; textSize = 20f; setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                if (left != null) setPadding(dp(ctx, 12), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            right?.let { addView(it) }
        }

    fun headerText(ctx: Context, text: String, size: Float = 16f, onClick: () -> Unit) =
        TextView(ctx).apply {
            this.text = text; textSize = size; setTextColor(Color.WHITE)
            setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4))
            setOnClickListener { onClick() }
        }
}
