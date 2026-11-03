package com.example.stickermaker

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.util.concurrent.Executors

class PackActivity : ComponentActivity() {

    companion object { const val EXTRA_ID = "pack_id" }

    private lateinit var id: String
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var nameEdit: EditText
    private lateinit var countText: TextView
    private lateinit var grid: GridView
    private lateinit var addBtn: Button
    private var files: List<File> = emptyList()
    private var cell = 300
    private fun d(n: Int) = Ui.dp(this, n)

    // Multi-select picker: choose many images in one go
    private val picker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) importImages(uris)
    }

    private val whatsappResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) {
            r.data?.getStringExtra("validation_error")?.let { toast("WhatsApp: $it") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packId = intent.getStringExtra(EXTRA_ID)
        if (packId == null || packId !in StickerStore.packIds(this)) { finish(); return }
        id = packId
        window.statusBarColor = Ui.GREEN

        val pad = d(16)
        val gap = d(8)
        cell = (resources.displayMetrics.widthPixels - 2 * pad - 2 * gap) / 3

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
        root.addView(Ui.header(
            this, "Edit pack",
            left = Ui.headerText(this, "←", 24f) { finish() },
            right = Ui.headerText(this, "Delete") { confirmDelete() }
        ))

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        nameEdit = EditText(this).apply {
            hint = "Pack name"; setSingleLine(); textSize = 17f
            setText(StickerStore.name(this@PackActivity, this@PackActivity.id))
            background = Ui.round(Color.WHITE, d(12).toFloat())
            setPadding(d(16), d(14), d(16), d(14))
        }
        body.addView(nameEdit, lp().apply { bottomMargin = d(12) })

        body.addView(Ui.button(this, "＋  Add images (select many)", Ui.ACCENT) { picker.launch("image/*") }, lp())

        countText = TextView(this).apply { textSize = 13f; setTextColor(0xFF6B7471.toInt()); setPadding(d(2), d(12), 0, d(8)) }
        body.addView(countText)

        grid = GridView(this).apply {
            numColumns = 3; horizontalSpacing = gap; verticalSpacing = gap
            stretchMode = GridView.NO_STRETCH; columnWidth = cell
            selector = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
        }
        body.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        addBtn = Ui.button(this, "Add to WhatsApp", Ui.GREEN) { addToWhatsApp() }
        body.addView(addBtn, lp().apply { topMargin = d(12) })

        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        grid.setOnItemClickListener { _, _, pos, _ ->
            val f = files[pos]
            android.app.AlertDialog.Builder(this)
                .setTitle("Remove this sticker?")
                .setPositiveButton("Remove") { _, _ -> StickerStore.remove(this, id, f); refresh() }
                .setNegativeButton("Cancel", null).show()
        }
        refresh()
    }

    private fun lp() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    override fun onPause() {
        super.onPause()
        if (::nameEdit.isInitialized) {
            StickerStore.setName(this, id, nameEdit.text.toString().trim().ifEmpty { "My Stickers" })
        }
    }

    private fun refresh() {
        files = StickerStore.stickers(this, id)
        val n = files.size
        countText.text = "$n / ${StickerStore.MAX} stickers  •  WhatsApp requires at least ${StickerStore.MIN}  •  tap one to remove"
        val ready = n >= StickerStore.MIN
        addBtn.text = if (ready) "Add to WhatsApp" else "Add ${StickerStore.MIN - n} more to continue"
        addBtn.background = Ui.round(if (ready) Ui.GREEN else Ui.GRAY, d(14).toFloat())

        val r = d(12).toFloat()
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = files.size
            override fun getItem(p: Int) = files[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, parent: ViewGroup): View =
                FrameLayout(this@PackActivity).apply {
                    layoutParams = AbsListView.LayoutParams(cell, cell)
                    background = Ui.round(0xFFE3E8E6.toInt(), r)
                    Ui.clipRound(this, r)
                    addView(ImageView(this@PackActivity).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setPadding(d(4), d(4), d(4), d(4))
                        setImageURI(Uri.fromFile(files[p]))
                    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    addView(TextView(this@PackActivity).apply {
                        text = "✕"; textSize = 11f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x99000000.toInt()) }
                    }, FrameLayout.LayoutParams(d(22), d(22), Gravity.TOP or Gravity.END).apply { setMargins(0, d(6), d(6), 0) })
                }
        }
    }

    private fun importImages(uris: List<Uri>) {
        val room = StickerStore.MAX - StickerStore.stickers(this, id).size
        if (room <= 0) { toast("Pack is full (${StickerStore.MAX} max)"); return }
        val chosen = uris.take(room)
        if (uris.size > room) toast("Only adding $room (${StickerStore.MAX} max per pack)")
        else toast("Processing ${chosen.size} image(s)…")
        executor.execute {
            var ok = 0
            chosen.forEachIndexed { i, u -> if (StickerStore.addImage(this, id, u, i)) ok++ }
            StickerStore.updateTray(this, id)
            runOnUiThread { refresh(); toast("Added $ok sticker(s)") }
        }
    }

    private fun addToWhatsApp() {
        if (files.size < StickerStore.MIN) {
            toast("WhatsApp requires at least ${StickerStore.MIN} stickers per pack"); return
        }
        val n = nameEdit.text.toString().trim().ifEmpty { "My Stickers" }
        StickerStore.setName(this, id, n)
        StickerStore.updateTray(this, id)
        val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
            putExtra("sticker_pack_id", id)
            putExtra("sticker_pack_authority", StickerStore.authority(this@PackActivity))
            putExtra("sticker_pack_name", n)
        }
        try { whatsappResult.launch(intent) }
        catch (e: ActivityNotFoundException) { toast("WhatsApp isn't installed or is out of date") }
    }

    private fun confirmDelete() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete this pack?")
            .setMessage("This removes the pack and all its stickers from this app.")
            .setPositiveButton("Delete") { _, _ -> StickerStore.deletePack(this, id); finish() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
