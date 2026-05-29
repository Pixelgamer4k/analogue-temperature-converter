package com.analogue.temperatureconverter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun AnalogueComputerCanvas(
    state: ConverterUIState,
    onDragStartC: () -> Unit,
    onDragC: (Float) -> Unit,
    onDragEndC: () -> Unit,
    onDragStartF: () -> Unit,
    onDragF: (Float) -> Unit,
    onDragEndF: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Canvas dimensions and ratios
    val logicalWidth = 800f
    val logicalHeight = 1200f

    // Color definitions for metallic rendering
    val metalDark = Color(0x111315.toInt() or (0xFF shl 24))
    val metalMedium = Color(0x22252A.toInt() or (0xFF shl 24))
    val metalLight = Color(0x3E444F.toInt() or (0xFF shl 24))
    
    val brassGold = Color(0xFFD4AF37.toInt() or (0xFF shl 24))
    val brassShadow = Color(0x8C6B1C.toInt() or (0xFF shl 24))
    val brassHighlight = Color(0xFFFFF0B3.toInt() or (0xFF shl 24))

    val copperDark = Color(0x8B3E2F.toInt() or (0xFF shl 24))
    val copperLight = Color(0xFF8A6C.toInt() or (0xFF shl 24))
    
    val steelBlue = Color(0x3A6073.toInt() or (0xFF shl 24))
    val steelLight = Color(0xFF3A7BD5.toInt() or (0xFF shl 24))

    val steelOrange = Color(0xD35400.toInt() or (0xFF shl 24))
    val steelAmber = Color(0xFFE67E22.toInt() or (0xFF shl 24))

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Determine which dial was touched
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val scale = minOf(w / logicalWidth, h / logicalHeight)
                        val originX = w / 2f
                        val originY = h / 2f

                        // Convert screen touch coordinate to logical coordinates
                        val lx = (offset.x - originX) / scale
                        val ly = (offset.y - originY) / scale

                        // Dial C center is at (-240, 0)
                        val distC = sqrt((lx - (-240f)) * (lx - (-240f)) + ly * ly)
                        // Dial F center is at (240, 0)
                        val distF = sqrt((lx - 240f) * (lx - 240f) + ly * ly)

                        if (distC < 230f) {
                            onDragStartC()
                        } else if (distF < 230f) {
                            onDragStartF()
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val scale = minOf(w / logicalWidth, h / logicalHeight)
                        val originX = w / 2f
                        val originY = h / 2f

                        val lx = (change.position.x - originX) / scale
                        val ly = (change.position.y - originY) / scale

                        // Calculate angle relative to centers
                        val angleC = atan2(ly, lx - (-240f)) * (180f / PI.toFloat())
                        val angleF = atan2(ly, lx - 240f) * (180f / PI.toFloat())

                        // Adjust so 0 degrees points upwards
                        val rotatedAngleC = angleC + 90f
                        val rotatedAngleF = angleF + 90f

                        // Determine closest dial by distance
                        val distC = sqrt((lx - (-240f)) * (lx - (-240f)) + ly * ly)
                        val distF = sqrt((lx - 240f) * (lx - 240f) + ly * ly)

                        if (distC < distF && distC < 220f) {
                            onDragC(rotatedAngleC)
                        } else if (distF < distC && distF < 220f) {
                            onDragF(rotatedAngleF)
                        }
                    },
                    onDragEnd = {
                        onDragEndC()
                        onDragEndF()
                    },
                    onDragCancel = {
                        onDragEndC()
                        onDragEndF()
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val scale = minOf(w / logicalWidth, h / logicalHeight)
        
        // Gyro offsets (clamped for safety)
        val tX = state.tiltX.coerceIn(-2.8f, 2.8f)
        val tY = state.tiltY.coerceIn(-2.8f, 2.8f)

        // Draw everything centered and scaled
        withTransform({
            translate(left = w / 2f, top = h / 2f)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            // LAYER 1: Deep Background Plate (Parallax -10dp)
            withTransform({
                translate(left = -tX * 25f, top = -tY * 25f)
            }) {
                drawBackgroundPlate(logicalWidth, logicalHeight)
            }

            // LAYER 2: Gear Shaft Hubs & Bridges (Parallax -5dp)
            withTransform({
                translate(left = -tX * 15f, top = -tY * 15f)
            }) {
                drawBridgesAndMounts(state.gears)
            }

            // LAYER 3: Dynamic Interlocking Gears (Parallax 0dp)
            state.gears.forEach { gear ->
                drawGear(gear)
            }

            // LAYER 4: Dial Scales, Bezels & Needles (Parallax +8dp)
            withTransform({
                translate(left = tX * 20f, top = tY * 20f)
            }) {
                // Celsius Dial Bezel, Rotating Face, & Index Marks
                drawDialScale(
                    centerX = -280f,
                    centerY = 0f,
                    radius = 180f,
                    accentColor = Color(0xFF00B0FF),
                    isCelsius = true,
                    angle = state.celsiusDialAngle
                )

                // Fahrenheit Dial Bezel, Rotating Face, & Index Marks
                drawDialScale(
                    centerX = 280f,
                    centerY = 0f,
                    radius = 180f,
                    accentColor = Color(0xFFFF6D00),
                    isCelsius = false,
                    angle = state.fahrenheitDialAngle
                )
            }

            // LAYER 5: Curved Front Glass & Iridescent Reflections (Parallax +18dp)
            withTransform({
                translate(left = tX * 18f, top = tY * 18f)
            }) {
                drawGlassAndOilSheen(logicalWidth, logicalHeight, tX, tY)
            }
        }
    }
}

// Extension functions for DrawScope to make code highly readable
private fun DrawScope.drawBackgroundPlate(w: Float, h: Float) {
    // Solid base dark oil gradient
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1B1D22), Color(0xFF0A0B0C)),
            center = Offset(0f, 0f),
            radius = 600f
        ),
        topLeft = Offset(-w / 2f, -h / 2f),
        size = Size(w, h)
    )

    // Draw circular chassis recesses behind the dials
    drawCircle(
        color = Color(0xFF050607),
        radius = 175f,
        center = Offset(-240f, 0f)
    )
    drawCircle(
        color = Color(0xFF050607),
        radius = 175f,
        center = Offset(240f, 0f)
    )

    // Subtle iron paneling joints
    drawLine(
        color = Color(0x1AFFFFFF),
        start = Offset(0f, -500f),
        end = Offset(0f, 500f),
        strokeWidth = 2f
    )
    drawLine(
        color = Color(0x1AFFFFFF),
        start = Offset(-350f, 0f),
        end = Offset(350f, 0f),
        strokeWidth = 2f
    )

    // Vintage mechanical rivets
    val rivets = listOf(
        Offset(-370f, -480f), Offset(370f, -480f),
        Offset(-370f, 480f), Offset(370f, 480f),
        Offset(0f, -480f), Offset(0f, 480f),
        Offset(-370f, 0f), Offset(370f, 0f)
    )
    rivets.forEach { pt ->
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF777777), Color(0xFF1E1E1E)),
                start = pt - Offset(2f, 2f),
                end = pt + Offset(2f, 2f)
            ),
            radius = 5f,
            center = pt
        )
        drawCircle(
            color = Color(0xFF000000),
            radius = 5f,
            center = pt,
            style = Stroke(width = 1f)
        )
    }
}

