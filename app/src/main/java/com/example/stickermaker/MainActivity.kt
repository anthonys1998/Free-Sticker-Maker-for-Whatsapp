package com.example.stickermaker

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private lateinit var list: LinearLayout
    private fun d(n: Int) = Ui.dp(this, n)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.GREEN

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
        root.addView(Ui.header(this, "My Sticker Packs"))

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(16), d(16), d(16), d(8)) }
        root.addView(ScrollView(this).apply { addView(list) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val newBtn = Ui.button(this, "＋  New pack") { newPackDialog() }
        root.addView(newBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(d(16), d(8), d(16), d(16)) })

        setContentView(root)
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        list.removeAllViews()
        val ids = StickerStore.packIds(this)
        if (ids.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No packs yet\n\nTap “New pack” to make your first one."
                gravity = Gravity.CENTER; textSize = 16f; setTextColor(Ui.GRAY)
                setPadding(0, d(80), 0, 0)
            })
            return
        }
        ids.reversed().forEach { id -> list.addView(card(id)) }
    }

    private fun card(id: String): LinearLayout {
        val stickers = StickerStore.stickers(this, id)
        val ready = stickers.size >= StickerStore.MIN
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.round(Color.WHITE, d(16).toFloat())
            elevation = d(2).toFloat()
            setPadding(d(12), d(12), d(12), d(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = d(12) }
            setOnClickListener {
                startActivity(Intent(this@MainActivity, PackActivity::class.java).putExtra(PackActivity.EXTRA_ID, id))
            }

            addView(ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = Ui.round(0xFFE3E8E6.toInt(), d(12).toFloat())
                Ui.clipRound(this, d(12).toFloat())
                stickers.firstOrNull()?.let { setImageURI(Uri.fromFile(it)) }
            }, LinearLayout.LayoutParams(d(64), d(64)))

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(d(14), 0, d(8), 0)
                addView(TextView(this@MainActivity).apply {
                    text = StickerStore.name(this@MainActivity, id)
                    textSize = 17f; setTextColor(0xFF1B1F1E.toInt())
                    setTypeface(typeface, android.graphics.Typeface.BOLD); maxLines = 1
                })
                addView(TextView(this@MainActivity).apply {
                    text = "${stickers.size} sticker${if (stickers.size == 1) "" else "s"}"
                    textSize = 14f; setTextColor(0xFF6B7471.toInt())
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(TextView(this@MainActivity).apply {
                text = if (ready) "Ready" else "Needs ${StickerStore.MIN - stickers.size} more"
                textSize = 12f
                setTextColor(if (ready) Ui.ACCENT else 0xFFB26A00.toInt())
                background = Ui.round(if (ready) 0xFFDDF3EE.toInt() else 0xFFFFF0D9.toInt(), d(20).toFloat())
                setPadding(d(10), d(4), d(10), d(4))
            })
        }
    }

    private fun newPackDialog() {
        val input = EditText(this).apply {
            hint = "Pack name"; setSingleLine()
            setPadding(d(24), d(16), d(24), d(8))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("New pack")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "My Stickers" }
                val id = StickerStore.createPack(this, name)
                startActivity(Intent(this, PackActivity::class.java).putExtra(PackActivity.EXTRA_ID, id))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
