package com.chronolock.feature

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.chronolock.R
import com.chronolock.data.AppTheme
import com.chronolock.data.MascotKind
import com.chronolock.data.Pose

/**
 * The week as a story-sized picture (1080×1920) to post: the character, a headline and a few totals. No app names are
 * drawn, so nothing about which apps were used leaves the phone unless the person writes it.
 */
object ShareCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1920

    fun render(context: Context, theme: AppTheme, mascot: MascotKind, pose: Pose, title: String, headline: String, lines: List<String>, footer: String): Bitmap {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        val colors = (theme.pageColors.takeIf { it.size > 1 } ?: listOf(theme.background, theme.background)).map { it.toInt() }.toIntArray()
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
            Paint().apply { shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), colors, null, Shader.TileMode.CLAMP) })
        ContextCompat.getDrawable(context, mascot.art(pose))?.apply {
            setBounds((WIDTH - 520) / 2, 260, (WIDTH + 520) / 2, 780); draw(canvas)
        }
        fun font(id: Int) = ResourcesCompat.getFont(context, id)
        var y = 880f
        fun text(value: String, size: Float, color: Long, typeface: Int, gap: Float) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color.toInt(); this.typeface = font(typeface) }
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, WIDTH - 160).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
            canvas.save(); canvas.translate(80f, y); layout.draw(canvas); canvas.restore()
            y += layout.height + gap
        }
        text(title, 48f, theme.primary, R.font.pretendard_medium, 40f)
        text(headline, 84f, theme.text, R.font.pretendard_bold, 60f)
        lines.forEach { text(it, 46f, theme.muted, R.font.pretendard_regular, 24f) }
        y = HEIGHT - 200f
        text(footer, 40f, theme.primary, R.font.pretendard_medium, 0f)
        return bitmap
    }

    /** Saves [bitmap] in the app cache and opens the share sheet with it and [message]. */
    fun share(context: Context, bitmap: Bitmap, message: String, fileName: String = "chronolock-week.png") {
        val folder = java.io.File(context.cacheDir, "share").apply { mkdirs() }
        val file = java.io.File(folder, fileName)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".share", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, message)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(Intent.createChooser(send, null))
    }
}