private fun DrawScope.drawBridgesAndMounts(gears: List<GearState>) {
    // 1. Draw a beautiful central winding bridge that joins Shaft 1 to Shaft 5
    val bridgePath = Path().apply {
        moveTo(-39.7f, -132.2f)
        lineTo(4.3f, 21.7f)
        lineTo(76.3f, -164.9f)
        lineTo(132.5f, 27.0f)
        lineTo(136.0f, -122.9f)
    }

    // Draw winding background girder
    drawPath(
        path = bridgePath,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF263238), Color(0xFF455A64), Color(0xFF1A2327)),
            start = Offset(-39.7f, -132.2f),
            end = Offset(136.0f, -122.9f)
        ),
        style = Stroke(width = 28f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Beveled highlight on the bridge girder
    drawPath(
        path = bridgePath,
        color = Color(0xFFCFD8DC).copy(alpha = 0.35f),
        style = Stroke(width = 22f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = bridgePath,
        color = Color(0xFF070809),
        style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // 2. Draw shaft pin caps on each unique gear center
    val uniqueShafts = gears.distinctBy { Pair(it.centerX, it.centerY) }
    uniqueShafts.forEach { gear ->
        // Recess pocket
        drawCircle(
            color = Color(0xFF030405),
            radius = 16f,
            center = Offset(gear.centerX, gear.centerY)
        )

        // Polished shaft cap
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFCCCCCC), Color(0xFF333333)),
                start = Offset(gear.centerX - 8f, gear.centerY - 8f),
                end = Offset(gear.centerX + 8f, gear.centerY + 8f)
            ),
            radius = 10f,
            center = Offset(gear.centerX, gear.centerY)
        )
        
        // Inner brass center pin
        drawCircle(
            color = Color(0xFFD4AF37),
            radius = 3.5f,
            center = Offset(gear.centerX, gear.centerY)
        )
    }
}

