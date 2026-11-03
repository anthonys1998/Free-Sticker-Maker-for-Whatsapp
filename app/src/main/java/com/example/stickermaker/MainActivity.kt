package com.example.stickermaker

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var nameEdit: EditText
    private lateinit var countText: TextView
    private lateinit var grid: GridView
    private var files: List<File> = emptyList()
    private var cell = 300

    // Multi-select picker: choose many images in one go
    private val picker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) importImages(uris)
    }

    private val whatsappResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) {
            val err = r.data?.getStringExtra("validation_error")
            if (err != null) toast("WhatsApp: $err")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()
        cell = (resources.displayMetrics.widthPixels - 2 * pad) / 3

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        nameEdit = EditText(this).apply {
            hint = "Pack name"
            setText(StickerStore.name(this@MainActivity))
            setSingleLine()
        }
        val pickBtn = Button(this).apply { text = "Select images (multiple)"; setOnClickListener { picker.launch("image/*") } }
        countText = TextView(this).apply { setPadding(0, pad / 2, 0, pad / 2) }
        grid = GridView(this).apply { numColumns = 3; stretchMode = GridView.NO_STRETCH; columnWidth = cell }
        val addBtn = Button(this).apply { text = "Add to WhatsApp"; setOnClickListener { addToWhatsApp() } }

        root.addView(nameEdit)
        root.addView(pickBtn)
        root.addView(countText)
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(addBtn)
        setContentView(root)

        grid.setOnItemClickListener { _, _, pos, _ ->
            val f = files[pos]
            android.app.AlertDialog.Builder(this)
                .setTitle("Remove this sticker?")
                .setPositiveButton("Remove") { _, _ -> StickerStore.remove(this, f); refresh() }
                .setNegativeButton("Cancel", null).show()
        }
        refresh()
    }

    private fun refresh() {
        files = StickerStore.stickers(this)
        countText.text = "${files.size}/${StickerStore.MAX} stickers (min ${StickerStore.MIN}) • tap a sticker to remove"
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = files.size
            override fun getItem(p: Int) = files[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, parent: ViewGroup): View {
                val iv = (v as? ImageView) ?: ImageView(this@MainActivity).apply {
                    layoutParams = AbsListView.LayoutParams(cell, cell)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(0xFFE0E0E0.toInt())
                    setPadding(4, 4, 4, 4)
                }
                iv.setImageURI(Uri.fromFile(files[p]))
                return iv
            }
        }
    }

    private fun importImages(uris: List<Uri>) {
        val room = StickerStore.MAX - StickerStore.stickers(this).size
        if (room <= 0) { toast("Pack is full (${StickerStore.MAX})"); return }
        val chosen = uris.take(room)
        if (uris.size > room) toast("Only adding $room (30 max per pack)")
        toast("Processing ${chosen.size} images…")
        executor.execute {
            var ok = 0
            chosen.forEachIndexed { i, u -> if (StickerStore.addImage(this, u, i)) ok++ }
            StickerStore.updateTray(this)
            runOnUiThread { refresh(); toast("Added $ok sticker(s)") }
        }
    }

    private fun addToWhatsApp() {
        if (StickerStore.stickers(this).size < StickerStore.MIN) {
            toast("Add at least ${StickerStore.MIN} stickers first"); return
        }
        val n = nameEdit.text.toString().trim().ifEmpty { "My Stickers" }
        StickerStore.setName(this, n)
        StickerStore.updateTray(this)
        val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
            putExtra("sticker_pack_id", StickerStore.PACK_ID)
            putExtra("sticker_pack_authority", "$packageName.stickercontentprovider")
            putExtra("sticker_pack_name", n)
        }
        try { whatsappResult.launch(intent) }
        catch (e: ActivityNotFoundException) { toast("WhatsApp isn't installed or is out of date") }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
