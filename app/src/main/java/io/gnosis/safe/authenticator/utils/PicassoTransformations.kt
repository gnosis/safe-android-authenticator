package io.gnosis.safe.authenticator.utils

import android.graphics.*
import com.squareup.picasso.Transformation


object CircleTransformation: Transformation {
    override fun key() = "Circle Transformation"

    override fun transform(source: Bitmap?): Bitmap? {
        source ?: return null
        val size = source.width.coerceAtMost(source.height)

        val x = (source.width - size) / 2
        val y = (source.height - size) / 2

        val squaredBitmap = Bitmap.createBitmap(source, x, y, size, size)
        if (squaredBitmap != source) {
            source.recycle()
        }

        val bitmap = Bitmap.createBitmap(size, size, source.config)

        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.shader = BitmapShader(squaredBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        paint.isAntiAlias = true

        val r = size / 2f
        canvas.drawCircle(r, r, r, paint)

        squaredBitmap.recycle()
        return bitmap
    }

}