private fun DrawScope.drawGear(
    gear: GearState
) {
    val brassGold = Color(0xFFD4AF37.toInt() or (0xFF shl 24))
    val brassShadow = Color(0x8C6B1C.toInt() or (0xFF shl 24))
    val brassHighlight = Color(0xFFFFF0B3.toInt() or (0xFF shl 24))

    val copperDark = Color(0x8B3E2F.toInt() or (0xFF shl 24))
    val copperLight = Color(0xFF8A6C.toInt() or (0xFF shl 24))
    
    val steelBlue = Color(0x3A6073.toInt() or (0xFF shl 24))
    val steelLight = Color(0xFF3A7BD5.toInt() or (0xFF shl 24))

    val steelOrange = Color(0xD35400.toInt() or (0xFF shl 24))
    val steelAmber = Color(0xFFE67E22.toInt() or (0xFF shl 24))

    // Resolve brushes based on type
    val (primaryBrush, rimColor) = when (gear.colorType) {
        "brass" -> Pair(
            Brush.sweepGradient(
                colors = listOf(brassShadow, brassGold, brassHighlight, brassShadow),
                center = Offset(gear.centerX, gear.centerY)
            ),
            brassGold
        )
        "copper" -> Pair(
            Brush.sweepGradient(
                colors = listOf(copperDark, copperLight, copperDark),
                center = Offset(gear.centerX, gear.centerY)
            ),
            copperLight
        )
        "steel" -> Pair(
            Brush.sweepGradient(
                colors = listOf(Color(0xFF212C31), Color(0xFFCFD8DC), Color(0xFF212C31)),
                center = Offset(gear.centerX, gear.centerY)
            ),
            Color(0xFFB0BEC5)
        )
        "bronze" -> Pair(
            Brush.sweepGradient(
                colors = listOf(Color(0xFF3E2723), Color(0xFF8D6E63), Color(0xFF3E2723)),
                center = Offset(gear.centerX, gear.centerY)
            ),
            Color(0xFF6D4C41)
        )
        "steel_blue" -> Pair(
            Brush.sweepGradient(
                colors = listOf(Color(0xFF0F1A1E), steelBlue, Color(0xFF0F1A1E)),
                center = Offset(gear.centerX, gear.centerY)
            ),
            steelLight
        )
        "steel_orange" -> Pair(
            Brush.sweepGradient(
                colors = listOf(Color(0xFF260D00), steelOrange, Color(0xFF260D00)),
                center = Offset(gear.centerX, gear.centerY)
            ),
            steelAmber
        )
        else -> Pair(
            Brush.sweepGradient(
                colors = listOf(Color.Gray, Color.White, Color.Gray),
                center = Offset(gear.centerX, gear.centerY)
            ),
            Color.LightGray
        )
    }

    // DRAW GEAR TEETH (using exact geometry for perfect interlocking)
    drawGearTeeth(gear.centerX, gear.centerY, gear.radius, gear.teeth, gear.angle, rimColor)

    // DRAW GEAR CORE BODY
    drawCircle(
        brush = primaryBrush,
        radius = gear.radius - 2f,
        center = Offset(gear.centerX, gear.centerY)
    )

    // Dark inset ring for authentic relief/depth
    drawCircle(
        color = Color(0x66000000),
        radius = gear.radius * 0.82f,
        center = Offset(gear.centerX, gear.centerY),
        style = Stroke(width = gear.radius * 0.08f)
    )

    // DRAW SPOKES (Beautiful clockwork cutouts)
    // We draw 5 round-cornered cutouts around the center
    val spokesCount = 5
    val spokeInnerR = gear.radius * 0.25f
    val spokeOuterR = gear.radius * 0.72f
    
    if (gear.radius > 45f) {
        val cutoutPath = Path()
        val step = 360f / spokesCount
        for (i in 0 until spokesCount) {
            val startAngleRad = ((gear.angle + i * step + 5f) * PI / 180f).toFloat()
            val endAngleRad = ((gear.angle + (i + 1) * step - 12f) * PI / 180f).toFloat()

            val p1x = gear.centerX + spokeInnerR * cos(startAngleRad)
            val p1y = gear.centerY + spokeInnerR * sin(startAngleRad)
            val p2x = gear.centerX + spokeOuterR * cos(startAngleRad)
            val p2y = gear.centerY + spokeOuterR * sin(startAngleRad)

            val p3x = gear.centerX + spokeOuterR * cos(endAngleRad)
            val p3y = gear.centerY + spokeOuterR * sin(endAngleRad)
            val p4x = gear.centerX + spokeInnerR * cos(endAngleRad)
            val p4y = gear.centerY + spokeInnerR * sin(endAngleRad)

            cutoutPath.apply {
                moveTo(p1x, p1y)
                lineTo(p2x, p2y)
                quadraticTo(
                    gear.centerX + spokeOuterR * cos((startAngleRad + endAngleRad) / 2f),
                    gear.centerY + spokeOuterR * sin((startAngleRad + endAngleRad) / 2f),
                    p3x, p3y
                )
                lineTo(p4x, p4y)
                quadraticTo(
                    gear.centerX + spokeInnerR * cos((startAngleRad + endAngleRad) / 2f),
                    gear.centerY + spokeInnerR * sin((startAngleRad + endAngleRad) / 2f),
                    p1x, p1y
                )
                close()
            }
        }
        // Fill spokes with background colored shadow
        drawPath(
            path = cutoutPath,
            color = Color(0xFF0A0B0C),
        )
        // Give cutouts a subtle inner shadow/border
        drawPath(
            path = cutoutPath,
            color = Color(0x2B000000),
            style = Stroke(width = 2.5f)
        )
    }

    // Polished rim highlight
    drawCircle(
        color = Color(0x3BFFFFFF),
        radius = gear.radius,
        center = Offset(gear.centerX, gear.centerY),
        style = Stroke(width = 1.2f)
    )
}

