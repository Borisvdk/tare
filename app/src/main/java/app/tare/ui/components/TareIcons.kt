package app.tare.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Icons the Material set doesn't have in the shape we want. 24×24 viewport, 2 px strokes. */
object TareIcons {
    val Barcode: ImageVector by lazy {
        ImageVector.Builder("Barcode", 24.dp, 24.dp, 24f, 24f).apply {
            // Corner brackets of the viewfinder.
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                moveTo(3f, 8f); lineTo(3f, 5f); quadTo(3f, 3f, 5f, 3f); lineTo(8f, 3f)
                moveTo(16f, 3f); lineTo(19f, 3f); quadTo(21f, 3f, 21f, 5f); lineTo(21f, 8f)
                moveTo(21f, 16f); lineTo(21f, 19f); quadTo(21f, 21f, 19f, 21f); lineTo(16f, 21f)
                moveTo(8f, 21f); lineTo(5f, 21f); quadTo(3f, 21f, 3f, 19f); lineTo(3f, 16f)
            }
            // Bars.
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) {
                listOf(7.5f, 10f, 13.5f, 16.5f).forEach { x -> moveTo(x, 8f); lineTo(x, 16f) }
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 0.9f, strokeLineCap = StrokeCap.Round) {
                listOf(11.7f, 15f).forEach { x -> moveTo(x, 8f); lineTo(x, 16f) }
            }
        }.build()
    }
}
