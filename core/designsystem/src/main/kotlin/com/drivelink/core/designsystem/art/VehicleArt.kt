package com.drivelink.core.designsystem.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.tooling.preview.Preview

/** Body style of the original DriveLink vehicle illustrations. */
enum class BodyStyle { Crossover, Sedan }

/** Paint options for the demo vehicles. Names are invented. */
object VehiclePaint {
    val ArcticWhite = Color(0xFFF1F3F5)
    val GlacierBlue = Color(0xFF7FA7C9)
    val GraphiteGray = Color(0xFF4A5059)
    val MidnightBlack = Color(0xFF1C1F24)
    val EmberRed = Color(0xFFB3322A)
    val SageGreen = Color(0xFF8DA399)
}

private const val VIEW_W = 360f
private const val VIEW_H = 150f

private data class SideProfile(
    val body: String,
    val glass: String,
    val pillar: String,
    val frontWheelX: Float,
    val rearWheelX: Float,
    val wheelY: Float,
    val wheelR: Float,
    val headLight: String,
    val tailLight: String,
    val beltLine: String,
)

// Viewport 360 x 150, vehicle faces left. Original shapes, not traced from any product.
private val CrossoverProfile = SideProfile(
    body = "M16,112 L16,92 C16,82 24,77 38,75 L84,68 C102,50 124,39 152,37 L250,36 " +
        "C278,37 298,50 314,66 L332,72 C342,75 346,83 346,93 L346,112 L304,112 " +
        "A32,32 0 0 0 240,112 L122,112 A32,32 0 0 0 58,112 Z",
    glass = "M96,70 C112,55 130,46 154,44 L248,43 C268,44 286,53 300,68 L96,70 Z",
    pillar = "M196,43 L200,43 L200,70 L194,70 Z",
    frontWheelX = 90f,
    rearWheelX = 272f,
    wheelY = 112f,
    wheelR = 27f,
    headLight = "M18,86 L52,82 L52,86 L20,90 Z",
    tailLight = "M318,72 L344,80 L344,86 L318,78 Z",
    beltLine = "M58,92 L300,90",
)

private val SedanProfile = SideProfile(
    body = "M14,108 L14,94 C14,86 22,82 36,80 L96,74 C118,56 140,46 170,44 L236,44 " +
        "C266,46 290,60 306,74 L334,80 C344,82 348,88 348,96 L348,108 L304,108 " +
        "A30,30 0 0 0 244,108 L118,108 A30,30 0 0 0 58,108 Z",
    glass = "M108,74 C126,59 146,51 172,50 L234,50 C258,52 278,62 292,74 L108,74 Z",
    pillar = "M204,50 L208,50 L208,74 L202,74 Z",
    frontWheelX = 88f,
    rearWheelX = 274f,
    wheelY = 108f,
    wheelR = 25f,
    headLight = "M16,90 L50,86 L50,90 L18,94 Z",
    tailLight = "M318,78 L346,86 L346,91 L318,84 Z",
    beltLine = "M58,94 L304,92",
)

private fun String.toPath(): Path = PathParser().parsePathString(this).toPath()

/**
 * Flat side-profile vehicle illustration. Width-driven; height follows a 360:150 ratio.
 */
@Composable
fun VehicleSideView(
    modifier: Modifier = Modifier,
    style: BodyStyle = BodyStyle.Crossover,
    paint: Color = VehiclePaint.ArcticWhite,
    lightsOn: Boolean = false,
) {
    val profile = if (style == BodyStyle.Crossover) CrossoverProfile else SedanProfile
    val paths = remember(profile) {
        listOf(profile.body, profile.glass, profile.pillar, profile.headLight, profile.tailLight, profile.beltLine)
            .map { it.toPath() }
    }
    Canvas(modifier.aspectRatio(VIEW_W / VIEW_H)) {
        val s = size.width / VIEW_W
        scale(s, s, pivot = Offset.Zero) {
            drawVehicle(profile, paths, paint, lightsOn)
        }
    }
}

