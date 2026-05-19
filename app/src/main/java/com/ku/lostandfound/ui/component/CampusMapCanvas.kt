package com.ku.lostandfound.ui.component

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.data.CampusBoundary
import com.ku.lostandfound.data.CampusBuilding
import com.ku.lostandfound.data.CampusPath
import com.ku.lostandfound.data.GeoPoint
import com.ku.lostandfound.data.OutdoorPin
import com.ku.lostandfound.util.GeoUtils
import kotlin.math.max
import kotlin.math.min

private val MapBackground = Color(0xFFEEF3EA)
private val ReferencePathStroke = Color(0xFFC9D3CA)
private val BuildingFill = Color(0xFFC4D09A)
private val BuildingStroke = Color(0xFF849362)
private val SelectedBuildingFill = Color(0xFF5FAF69)
private val SelectedBuildingStroke = Color(0xFF1B6425)
private val LakeFill = Color(0xFFAED7EC)
private val BoundaryStroke = Color(0xFF2E442F)
private val RouteRed = Color(0xFFCB4050)
private val PinPink = Color(0xFFFF5D7A)

@Composable
fun CampusMapCanvas(
    boundary: CampusBoundary,
    buildings: List<CampusBuilding>,
    modifier: Modifier = Modifier,
    referencePaths: List<CampusPath> = emptyList(),
    showReferencePaths: Boolean = true,
    outdoorPins: List<OutdoorPin> = emptyList(),
    singlePin: GeoPoint? = null,
    selectedBuildingIds: Set<String> = emptySet(),
    showRoute: Boolean = true,
    showLabels: Boolean = true,
    onMapTap: ((GeoPoint) -> Unit)? = null,
    onBuildingClick: ((CampusBuilding) -> Unit)? = null,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current
    val mapPoints = remember(boundary, buildings) {
        boundary.polygon + buildings.flatMap { it.outerRing }
    }

    fun clampPan(nextPanOffset: Offset, nextZoom: Float = zoom): Offset {
        return clampPanOffset(
            points = mapPoints,
            canvasSize = canvasSize,
            zoom = nextZoom,
            panOffset = nextPanOffset,
            paddingPx = with(density) { 22.dp.toPx() },
        )
    }

    Box(modifier = modifier.clipToBounds().background(MapBackground)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    canvasSize = Size(it.width.toFloat(), it.height.toFloat())
                    panOffset = clampPan(panOffset)
                }
                .pointerInput(mapPoints, zoom, canvasSize) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        panOffset = clampPan(panOffset + dragAmount)
                    }
                }
                .then(
                    if (onMapTap != null || onBuildingClick != null) {
                        Modifier.pointerInput(mapPoints, zoom, panOffset) {
                            detectTapGestures { offset ->
                                val converter = GeoScreenConverter(
                                    points = mapPoints,
                                    canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                                    zoom = zoom,
                                    panOffset = panOffset,
                                    paddingPx = with(density) { 22.dp.toPx() },
                                )
                                val tappedGeo = converter.screenToGeo(offset)
                                val clickedBuilding = buildings.firstOrNull { building ->
                                    GeoUtils.pointInPolygon(tappedGeo, building.outerRing)
                                }
                                if (clickedBuilding != null && onBuildingClick != null) {
                                    onBuildingClick(clickedBuilding)
                                } else {
                                    onMapTap?.invoke(tappedGeo)
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            val converter = GeoScreenConverter(
                points = mapPoints,
                canvasSize = size,
                zoom = zoom,
                panOffset = panOffset,
                paddingPx = 22.dp.toPx(),
            )

            drawBoundary(boundary, converter)

            if (showReferencePaths) {
                drawReferencePaths(referencePaths, converter)
            }

            buildings.forEach { building ->
                drawBuilding(
                    building = building,
                    converter = converter,
                    showLabels = showLabels,
                    zoom = zoom,
                    isSelected = building.id in selectedBuildingIds,
                )
            }

            val orderedPins = outdoorPins.sortedBy { it.order }
            if (showRoute && orderedPins.size >= 2) {
                drawUserRoute(orderedPins.map { it.point }, converter)
            }
            orderedPins.forEach { pin -> drawPin(pin.point, converter, label = pin.order.toString()) }
            singlePin?.let { drawPin(it, converter, label = null) }
        }

        MapZoomButtons(
            zoom = zoom,
            onMinusClick = {
                val nextZoom = max(0.75f, zoom - 0.25f)
                zoom = nextZoom
                panOffset = clampPan(panOffset, nextZoom)
            },
            onPlusClick = {
                val nextZoom = min(3.0f, zoom + 0.25f)
                zoom = nextZoom
                panOffset = clampPan(panOffset, nextZoom)
            },
            onResetClick = {
                zoom = 1f
                panOffset = Offset.Zero
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
        )
    }
}

@Composable
private fun MapZoomButtons(
    zoom: Float,
    onMinusClick: () -> Unit,
    onPlusClick: () -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.75f))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleButton(text = "−", onClick = onMinusClick)
        Spacer(Modifier.size(5.dp))
        CircleButton(text = "+", onClick = onPlusClick)
        Spacer(Modifier.size(5.dp))
        CircleButton(text = "${zoom.toString().take(3)}x", onClick = onResetClick, wide = true)
    }
}

