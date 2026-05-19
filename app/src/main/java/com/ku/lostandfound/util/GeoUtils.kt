package com.ku.lostandfound.util

import com.ku.lostandfound.data.CampusBuilding
import com.ku.lostandfound.data.GeoPoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
            pointInPolygon(point, outer)
        }
}
