package com.ku.lostandfound.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class CampusJsonRepository(private val context: Context) {

    fun loadBoundary(assetName: String = "konkuk_campus_boundary.json"): CampusBoundary {
        val root = JSONObject(readAsset(assetName))
        val feature = root.getJSONArray("features").getJSONObject(0)
        val coordinates = feature
            .getJSONObject("geometry")
            .getJSONArray("coordinates")
            .getJSONArray(0)

        return CampusBoundary(polygon = coordinates.toGeoPointList())
    }

    fun loadBuildings(assetName: String = "konkuk_buildings_full_coordinates.json"): List<CampusBuilding> {
        val root = JSONObject(readAsset(assetName))
        val buildings = root.getJSONArray("buildings")
        return buildList {
            for (i in 0 until buildings.length()) {
                val obj = buildings.getJSONObject(i)
                val coordinateRings = obj.getJSONArray("coordinate")
                add(
                    CampusBuilding(
                        id = obj.opt("id")?.toString().orEmpty(),
                        name = obj.optString("name"),
                        levels = obj.optInt("building:levels", 1),
                        undergroundLevels = obj.optInt("building:levels:underground", 0),
                        rings = coordinateRings.toRings(),
                    )
                )
            }
        }
    }

    /**
     * 경로도는 프론트 지도에서만 참고용으로 보여준다.
     * assets에 파일이 없거나 파싱에 실패하면 앱이 죽지 않도록 빈 리스트를 반환한다.
     */
    fun loadPaths(assetName: String = "konkuk_path_coordinate.json"): List<CampusPath> = runCatching {
        val root = JSONObject(readAsset(assetName))
        val paths = root.getJSONArray("paths")
        buildList {
            for (i in 0 until paths.length()) {
                val obj = paths.getJSONObject(i)
                val coordinate = obj.getJSONArray("coordinate").toGeoPointList()
                if (coordinate.size >= 2) {
                    add(
                        CampusPath(
                            id = obj.optString("id"),
                            sourceId = obj.optString("source_id"),
                            geometryType = obj.optString("geometry_type"),
                            highway = obj.optString("highway"),
                            closed = obj.optBoolean("closed", false),
                            coordinate = coordinate,
                        )
                    )
                }
            }
        }
    }.getOrElse { emptyList() }

    private fun readAsset(name: String): String =
        context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

private fun JSONArray.toGeoPointList(): List<GeoPoint> = buildList {
    for (i in 0 until length()) {
        val pair = getJSONArray(i)
        add(
            GeoPoint(
                longitude = pair.getDouble(0),
                latitude = pair.getDouble(1),
            )
        )
    }
}

private fun JSONArray.toRings(): List<List<GeoPoint>> = buildList {
    for (ringIndex in 0 until length()) {
        add(getJSONArray(ringIndex).toGeoPointList())
    }
}