private fun DrawScope.drawGearTeeth(
    cx: Float,
    cy: Float,
    radius: Float,
    teeth: Int,
    rotation: Float,
    color: Color
) {
    val toothPath = Path()
    val toothAngleSpan = 360f / teeth
    val radPerDeg = (PI / 180f).toFloat()

    // Dimensions of standard gear teeth based on radius
    val addendum = 3.6f
    val dedendum = 3.8f
    val rOuter = radius + addendum
    val rInner = radius - dedendum

    for (i in 0 until teeth) {
        val toothCenterAngle = rotation + i * toothAngleSpan

        // Four control angles for the trapezoidal tooth profile
        val a1 = (toothCenterAngle - toothAngleSpan * 0.32f) * radPerDeg
        val a2 = (toothCenterAngle - toothAngleSpan * 0.16f) * radPerDeg
        val a3 = (toothCenterAngle + toothAngleSpan * 0.16f) * radPerDeg
        val a4 = (toothCenterAngle + toothAngleSpan * 0.32f) * radPerDeg
        val a5 = (toothCenterAngle + toothAngleSpan * 0.50f) * radPerDeg

        val p1 = Offset(cx + rInner * cos(a1), cy + rInner * sin(a1))
        val p2 = Offset(cx + rOuter * cos(a2), cy + rOuter * sin(a2))
        val p3 = Offset(cx + rOuter * cos(a3), cy + rOuter * sin(a3))
        val p4 = Offset(cx + rInner * cos(a4), cy + rInner * sin(a4))
        val p5 = Offset(cx + rInner * cos(a5), cy + rInner * sin(a5))

        if (i == 0) {
            toothPath.moveTo(p1.x, p1.y)
        } else {
            toothPath.lineTo(p1.x, p1.y)
        }
        toothPath.lineTo(p2.x, p2.y)
        toothPath.lineTo(p3.x, p3.y)
        toothPath.lineTo(p4.x, p4.y)
        toothPath.lineTo(p5.x, p5.y)
    }
    toothPath.close()

    // Draw the continuous outer teeth profile
    drawPath(
        path = toothPath,
        color = color
    )
    // Draw dark teeth side borders for 3D metallic feel
    drawPath(
        path = toothPath,
        color = Color(0x6A000000),
        style = Stroke(width = 0.8f)
    )
}

