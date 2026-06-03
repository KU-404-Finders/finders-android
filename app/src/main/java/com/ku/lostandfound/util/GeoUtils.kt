package com.ku.lostandfound.util

import com.ku.lostandfound.data.CampusBuilding
import com.ku.lostandfound.data.GeoPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object GeoUtils {

    data class GeoBounds(
        val minLng: Double,
        val minLat: Double,
        val maxLng: Double,
        val maxLat: Double,
    ) {
        val width: Double get() = maxLng - minLng
        val height: Double get() = maxLat - minLat

        fun include(point: GeoPoint): GeoBounds = GeoBounds(
            minLng = min(minLng, point.longitude),
            minLat = min(minLat, point.latitude),
            maxLng = max(maxLng, point.longitude),
            maxLat = max(maxLat, point.latitude),
        )
    }

    fun boundsOf(points: List<GeoPoint>): GeoBounds {
        require(points.isNotEmpty()) { "points must not be empty" }
        var bounds = GeoBounds(
            minLng = points.first().longitude,
            minLat = points.first().latitude,
            maxLng = points.first().longitude,
            maxLat = points.first().latitude,
        )
        points.drop(1).forEach { bounds = bounds.include(it) }
        return bounds
    }

    fun boundsOfPolygons(polygons: List<List<GeoPoint>>): GeoBounds =
        boundsOf(polygons.flatten())

    fun pointInPolygon(point: GeoPoint, polygon: List<GeoPoint>): Boolean {
        if (polygon.size < 3) return false
        if (pointOnPolygonBoundary(point, polygon)) return true
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            val intersects = ((pi.latitude > point.latitude) != (pj.latitude > point.latitude)) &&
                (point.longitude < (pj.longitude - pi.longitude) * (point.latitude - pi.latitude) /
                    ((pj.latitude - pi.latitude).takeIf { abs(it) > 1e-12 } ?: 1e-12) + pi.longitude)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    fun pointInPolygonOrNearBoundary(
        point: GeoPoint,
        polygon: List<GeoPoint>,
        toleranceMeters: Double = 8.0,
    ): Boolean {
        return pointInPolygon(point, polygon) || distanceToPolygonMeters(point, polygon) <= toleranceMeters
    }

    fun centroid(points: List<GeoPoint>): GeoPoint {
        if (points.isEmpty()) return GeoPoint(0.0, 0.0)
        return GeoPoint(
            longitude = points.sumOf { it.longitude } / points.size,
            latitude = points.sumOf { it.latitude } / points.size,
        )
    }

    fun findBuildingAt(point: GeoPoint, buildings: List<CampusBuilding>): CampusBuilding? =
        buildings.firstOrNull { building ->
            val outer = building.outerRing
            pointInPolygonOrNearBoundary(point, outer, toleranceMeters = 4.0)
        }

    private fun pointOnPolygonBoundary(point: GeoPoint, polygon: List<GeoPoint>): Boolean =
        distanceToPolygonMeters(point, polygon) <= 0.8

    private fun distanceToPolygonMeters(point: GeoPoint, polygon: List<GeoPoint>): Double {
        if (polygon.size < 2) return Double.POSITIVE_INFINITY
        var minDistance = Double.POSITIVE_INFINITY
        var previous = polygon.last()
        polygon.forEach { current ->
            minDistance = min(minDistance, distanceToSegmentMeters(point, previous, current))
            previous = current
        }
        return minDistance
    }

    private fun distanceToSegmentMeters(point: GeoPoint, start: GeoPoint, end: GeoPoint): Double {
        val metersPerLat = 111_320.0
        val metersPerLng = metersPerLat * kotlin.math.cos(Math.toRadians(point.latitude))
        val px = point.longitude * metersPerLng
        val py = point.latitude * metersPerLat
        val ax = start.longitude * metersPerLng
        val ay = start.latitude * metersPerLat
        val bx = end.longitude * metersPerLng
        val by = end.latitude * metersPerLat
        val dx = bx - ax
        val dy = by - ay
        val segmentLengthSquared = dx * dx + dy * dy
        val t = if (segmentLengthSquared <= 1e-9) {
            0.0
        } else {
            (((px - ax) * dx + (py - ay) * dy) / segmentLengthSquared).coerceIn(0.0, 1.0)
        }
        val closestX = ax + t * dx
        val closestY = ay + t * dy
        return sqrt((px - closestX) * (px - closestX) + (py - closestY) * (py - closestY))
    }
}
