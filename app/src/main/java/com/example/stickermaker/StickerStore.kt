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
    const val PACK_ID = "my_sticker_pack"
    const val TRAY = "tray.png"
    const val MAX = 30
    const val MIN = 3
    private const val PREFS = "pack"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun dir(ctx: Context): File = File(ctx.filesDir, "pack").also { it.mkdirs() }

    fun stickers(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.name.startsWith("s_") && f.name.endsWith(".webp") }
            ?.sortedBy { it.name } ?: emptyList()

    fun name(ctx: Context): String = prefs(ctx).getString("name", "My Stickers") ?: "My Stickers"
    fun setName(ctx: Context, n: String) {
        if (n != name(ctx)) { prefs(ctx).edit().putString("name", n).apply(); bump(ctx) }
    }

    fun version(ctx: Context): String = prefs(ctx).getInt("version", 1).toString()
    private fun bump(ctx: Context) {
        prefs(ctx).edit().putInt("version", prefs(ctx).getInt("version", 1) + 1).apply()
    }

    fun remove(ctx: Context, f: File) { f.delete(); bump(ctx); updateTray(ctx) }

    /** Converts any image into a 512x512 transparent-padded WebP under 100 KB. */
    fun addImage(ctx: Context, uri: Uri, index: Int): Boolean {
        return try {
            val bmp = decode(ctx, uri) ?: return false
            val s = 512f / maxOf(bmp.width, bmp.height)
            val w = (bmp.width * s).toInt().coerceAtLeast(1)
            val h = (bmp.height * s).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
            val out = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(scaled, (512 - w) / 2f, (512 - h) / 2f, null)
            val name = "s_%013d_%03d.webp".format(System.currentTimeMillis(), index)
            File(dir(ctx), name).writeBytes(encode(out))
            bump(ctx)
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
    fun updateTray(ctx: Context) {
        val first = stickers(ctx).firstOrNull() ?: return
        val b = BitmapFactory.decodeFile(first.path) ?: return
        val t = Bitmap.createScaledBitmap(b, 96, 96, true)
        FileOutputStream(File(dir(ctx), TRAY)).use { t.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
