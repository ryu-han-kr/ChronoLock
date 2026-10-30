package com.chronolock.feature

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.chronolock.R
import com.chronolock.domain.DeskQr
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Drawing the printed code and scanning it back. Scanning uses Google Play services' scanner, so no camera permission. */
internal object DeskQrCode {
    /** The code as black-on-white squares, [size] pixels across with a quiet margin. */
    fun bitmap(secret: String, size: Int = 720): Bitmap {
        val matrix = QRCodeWriter().encode(DeskQr.payload(secret), BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 2))
        return createBitmap(size, size).also { bitmap ->
            for (x in 0 until size) for (y in 0 until size) bitmap[x, y] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
    }

    /** A page to print: the code with a line under it saying what it is for. */
    fun printable(context: Context, secret: String, caption: String): Bitmap {
        val code = bitmap(secret, 900)
        val page = createBitmap(1080, 1240)
        Canvas(page).apply {
            drawColor(Color.WHITE)
            drawBitmap(code, 90f, 80f, null)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK; textSize = 44f; textAlign = Paint.Align.CENTER
                typeface = ResourcesCompat.getFont(context, R.font.pretendard_bold)
            }
            drawText(caption, 540f, 1120f, paint)
        }
        return page
    }

    /** Opens the scanner; [done] gets the code's text, or null when it was closed or could not open. */
    fun scan(context: Context, done: (String?) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { done(it.rawValue) }
            .addOnCanceledListener { done(null) }
            .addOnFailureListener { android.util.Log.w("ChronoLock", "QR scanner failed", it); done(null) }
    }
}

/** Settings: stopping a focus only by scanning a printed code kept somewhere out of reach. */
@Composable
internal fun ColumnScope.DeskQrCard(secret: String?, focusRunning: Boolean, set: (Boolean) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().toggleable(value = secret != null, enabled = !focusRunning, role = Role.Switch, onValueChange = set)
        .testTag("desk-qr"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("책상 QR로만 집중 끄기"), style = MaterialTheme.typography.titleSmall)
            Text(tr("QR을 인쇄해 책상이나 거실처럼 바로 손이 닿지 않는 곳에 붙여 두세요. 집중을 중간에 끄려면 그 QR을 찍어야 해요."),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = secret != null, onCheckedChange = null, enabled = !focusRunning)
    }
    if (focusRunning) Text(tr("집중이 끝난 뒤에 바꿀 수 있어요."), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    if (secret != null) {
        val code = remember(secret) { DeskQrCode.bitmap(secret).asImageBitmap() }
        Image(code, contentDescription = tr("집중 QR"), modifier = Modifier.size(160.dp).align(Alignment.CenterHorizontally).testTag("desk-qr-image"))
        val caption = tr("ChronoLock 집중 QR · 여기서 찍어야 집중이 끝나요")
        OutlinedButton(onClick = {
            runCatching { ShareCard.share(context, DeskQrCode.printable(context, secret, caption), caption, "chronolock-desk-qr.png") }
                .onFailure { android.util.Log.w("ChronoLock", "Desk QR share failed", it) }
        }, modifier = Modifier.fillMaxWidth()) { Text(tr("QR 이미지 저장·인쇄")) }
    }
}
