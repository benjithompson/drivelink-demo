package com.drivelink.core.designsystem.art

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Icons that Material Icons does not provide. Original 24 x 24 artwork. */
object DlIcons {
    /** Four-blade fan, used for climate. */
    val Fan: ImageVector by lazy {
        ImageVector.Builder("Fan", 24.dp, 24.dp, 24f, 24f).apply {
            for (i in 0 until 4) {
                group(rotate = 90f * i, pivotX = 12f, pivotY = 12f) {
                    path(fill = SolidColor(Color.Black)) {
                        moveTo(12f, 10.4f)
                        curveTo(10.1f, 8.6f, 10.0f, 4.8f, 12.8f, 3.0f)
                        curveTo(15.6f, 3.6f, 16.2f, 7.6f, 13.6f, 10.6f)
                        close()
                    }
                }
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 10.6f)
                arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 13.4f)
                arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 10.6f)
                close()
            }
        }.build()
    }
}
