package com.datawaves.animalmatch.util

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

/**
 * Tile bitmap cache. Each tile drawable is decoded once at the current target tile pixel size
 * (the largest size needed across the active board) and reused for every draw. The cache key is
 * "${resName}@${targetPx}". Decode is via inSampleSize-based bounds reading to avoid loading the
 * full bitmap before downscaling.
 *
 * Memory cap = 1/6 of process maxMemory (typical: 24–40MB for modern phones), so safely caches
 * dozens of tile faces at 200–300px.
 */
object AssetCache {
    private val cache: LruCache<String, Bitmap> = run {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        object : LruCache<String, Bitmap>(maxKb / 6) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    fun getTile(context: Context, drawableName: String, targetPx: Int): Bitmap {
        val key = "$drawableName@$targetPx"
        cache.get(key)?.let { return it }
        val res = context.resources
        val id = res.getIdentifier(drawableName, "drawable", context.packageName)
        require(id != 0) { "Drawable not found: $drawableName" }
        val bmp = decodeScaled(res, id, targetPx, targetPx)
        cache.put(key, bmp)
        return bmp
    }

    fun clear() { cache.evictAll() }

    private fun decodeScaled(res: Resources, resId: Int, reqW: Int, reqH: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(res, resId, bounds)
        val sample = calcInSampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeResource(res, resId, opts)
        // Scale to exact size for crisp tiles
        return if (raw.width == reqW && raw.height == reqH) raw
        else Bitmap.createScaledBitmap(raw, reqW, reqH, true).also { if (it !== raw) raw.recycle() }
    }

    private fun calcInSampleSize(w: Int, h: Int, reqW: Int, reqH: Int): Int {
        if (w <= reqW && h <= reqH) return 1
        var s = 1
        while ((w / (s * 2)) >= reqW && (h / (s * 2)) >= reqH) s *= 2
        return s
    }
}
