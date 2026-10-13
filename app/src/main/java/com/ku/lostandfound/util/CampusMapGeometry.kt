package com.ku.lostandfound.util

import com.ku.lostandfound.data.CampusBoundary
import com.ku.lostandfound.data.CampusBuilding
import com.ku.lostandfound.data.CampusPath
import com.ku.lostandfound.data.GeoPoint

/** The map outline and its selectable area must use the same polygon. */
class CampusMapGeometry(
    boundary: CampusBoundary,
    buildings: List<CampusBuilding>,
    referencePaths: List<CampusPath>,
) {
    val points: List<GeoPoint> = boundary.polygon +
        buildings.flatMap { it.outerRing } +
        referencePaths.flatMap { it.coordinate }

    val outerBoundary: List<GeoPoint> = points.convexHull()
        .expandFromCenter(scale = 1.04)
        .ifEmpty { boundary.polygon }

    fun contains(point: GeoPoint): Boolean = GeoUtils.pointInPolygon(point, outerBoundary)
}

private fun List<GeoPoint>.convexHull(): List<GeoPoint> {
    val points = distinctBy { it.longitude to it.latitude }
        .sortedWith(compareBy<GeoPoint> { it.longitude }.thenBy { it.latitude })
    if (points.size < 3) return points

    fun cross(o: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        return (a.longitude - o.longitude) * (b.latitude - o.latitude) -
            (a.latitude - o.latitude) * (b.longitude - o.longitude)
    }

    val lower = mutableListOf<GeoPoint>()
    points.forEach { point ->
        while (lower.size >= 2 && cross(lower[lower.lastIndex - 1], lower.last(), point) <= 0.0) {
            lower.removeAt(lower.lastIndex)
        }
        lower.add(point)
    }

    val upper = mutableListOf<GeoPoint>()
    points.asReversed().forEach { point ->
        while (upper.size >= 2 && cross(upper[upper.lastIndex - 1], upper.last(), point) <= 0.0) {
            upper.removeAt(upper.lastIndex)
        }
        upper.add(point)
    }

    return lower.dropLast(1) + upper.dropLast(1)
}

private fun List<GeoPoint>.expandFromCenter(scale: Double): List<GeoPoint> {
    if (isEmpty()) return this
    val centerLng = sumOf { it.longitude } / size
    val centerLat = sumOf { it.latitude } / size
    return map { point ->
        GeoPoint(
            longitude = centerLng + (point.longitude - centerLng) * scale,
            latitude = centerLat + (point.latitude - centerLat) * scale,
        )
    }
}