@Composable
private fun CircleButton(text: String, onClick: () -> Unit, wide: Boolean = false) {
    Box(
        modifier = Modifier
            .size(width = if (wide) 30.dp else 25.dp, height = 25.dp)
            .clip(CircleShape)
            .background(Color(0xFFE9ECFF))
            .border(0.5.dp, Color.White, CircleShape)
            .padding(2.dp)
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Color(0xFF6A6F8F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun DrawScope.drawBoundary(boundary: CampusBoundary, converter: GeoScreenConverter) {
    drawPath(
        path = boundary.polygon.toClosedPath(converter),
        color = BoundaryStroke,
        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawReferencePaths(paths: List<CampusPath>, converter: GeoScreenConverter) {
    paths.forEach { path ->
        val points = path.coordinate
        if (points.size < 2) return@forEach
        val screenPoints = points.map { converter.geoToScreen(it) }
        val width = when (path.highway) {
            "residential", "service" -> 1.0.dp.toPx()
            "pedestrian" -> 1.2.dp.toPx()
            else -> 0.8.dp.toPx()
        }
        for (i in 0 until screenPoints.lastIndex) {
            drawLine(
                color = ReferencePathStroke,
                start = screenPoints[i],
                end = screenPoints[i + 1],
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
        if (path.closed && screenPoints.size >= 3) {
            drawLine(
                color = ReferencePathStroke,
                start = screenPoints.last(),
                end = screenPoints.first(),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawBuilding(
    building: CampusBuilding,
    converter: GeoScreenConverter,
    showLabels: Boolean,
    zoom: Float,
    isSelected: Boolean,
) {
    val outer = building.outerRing
    if (outer.size < 3) return
    val fill = when {
        isSelected -> SelectedBuildingFill
        building.name == "일감호" -> LakeFill
        else -> BuildingFill
    }
    val stroke = when {
        isSelected -> SelectedBuildingStroke
        building.name == "일감호" -> LakeFill.copy(alpha = 0.8f)
        else -> BuildingStroke
    }
    val strokeWidth = if (isSelected) 1.8.dp.toPx() else 0.8.dp.toPx()

    drawPath(path = outer.toClosedPath(converter), color = fill)
    drawPath(path = outer.toClosedPath(converter), color = stroke, style = Stroke(width = strokeWidth))

    if (showLabels && shouldShowBuildingLabel(building, zoom)) {
        drawBuildingLabel(building = building, converter = converter, zoom = zoom)
    }
}

private val AlwaysVisibleBuildingLabels = setOf("일감호")

private fun shouldShowBuildingLabel(building: CampusBuilding, zoom: Float): Boolean {
    return zoom >= 1.15f || building.name in AlwaysVisibleBuildingLabels
}

private fun DrawScope.drawBuildingLabel(
    building: CampusBuilding,
    converter: GeoScreenConverter,
    zoom: Float,
) {
    val center = converter.geoToScreen(GeoUtils.centroid(building.outerRing))
    val lines = building.name.toBuildingLabelLines(zoom)
    val textSizePx = when {
        zoom >= 1.75f -> 11.5.dp.toPx()
        zoom >= 1.15f -> 10.5.dp.toPx()
        else -> 9.5.dp.toPx()
    }
    val lineHeight = textSizePx * 1.08f
    val startY = center.y - ((lines.size - 1) * lineHeight / 2f)
    val fillColor = if (building.name == "일감호") {
        android.graphics.Color.rgb(78, 130, 150)
    } else {
        android.graphics.Color.rgb(60, 74, 48)
    }

    lines.forEachIndexed { index, line ->
        drawOutlinedText(
            text = line,
            x = center.x,
            y = startY + index * lineHeight,
            textSizePx = textSizePx,
            fillColor = fillColor,
        )
    }
}

private fun String.toBuildingLabelLines(zoom: Float): List<String> {
    if (length <= 5 || zoom >= 2.0f) return listOf(this)
    val chunkSize = if (length <= 8) 4 else 5
    return chunked(chunkSize)
}

private fun DrawScope.drawOutlinedText(
    text: String,
    x: Float,
    y: Float,
    textSizePx: Float,
    fillColor: Int,
) {
    val canvas = drawContext.canvas.nativeCanvas
    val strokePaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = textSizePx
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeWidth = 3.2f
        isFakeBoldText = true
        isAntiAlias = true
    }
    val fillPaint = Paint().apply {
        color = fillColor
        textSize = textSizePx
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
        isFakeBoldText = true
        isAntiAlias = true
    }
    canvas.drawText(text, x, y, strokePaint)
    canvas.drawText(text, x, y, fillPaint)
}

private fun DrawScope.drawUserRoute(points: List<GeoPoint>, converter: GeoScreenConverter) {
    val screenPoints = points.map { converter.geoToScreen(it) }
    for (i in 0 until screenPoints.lastIndex) {
        drawLine(
            color = RouteRed,
            start = screenPoints[i],
            end = screenPoints[i + 1],
            strokeWidth = 2.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawPin(point: GeoPoint, converter: GeoScreenConverter, label: String?) {
    val p = converter.geoToScreen(point)
    drawCircle(color = PinPink, radius = 8.dp.toPx(), center = p)
    drawCircle(color = Color.White, radius = 3.dp.toPx(), center = p)
    label?.let {
        drawContext.canvas.nativeCanvas.drawText(
            it,
            p.x,
            p.y - 12.dp.toPx(),
            Paint().apply {
                color = android.graphics.Color.rgb(203, 64, 80)
                textSize = 11.dp.toPx()
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                isAntiAlias = true
            }
        )
    }
}

private fun List<GeoPoint>.toClosedPath(converter: GeoScreenConverter): Path = Path().apply {
    if (isEmpty()) return@apply
    val first = converter.geoToScreen(first())
    moveTo(first.x, first.y)
    drop(1).forEach { point ->
        val screen = converter.geoToScreen(point)
        lineTo(screen.x, screen.y)
    }
    close()
}

private fun clampPanOffset(
    points: List<GeoPoint>,
    canvasSize: Size,
    zoom: Float,
    panOffset: Offset,
    paddingPx: Float,
): Offset {
    if (points.isEmpty() || canvasSize.width <= 0f || canvasSize.height <= 0f) return Offset.Zero

    val bounds = GeoUtils.boundsOf(points)
    val safeWidth = max(1e-9, bounds.width).toFloat()
    val safeHeight = max(1e-9, bounds.height).toFloat()
    val availableWidth = canvasSize.width - paddingPx * 2
    val availableHeight = canvasSize.height - paddingPx * 2
    if (availableWidth <= 0f || availableHeight <= 0f) return Offset.Zero

    val baseScale = min(availableWidth / safeWidth, availableHeight / safeHeight)
    val contentWidth = safeWidth * baseScale * zoom
    val contentHeight = safeHeight * baseScale * zoom

    return Offset(
        x = panOffset.x.coercePanAxis(
            contentSize = contentWidth,
            viewportSize = canvasSize.width,
            paddingPx = paddingPx,
        ),
        y = panOffset.y.coercePanAxis(
            contentSize = contentHeight,
            viewportSize = canvasSize.height,
            paddingPx = paddingPx,
        ),
    )
}

private fun Float.coercePanAxis(
    contentSize: Float,
    viewportSize: Float,
    paddingPx: Float,
): Float {
    val availableSize = viewportSize - paddingPx * 2
    if (contentSize <= availableSize) return 0f

    val baseOffset = (viewportSize - contentSize) / 2f
    val minPan = viewportSize - paddingPx - contentSize - baseOffset
    val maxPan = paddingPx - baseOffset
    return coerceIn(minPan, maxPan)
}

class GeoScreenConverter(
    points: List<GeoPoint>,
    private val canvasSize: Size,
    private val zoom: Float,
    private val panOffset: Offset,
    private val paddingPx: Float,
) {
    private val bounds = GeoUtils.boundsOf(points)
    private val scale: Float
    private val contentWidth: Float
    private val contentHeight: Float
    private val offsetX: Float
    private val offsetY: Float

    init {
        val safeWidth = max(1e-9, bounds.width).toFloat()
        val safeHeight = max(1e-9, bounds.height).toFloat()
        val baseScale = min(
            (canvasSize.width - paddingPx * 2) / safeWidth,
            (canvasSize.height - paddingPx * 2) / safeHeight,
        )
        scale = baseScale * zoom
        contentWidth = safeWidth * scale
        contentHeight = safeHeight * scale
        offsetX = (canvasSize.width - contentWidth) / 2f + panOffset.x
        offsetY = (canvasSize.height - contentHeight) / 2f + panOffset.y
    }

    fun geoToScreen(point: GeoPoint): Offset {
        val x = offsetX + ((point.longitude - bounds.minLng).toFloat() * scale)
        val y = offsetY + ((bounds.maxLat - point.latitude).toFloat() * scale)
        return Offset(x, y)
    }

    fun screenToGeo(offset: Offset): GeoPoint {
        val lng = ((offset.x - offsetX) / scale) + bounds.minLng
        val lat = bounds.maxLat - ((offset.y - offsetY) / scale)
        return GeoPoint(longitude = lng.toDouble(), latitude = lat.toDouble())
    }
}
