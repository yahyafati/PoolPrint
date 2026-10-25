package com.yahyafati.poolprint.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The few Lucide icons the design uses (lucide.dev, ISC licence), as stroked 24×24 vectors tinted by the caller. */
object PoolIcons {
    val Copy: ImageVector by lazy { lucide("copy", "M8 8h14v14H8z", "M4 16V4h12") }
    val Share: ImageVector by lazy { lucide("share", "M4 12v8h16v-8", "m16 6-4-4-4 4", "M12 2v13") }
    val Refresh: ImageVector by lazy {
        lucide(
            "refresh-cw",
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
            "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
            "M8 16H3v5",
        )
    }
    val Alert: ImageVector by lazy {
        lucide(
            "triangle-alert",
            "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
            "M12 9v4",
            "M12 17h.01",
        )
    }
    val Check: ImageVector by lazy { lucide("check", "M20 6 9 17l-5-5", width = 3f, cap = StrokeCap.Square) }

    private fun lucide(name: String, vararg paths: String, width: Float = 2.2f, cap: StrokeCap = StrokeCap.Round): ImageVector {
        val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (path in paths) {
            builder.addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = cap,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