private fun DrawScope.drawDialScale(
    centerX: Float,
    centerY: Float,
    radius: Float,
    accentColor: Color,
    isCelsius: Boolean,
    angle: Float
) {
    // 1. Solid metal bezel base
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF33363B), Color(0xFF15171A)),
            start = Offset(centerX - radius, centerY - radius),
            end = Offset(centerX + radius, centerY + radius)
        ),
        radius = radius,
        center = Offset(centerX, centerY)
    )

    // Decorative copper mounting ring
    drawCircle(
        color = Color(0xFF8B5A2B),
        radius = radius + 2f,
        center = Offset(centerX, centerY),
        style = Stroke(width = 3f)
    )

    // Inner shadow for depth
    drawCircle(
        color = Color(0xCC000000),
        radius = radius - 8f,
        center = Offset(centerX, centerY)
    )

    // 2. DRAW ROTATING DIAL FACE (rotated by angle)
    withTransform({
        rotate(degrees = angle, pivot = Offset(centerX, centerY))
    }) {
        // Brushed metallic dial face
        drawCircle(
            brush = Brush.sweepGradient(
                colors = if (isCelsius) {
                    listOf(Color(0xFF1F2429), Color(0xFF3E464F), Color(0xFF1F2429))
                } else {
                    listOf(Color(0xFF261208), Color(0xFF5E351E), Color(0xFF261208))
                },
                center = Offset(centerX, centerY)
            ),
            radius = radius - 10f,
            center = Offset(centerX, centerY)
        )

        // Dark bevel accent ring
        drawCircle(
            color = Color(0x66000000),
            radius = radius - 10f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 3f)
        )

        // Elegant indicator line
        drawCircle(
            color = accentColor.copy(alpha = 0.35f),
            radius = radius - 16f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.5f)
        )

        // Engraved styled Letter 'C' or 'F' near the top
        drawEngravedLetter(
            centerX = centerX,
            centerY = centerY - (radius - 40f),
            isCelsius = isCelsius
        )
        
        // Coaxial mounting rivets on the dial plate
        val rivetRadius = radius - 30f
        val rad = (PI / 180f).toFloat()
        for (a in listOf(45f, 135f, 225f, 315f)) {
            val rPt = Offset(
                centerX + rivetRadius * cos(a * rad),
                centerY + rivetRadius * sin(a * rad)
            )
            drawCircle(
                color = Color(0xFF111111),
                radius = 3.5f,
                center = rPt
            )
            drawCircle(
                color = Color(0xAAFFFFFF),
                radius = 2f,
                center = rPt
            )
        }
    }

    // 3. STATIC bezel pointer index mark at 12 o'clock position
    val markerPath = Path().apply {
        val topY = centerY - radius + 4f
        moveTo(centerX, topY + 22f)
        lineTo(centerX - 9f, topY)
        lineTo(centerX + 9f, topY)
        close()
    }
    // Shadow
    drawPath(
        path = markerPath,
        color = Color(0xDD000000)
    )
    // Copper/gold index gradient fill
    drawPath(
        path = markerPath,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFE5A93B), Color(0xFF7F4E1D)),
            start = Offset(centerX - 9f, centerY - radius),
            end = Offset(centerX + 9f, centerY - radius + 22f)
        )
    )
    // Red enamel indicator point
    drawCircle(
        color = Color(0xFFD32F2F),
        radius = 2.5f,
        center = Offset(centerX, centerY - radius + 17f)
    )
}

