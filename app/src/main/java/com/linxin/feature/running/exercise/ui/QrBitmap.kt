package com.linxin.feature.running.exercise.ui

import com.linxin.R
import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.linxin.feature.running.exercise.domain.QrCodeMatrixGenerator

/** 把二维码内容编码为 Compose ImageBitmap。UI 层薄封装，核心矩阵由 QrCodeMatrixGenerator 生成。 */
fun qrImageBitmap(content: String, size: Int = 512): ImageBitmap {
    val matrix = QrCodeMatrixGenerator.encode(content, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (y in 0 until size) {
        for (x in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[y][x]) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap.asImageBitmap()
}