private fun DrawScope.drawVehicle(p: SideProfile, paths: List<Path>, paint: Color, lightsOn: Boolean) {
    val (body, glass, pillar, head, tail, belt) = paths
    // Ground shadow.
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent),
            center = Offset(VIEW_W / 2, p.wheelY + p.wheelR - 2),
            radius = 190f,
        ),
        topLeft = Offset(10f, p.wheelY + p.wheelR - 12),
        size = Size(VIEW_W - 20f, 20f),
    )
    // Body with a soft top-down sheen.
    drawPath(
        body,
        brush = Brush.verticalGradient(
            listOf(lerp(paint, Color.White, 0.35f), paint, lerp(paint, Color.Black, 0.18f)),
            startY = 36f,
            endY = 112f,
        ),
    )
    drawPath(body, color = Color.Black.copy(alpha = 0.18f), style = Stroke(width = 1.2f))
    drawPath(belt, color = Color.Black.copy(alpha = 0.12f), style = Stroke(width = 1.4f))
    drawPath(
        glass,
        brush = Brush.verticalGradient(listOf(Color(0xFF3A4A5E), Color(0xFF1B2533)), startY = 40f, endY = 72f),
    )
    drawPath(pillar, color = lerp(paint, Color.Black, 0.25f))
    drawPath(head, color = if (lightsOn) Color(0xFFFFF4C2) else Color(0xFFDDE6EE))
    drawPath(tail, color = Color(0xFFC0392B))
    drawWheel(p.frontWheelX, p.wheelY, p.wheelR)
    drawWheel(p.rearWheelX, p.wheelY, p.wheelR)
}

private operator fun <T> List<T>.component6(): T = this[5]

private fun DrawScope.drawWheel(cx: Float, cy: Float, r: Float) {
    val c = Offset(cx, cy)
    drawCircle(Color(0xFF1E2228), radius = r, center = c)
    drawCircle(Color(0xFF8E98A4), radius = r * 0.58f, center = c)
    drawCircle(Color(0xFF5D6670), radius = r * 0.22f, center = c)
    for (i in 0 until 5) {
        translate(cx, cy) {
            val a = Math.toRadians(i * 72.0 - 90.0)
            val x = (Math.cos(a) * r * 0.5).toFloat()
            val y = (Math.sin(a) * r * 0.5).toFloat()
            drawLine(Color(0xFF5D6670), Offset.Zero, Offset(x, y), strokeWidth = 3f)
        }
    }
}

/** Openings shown on the top-down status view. */
data class VehicleOpenings(
    val frontLeft: Boolean = false,
    val frontRight: Boolean = false,
    val rearLeft: Boolean = false,
    val rearRight: Boolean = false,
    val trunk: Boolean = false,
    val hood: Boolean = false,
)

/**
 * Top-down outline for the Vehicle Status screen. Viewport 160 x 300, front at the top.
 * Open items are drawn in [alertColor].
 */
@Composable
fun VehicleTopView(
    modifier: Modifier = Modifier,
    fill: Color,
    outline: Color,
    alertColor: Color,
    openings: VehicleOpenings = VehicleOpenings(),
) {
    Canvas(modifier.aspectRatio(160f / 300f)) {
        val s = size.width / 160f
        scale(s, s, pivot = Offset.Zero) {
            val corner = CornerRadius(48f, 48f)
            drawRoundRect(fill, topLeft = Offset(20f, 10f), size = Size(120f, 280f), cornerRadius = corner)
            drawRoundRect(
                outline, topLeft = Offset(20f, 10f), size = Size(120f, 280f), cornerRadius = corner,
                style = Stroke(width = 2.5f),
            )
            // Windshield, roof and rear glass.
            drawRoundRect(outline.copy(alpha = 0.35f), Offset(34f, 82f), Size(92f, 40f), CornerRadius(14f, 14f))
            drawRoundRect(outline.copy(alpha = 0.18f), Offset(36f, 126f), Size(88f, 90f), CornerRadius(10f, 10f))
            drawRoundRect(outline.copy(alpha = 0.35f), Offset(36f, 220f), Size(88f, 30f), CornerRadius(12f, 12f))
            // Mirrors.
            drawRoundRect(outline, Offset(8f, 96f), Size(14f, 10f), CornerRadius(4f, 4f))
            drawRoundRect(outline, Offset(138f, 96f), Size(14f, 10f), CornerRadius(4f, 4f))
            // Door seams and openings.
            fun door(open: Boolean, x: Float, y: Float) = drawLine(
                if (open) alertColor else outline.copy(alpha = 0.6f),
                Offset(x, y), Offset(x, y + 56f), strokeWidth = if (open) 6f else 2f,
            )
            door(openings.frontLeft, 20f, 110f)
            door(openings.frontRight, 140f, 110f)
            door(openings.rearLeft, 20f, 170f)
            door(openings.rearRight, 140f, 170f)
            drawLine(
                if (openings.hood) alertColor else outline.copy(alpha = 0.6f),
                Offset(40f, 70f), Offset(120f, 70f), strokeWidth = if (openings.hood) 6f else 2f,
            )
            drawLine(
                if (openings.trunk) alertColor else outline.copy(alpha = 0.6f),
                Offset(40f, 262f), Offset(120f, 262f), strokeWidth = if (openings.trunk) 6f else 2f,
            )
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun VehicleSideViewPreview() {
    VehicleSideView(paint = VehiclePaint.GlacierBlue)
}
