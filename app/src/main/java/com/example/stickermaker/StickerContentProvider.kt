package com.example.stickermaker

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

class StickerContentProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH)
    private lateinit var authority: String

    override fun onCreate(): Boolean {
        authority = StickerStore.authority(context!!)
        matcher.addURI(authority, "metadata", METADATA)
        matcher.addURI(authority, "metadata/*", METADATA_ONE)
        matcher.addURI(authority, "stickers/*", STICKERS)
        matcher.addURI(authority, "stickers_asset/*/*", ASSET)
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val ctx = context!!
        return when (matcher.match(uri)) {
            METADATA, METADATA_ONE -> {
                val c = MatrixCursor(arrayOf(
                    "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher",
                    "sticker_pack_icon", "android_play_store_link", "ios_app_download_link",
                    "sticker_pack_publisher_email", "sticker_pack_publisher_website",
                    "sticker_pack_privacy_policy_website", "sticker_pack_license_agreement_website",
                    "image_data_version", "whatsapp_will_not_cache_stickers", "animated_sticker_pack"
                ))
                val only = if (matcher.match(uri) == METADATA_ONE) uri.lastPathSegment else null
                StickerStore.packIds(ctx)
                    .filter { only == null || it == only }
                    // WhatsApp rejects packs below its minimum, so only expose valid ones
                    .filter { StickerStore.stickers(ctx, it).size >= StickerStore.MIN }
                    .forEach { id ->
                        c.addRow(arrayOf<Any>(
                            id, StickerStore.name(ctx, id), "Sticker Maker",
                            StickerStore.TRAY, "", "", "", "", "", "",
                            StickerStore.version(ctx, id), 0, 0
                        ))
                    }
                c
            }
            STICKERS -> {
                val id = uri.lastPathSegment ?: return null
                val c = MatrixCursor(arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text"))
                if (id in StickerStore.packIds(ctx)) {
                    StickerStore.stickers(ctx, id).forEach { c.addRow(arrayOf<Any>(it.name, "😀", "Sticker")) }
                }
                c
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (matcher.match(uri) != ASSET) return null
        val id = uri.pathSegments[1]
        if (id !in StickerStore.packIds(context!!)) throw FileNotFoundException(id)
        val fileName = File(uri.pathSegments[2]).name // strip any path tricks
        val f = File(StickerStore.packDir(context!!, id), fileName)
        if (!f.exists()) throw FileNotFoundException(fileName)
        return AssetFileDescriptor(
            ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH
        )
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        METADATA -> "vnd.android.cursor.dir/vnd.$authority.metadata"
        METADATA_ONE -> "vnd.android.cursor.item/vnd.$authority.metadata"
        STICKERS -> "vnd.android.cursor.dir/vnd.$authority.stickers"
        ASSET -> if (uri.lastPathSegment?.endsWith(".png") == true) "image/png" else "image/webp"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    companion object {
        private const val METADATA = 1
        private const val METADATA_ONE = 2
        private const val STICKERS = 3
        private const val ASSET = 4
    }
}
