package com.mycloset.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Icone proprie (linea arrotondata, 24x24): niente libreria di icone aggiuntiva. */
object AppIcons {
    private fun icon(name: String, width: Float = 1.8f, block: ImageVector.Builder.(Float) -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply { block(width) }.build()

    private fun ImageVector.Builder.line(w: Float, b: PathBuilder.() -> Unit) = path(
        fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = w,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = b,
    )

    val Today: ImageVector = icon("today") { w ->
        line(w) { moveTo(16f, 12f); arcTo(4f, 4f, 0f, true, true, 8f, 12f); arcTo(4f, 4f, 0f, true, true, 16f, 12f); close() }
        line(w) {
            moveTo(12f, 2.5f); lineTo(12f, 5f); moveTo(12f, 19f); lineTo(12f, 21.5f)
            moveTo(2.5f, 12f); lineTo(5f, 12f); moveTo(19f, 12f); lineTo(21.5f, 12f)
            moveTo(5.3f, 5.3f); lineTo(7f, 7f); moveTo(17f, 7f); lineTo(18.7f, 5.3f)
            moveTo(5.3f, 18.7f); lineTo(7f, 17f); moveTo(17f, 17f); lineTo(18.7f, 18.7f)
        }
    }

    val Hanger: ImageVector = icon("hanger") { w ->
        line(w) {
            moveTo(12f, 10f); verticalLineTo(8.5f); arcToRelative(2.2f, 2.2f, 0f, true, false, -2.2f, -2.2f)
            moveTo(12f, 10f); lineTo(3f, 17f); arcToRelative(1.2f, 1.2f, 0f, false, false, 0.8f, 2f)
            horizontalLineTo(20.2f); arcToRelative(1.2f, 1.2f, 0f, false, false, 0.8f, -2f); close()
        }
    }

    val Shirt: ImageVector = icon("shirt") { w ->
        line(w) {
            moveTo(8f, 3.5f); lineTo(3f, 7f); lineTo(5.5f, 10.5f); lineTo(8f, 9f); verticalLineTo(20f); horizontalLineTo(16f)
            verticalLineTo(9f); lineTo(18.5f, 10.5f); lineTo(21f, 7f); lineTo(16f, 3.5f)
            curveTo(15.5f, 5f, 14f, 5.8f, 12f, 5.8f); curveTo(10f, 5.8f, 8.5f, 5f, 8f, 3.5f); close()
        }
    }

    val Calendar: ImageVector = icon("calendar") { w ->
        line(w) {
            moveTo(6.5f, 5.5f); horizontalLineToRelative(11f); arcToRelative(2.5f, 2.5f, 0f, false, true, 2.5f, 2.5f)
            verticalLineToRelative(9.5f); arcToRelative(2.5f, 2.5f, 0f, false, true, -2.5f, 2.5f); horizontalLineToRelative(-11f)
            arcToRelative(2.5f, 2.5f, 0f, false, true, -2.5f, -2.5f); verticalLineTo(8f); arcToRelative(2.5f, 2.5f, 0f, false, true, 2.5f, -2.5f); close()
            moveTo(4f, 10f); horizontalLineTo(20f); moveTo(8.5f, 3.5f); verticalLineTo(7.5f); moveTo(15.5f, 3.5f); verticalLineTo(7.5f)
        }
    }

    val Chart: ImageVector = icon("chart", width = 2.6f) { w ->
        line(w) { moveTo(6f, 20f); verticalLineTo(13f); moveTo(12f, 20f); verticalLineTo(5f); moveTo(18f, 20f); verticalLineTo(10f) }
    }

    val Sparkle: ImageVector = icon("sparkle") { w ->
        line(w) {
            moveTo(12f, 3f); lineTo(13.9f, 9.4f); lineTo(20.5f, 12f); lineTo(13.9f, 14.6f); lineTo(12f, 21f)
            lineTo(10.1f, 14.6f); lineTo(3.5f, 12f); lineTo(10.1f, 9.4f); close()
        }
    }

    val Camera: ImageVector = icon("camera") { w ->
        line(w) {
            moveTo(4f, 8f); horizontalLineTo(7f); lineTo(8.6f, 5.5f); horizontalLineTo(15.4f); lineTo(17f, 8f); horizontalLineTo(20f)
            verticalLineTo(19f); horizontalLineTo(4f); close()
            moveTo(15.5f, 13.2f); arcTo(3.5f, 3.5f, 0f, true, true, 8.5f, 13.2f); arcTo(3.5f, 3.5f, 0f, true, true, 15.5f, 13.2f); close()
        }
    }

    val Gallery: ImageVector = icon("gallery") { w ->
        line(w) {
            moveTo(5.5f, 4f); horizontalLineTo(18.5f); arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f); verticalLineTo(18.5f)
            arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f); horizontalLineTo(5.5f); arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
            verticalLineTo(5.5f); arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, -1.5f); close()
            moveTo(4f, 16f); lineTo(9f, 11f); lineTo(14f, 16f); moveTo(13f, 15f); lineTo(15.5f, 12.5f); lineTo(20f, 17f)
        }
    }

    val Bag: ImageVector = icon("bag") { w ->
        line(w) {
            moveTo(5f, 8f); horizontalLineTo(19f); lineTo(20f, 20f); horizontalLineTo(4f); close()
            moveTo(9f, 8f); verticalLineTo(6.5f); arcToRelative(3f, 3f, 0f, false, true, 6f, 0f); verticalLineTo(8f)
        }
    }

    val Suitcase: ImageVector = icon("suitcase") { w ->
        line(w) {
            moveTo(5f, 8f); horizontalLineTo(19f); arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f); verticalLineTo(18f)
            arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f); horizontalLineTo(5f); arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
            verticalLineTo(9.5f); arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, -1.5f); close()
            moveTo(9f, 8f); verticalLineTo(6f); horizontalLineTo(15f); verticalLineTo(8f)
        }
    }

    /** Regolazioni (tre cursori). */
    val Settings: ImageVector = icon("settings") { w ->
        line(w) {
            moveTo(4f, 7f); horizontalLineTo(6.5f); moveTo(11.5f, 7f); horizontalLineTo(20f)
            moveTo(4f, 12f); horizontalLineTo(12.5f); moveTo(17.5f, 12f); horizontalLineTo(20f)
            moveTo(4f, 17f); horizontalLineTo(6.5f); moveTo(11.5f, 17f); horizontalLineTo(20f)
            moveTo(11.5f, 7f); arcTo(2.5f, 2.5f, 0f, true, true, 6.5f, 7f); arcTo(2.5f, 2.5f, 0f, true, true, 11.5f, 7f)
            moveTo(17.5f, 12f); arcTo(2.5f, 2.5f, 0f, true, true, 12.5f, 12f); arcTo(2.5f, 2.5f, 0f, true, true, 17.5f, 12f)
            moveTo(11.5f, 17f); arcTo(2.5f, 2.5f, 0f, true, true, 6.5f, 17f); arcTo(2.5f, 2.5f, 0f, true, true, 11.5f, 17f)
        }
    }
}
