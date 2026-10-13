package com.ku.lostandfound.util

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ku.lostandfound.data.CampusBoundary
import com.ku.lostandfound.data.CampusBuilding
import com.ku.lostandfound.data.CampusPath
import com.ku.lostandfound.data.GeoPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusMapGeometryTest {
    private val campusBoundary = CampusBoundary(
        readAsset("konkuk_campus_boundary.json")
            .getAsJsonArray("features")[0].asJsonObject
            .getAsJsonObject("geometry").getAsJsonArray("coordinates")[0]
            .asJsonArray.toPoints()
    )
    private val buildings = readAsset("konkuk_buildings_full_coordinates.json")
        .getAsJsonArray("buildings").map { element ->
            val building = element.asJsonObject
            CampusBuilding(
                id = building["id"].asString,
                name = building["name"].asString,
                levels = 1,
                undergroundLevels = 0,
                rings = building.getAsJsonArray("coordinate").map { it.asJsonArray.toPoints() },
            )
        }
    private val paths = readAsset("konkuk_path_coordinate.json")
        .getAsJsonArray("paths").map { element ->
            val path = element.asJsonObject
            CampusPath(
                id = path["id"].asString,
                sourceId = "",
                geometryType = "LineString",
                highway = "",
                closed = false,
                coordinate = path.getAsJsonArray("coordinate").toPoints(),
            )
        }.filter { it.coordinate.size >= 2 }
    private val geometry = CampusMapGeometry(campusBoundary, buildings, paths)

    @Test
    fun reportedOutdoorLocationsInsideVisibleOutlineAreSelectable() {
        val locations = mapOf(
            "산학협동관 왼쪽" to GeoPoint(127.0728, 37.5398),
            "예술문화관 아래 건대입구 방향" to GeoPoint(127.0729, 37.5418),
            "대운동장 오른쪽" to GeoPoint(127.0793, 37.54435),
            "신공학관 오른쪽" to GeoPoint(127.0801, 37.5406),
            "서버에서 거부된 남쪽 캠퍼스" to GeoPoint(127.0770, 37.5390),
            "서버에서 거부된 서쪽 캠퍼스" to GeoPoint(127.0724, 37.5405),
        )

        locations.forEach { (name, point) ->
            assertFalse("Fixture must reproduce the old rejection: $name",
                GeoUtils.pointInPolygonOrNearBoundary(point, campusBoundary.polygon))
            assertTrue("Visible campus area must be selectable: $name", geometry.contains(point))
        }
    }

    @Test
    fun wholeVisibleOutlineIncludingVerticesAndEdgesIsSelectable() {
        val center = GeoUtils.centroid(geometry.outerBoundary)
        outlineEdges().forEach { (start, end) ->
            for (edgeStep in 0..20) {
                val edgePoint = interpolate(start, end, edgeStep / 20.0)
                for (radialStep in 0..10) {
                    val point = interpolate(center, edgePoint, radialStep / 10.0)
                    assertTrue("Campus interior/edge point was rejected: $point", geometry.contains(point))
                }
            }
        }
    }

    @Test
    fun pointsOutsideEveryVisibleEdgeAreRejected() {
        outlineEdges().forEach { (start, end) ->
            val midpoint = interpolate(start, end, 0.5)
            // The hull is counter-clockwise, so its right-hand normal points outside.
            val dx = end.longitude - start.longitude
            val dy = end.latitude - start.latitude
            val length = kotlin.math.sqrt(dx * dx + dy * dy)
            val outside = GeoPoint(
                longitude = midpoint.longitude + dy / length * 0.0001,
                latitude = midpoint.latitude - dx / length * 0.0001,
            )
            assertFalse("Point outside the campus outline was accepted: $outside", geometry.contains(outside))
        }
    }

    @Test
    fun campusBuildingsAndReferencePathsRemainWithinSelectableArea() {
        (campusBoundary.polygon + buildings.flatMap { it.outerRing } + paths.flatMap { it.coordinate })
            .forEach { point ->
                assertTrue("Map coordinate was excluded: $point", geometry.contains(point))
            }
    }

    @Test
    fun visibleAreaIncludesBuildingsAndPathsBeyondSourceBoundary() {
        val localBoundary = CampusBoundary(listOf(
            GeoPoint(127.0, 37.0), GeoPoint(127.001, 37.0),
            GeoPoint(127.001, 37.001), GeoPoint(127.0, 37.001),
        ))
        val building = CampusBuilding(
            id = "building", name = "building", levels = 1, undergroundLevels = 0,
            rings = listOf(listOf(
                GeoPoint(127.002, 37.0), GeoPoint(127.003, 37.0), GeoPoint(127.002, 37.001),
            )),
        )
        val path = CampusPath(
            id = "path", sourceId = "", geometryType = "LineString", highway = "", closed = false,
            coordinate = listOf(GeoPoint(127.0, 37.002), GeoPoint(127.001, 37.003)),
        )
        val expandedMap = CampusMapGeometry(localBoundary, listOf(building), listOf(path))

        assertTrue(expandedMap.contains(GeoPoint(127.0025, 37.0)))
        assertTrue(expandedMap.contains(GeoPoint(127.001, 37.003)))
        assertFalse(expandedMap.contains(GeoPoint(127.003, 37.003)))
    }

    @Test
    fun missingMapGeometryDoesNotAcceptTaps() {
        val emptyMap = CampusMapGeometry(CampusBoundary(emptyList()), emptyList(), emptyList())
        assertFalse(emptyMap.contains(GeoPoint(127.076, 37.542)))
    }

    private fun outlineEdges(): List<Pair<GeoPoint, GeoPoint>> = geometry.outerBoundary.indices.map { index ->
        geometry.outerBoundary[index] to geometry.outerBoundary[(index + 1) % geometry.outerBoundary.size]
    }

    private fun interpolate(start: GeoPoint, end: GeoPoint, fraction: Double) = GeoPoint(
        longitude = start.longitude + (end.longitude - start.longitude) * fraction,
        latitude = start.latitude + (end.latitude - start.latitude) * fraction,
    )

    private fun readAsset(name: String): JsonObject = requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
        "Missing campus asset: $name"
    }.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }

    private fun JsonArray.toPoints(): List<GeoPoint> = map { pair ->
        GeoPoint(pair.asJsonArray[0].asDouble, pair.asJsonArray[1].asDouble)
    }
}
