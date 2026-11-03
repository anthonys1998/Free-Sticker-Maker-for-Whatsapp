package com.example.stickermaker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object StickerStore {
    const val TRAY = "tray.png"
    const val MAX = 30   // WhatsApp limit per pack
    const val MIN = 3    // WhatsApp refuses packs with fewer than 3 stickers

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("packs", Context.MODE_PRIVATE)

    fun authority(ctx: Context) = ctx.packageName + ".stickercontentprovider"

    // ---- packs ----
    fun packIds(ctx: Context): List<String> =
        (prefs(ctx).getString("ids", "") ?: "").split(",").filter { it.isNotBlank() }

    fun createPack(ctx: Context, name: String): String {
        val id = "pack_" + System.currentTimeMillis()
        prefs(ctx).edit()
            .putString("ids", (packIds(ctx) + id).joinToString(","))
            .putString("name_$id", name)
            .putInt("ver_$id", 1)
            .apply()
        packDir(ctx, id)
        return id
    }

    fun deletePack(ctx: Context, id: String) {
        packDir(ctx, id).deleteRecursively()
        prefs(ctx).edit()
            .putString("ids", packIds(ctx).filter { it != id }.joinToString(","))
            .remove("name_$id").remove("ver_$id").apply()
    }

    fun packDir(ctx: Context, id: String): File = File(ctx.filesDir, "packs/$id").also { it.mkdirs() }

    fun name(ctx: Context, id: String): String = prefs(ctx).getString("name_$id", "My Stickers") ?: "My Stickers"
    fun setName(ctx: Context, id: String, n: String) {
        if (n != name(ctx, id)) { prefs(ctx).edit().putString("name_$id", n).apply(); bump(ctx, id) }
    }

    fun version(ctx: Context, id: String): String = prefs(ctx).getInt("ver_$id", 1).toString()
    private fun bump(ctx: Context, id: String) {
        prefs(ctx).edit().putInt("ver_$id", prefs(ctx).getInt("ver_$id", 1) + 1).apply()
    }

    // ---- stickers ----
    fun stickers(ctx: Context, id: String): List<File> =
        packDir(ctx, id).listFiles { f -> f.name.startsWith("s_") && f.name.endsWith(".webp") }
            ?.sortedBy { it.name } ?: emptyList()

    fun remove(ctx: Context, id: String, f: File) { f.delete(); bump(ctx, id); updateTray(ctx, id) }

    /** Converts any image into a 512x512 transparent-padded WebP under 100 KB. */
    fun addImage(ctx: Context, id: String, uri: Uri, index: Int): Boolean {
        return try {
            val bmp = decode(ctx, uri) ?: return false
            val s = 512f / maxOf(bmp.width, bmp.height)
            val w = (bmp.width * s).toInt().coerceAtLeast(1)
            val h = (bmp.height * s).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
            val out = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(scaled, (512 - w) / 2f, (512 - h) / 2f, null)
            val fileName = "s_%013d_%03d.webp".format(System.currentTimeMillis(), index)
            File(packDir(ctx, id), fileName).writeBytes(encode(out))
            bump(ctx, id)
            true
        } catch (e: Exception) { false }
    }

    private fun encode(bmp: Bitmap): ByteArray {
        val fmt = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY
                  else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        var q = 90
        while (true) {
            val b = ByteArrayOutputStream()
            bmp.compress(fmt, q, b)
            if (b.size() <= 100 * 1024 || q <= 10) return b.toByteArray()
            q -= 10
        }
    }

    private fun decode(ctx: Context, uri: Uri): Bitmap? {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val orientation = try {
            cr.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) { ExifInterface.ORIENTATION_NORMAL }

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        }
        return if (m.isIdentity) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /** WhatsApp requires a 96x96 PNG tray icon (<50 KB). Uses the first sticker. */
    fun updateTray(ctx: Context, id: String) {
        val first = stickers(ctx, id).firstOrNull() ?: return
        val b = BitmapFactory.decodeFile(first.path) ?: return
        val t = Bitmap.createScaledBitmap(b, 96, 96, true)
        FileOutputStream(File(packDir(ctx, id), TRAY)).use { t.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