private fun DrawScope.drawEngravedLetter(centerX: Float, centerY: Float, isCelsius: Boolean) {
    val size = 26f
    val halfSize = size / 2f
    
    if (isCelsius) {
        // Draw stylized 'C'
        val letterPath = Path().apply {
            addArc(
                oval = Rect(
                    left = centerX - halfSize,
                    top = centerY - halfSize,
                    right = centerX + halfSize,
                    bottom = centerY + halfSize
                ),
                startAngleDegrees = 45f,
                sweepAngleDegrees = 270f
            )
        }
        
        // 3D Highlight shadow
        drawPath(
            path = letterPath,
            color = Color(0x33FFFFFF),
            style = Stroke(width = 5.5f, cap = StrokeCap.Round)
        )
        // Main cyan engraved fill
        drawPath(
            path = letterPath,
            color = Color(0xFF00E5FF),
            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
        )
    } else {
        // Draw stylized 'F'
        val letterPath = Path().apply {
            // Vertical bar
            moveTo(centerX - 6f, centerY - halfSize)
            lineTo(centerX - 6f, centerY + halfSize)
            // Top bar
            moveTo(centerX - 6f, centerY - halfSize)
            lineTo(centerX + 10f, centerY - halfSize)
            // Middle bar
            moveTo(centerX - 6f, centerY)
            lineTo(centerX + 5f, centerY)
        }
        
        // 3D Highlight shadow
        drawPath(
            path = letterPath,
            color = Color(0x33FFFFFF),
            style = Stroke(width = 5.5f, cap = StrokeCap.Round)
        )
        // Main orange engraved fill
        drawPath(
            path = letterPath,
            color = Color(0xFFFF9100),
            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawGlassAndOilSheen(w: Float, h: Float, tX: Float, tY: Float) {
    // 1. High contrast diagonal glare reflecting across the dial glass
    val glareBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.04f),
            Color.White.copy(alpha = 0.16f),
            Color.White.copy(alpha = 0.0f)
        ),
        start = Offset(-w / 2f + tX * 30f, -h / 2f + tY * 30f),
        end = Offset(w / 2f + tX * 30f, h / 2f + tY * 30f)
    )
    drawRect(
        brush = glareBrush,
        topLeft = Offset(-w / 2f, -h / 2f),
        size = Size(w, h),
        blendMode = BlendMode.Screen
    )

    // 2. Oil Sheen Thin-Film Interference Iridescent Reflections (Dynamic based on tilting)
    val filmColors = listOf(
        Color(0x00E040FB), // Fuchsia (transparent)
        Color(0x1400E5FF), // Cyan/Teal
        Color(0x1BFFEA00), // Yellow
        Color(0x14FF1744), // Crimson pink
        Color(0x00E040FB)
    )
    
    val filmStart = Offset(-w / 3f + tX * 60f, -h / 3f + tY * 60f)
    val filmEnd = Offset(w / 3f + tX * 60f, h / 3f + tY * 60f)
    
    val filmBrush = Brush.linearGradient(
        colors = filmColors,
        start = filmStart,
        end = filmEnd
    )

    // Draw the thin-film interference on top of both dials
    drawCircle(
        brush = filmBrush,
        radius = 180f,
        center = Offset(-240f, 0f),
        blendMode = BlendMode.Color
    )

    drawCircle(
        brush = filmBrush,
        radius = 180f,
        center = Offset(240f, 0f),
        blendMode = BlendMode.Color
    )

    // Subtle curved outer glass highlight lines
    drawArc(
        color = Color.White.copy(alpha = 0.12f),
        startAngle = 200f,
        sweepAngle = 100f,
        useCenter = false,
        topLeft = Offset(-390f, -150f),
        size = Size(300f, 300f),
        style = Stroke(width = 2.5f)
    )
    drawArc(
        color = Color.White.copy(alpha = 0.12f),
        startAngle = 200f,
        sweepAngle = 100f,
        useCenter = false,
        topLeft = Offset(90f, -150f),
        size = Size(300f, 300f),
        style = Stroke(width = 2.5f)
    )
}
