package com.ku.lostandfound.ui.location.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ku.lostandfound.data.CampusBoundary
import com.ku.lostandfound.data.CampusBuilding
import com.ku.lostandfound.data.CampusPath
import com.ku.lostandfound.data.IndoorPlace
import com.ku.lostandfound.data.LocationTab
import com.ku.lostandfound.data.OutdoorPin
import com.ku.lostandfound.data.PostType
import com.ku.lostandfound.ui.component.BackTitleBar
import com.ku.lostandfound.ui.component.CampusMapCanvas
import com.ku.lostandfound.ui.component.GreenSegmentedSwitch
import com.ku.lostandfound.util.GeoUtils

private val DeepGreen = Color(0xFF1B6425)
private val FieldGray = Color(0xFFF4F4F4)
private val DangerRed = Color(0xFFFF3B3B)

@Composable
fun LocationPickerScreen(
    postType: PostType,
    boundary: CampusBoundary,
    buildings: List<CampusBuilding>,
    referencePaths: List<CampusPath> = emptyList(),
    initialOutdoorPins: List<OutdoorPin> = emptyList(),
    initialIndoorPlaces: List<IndoorPlace> = emptyList(),
    onBackClick: () -> Unit,
    onSubmitLost: (outdoorPins: List<OutdoorPin>, indoorPlaces: List<IndoorPlace>) -> Unit,
    onSubmitFound: (outdoorPin: OutdoorPin?, indoorPlace: IndoorPlace?) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(LocationTab.OUTDOOR) }
    val outdoorPins = remember { mutableStateListOf<OutdoorPin>() }
    val indoorPlaces = remember { mutableStateListOf<IndoorPlace>() }
    var pendingBuilding by remember { mutableStateOf<CampusBuilding?>(null) }

    LaunchedEffect(Unit) {
        outdoorPins.clear()
        outdoorPins.addAll(initialOutdoorPins)
        indoorPlaces.clear()
        indoorPlaces.addAll(initialIndoorPlaces)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        BackTitleBar(
            title = if (postType == PostType.LOST) "분실 위치 추가" else "습득 위치 추가",
            onBackClick = onBackClick,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            GreenSegmentedSwitch(
                leftText = "외부",
                rightText = "내부",
                selectedLeft = selectedTab == LocationTab.OUTDOOR,
                onLeftClick = { selectedTab = LocationTab.OUTDOOR },
                onRightClick = { selectedTab = LocationTab.INDOOR },
            )

            Spacer(Modifier.height(18.dp))

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    LocationTab.OUTDOOR -> OutdoorLocationBody(
                        postType = postType,
                        boundary = boundary,
                        buildings = buildings,
                        referencePaths = referencePaths,
                        pins = outdoorPins,
                    )

                    LocationTab.INDOOR -> IndoorLocationBody(
                        postType = postType,
                        boundary = boundary,
                        buildings = buildings,
                        referencePaths = referencePaths,
                        indoorPlaces = indoorPlaces,
                        pendingBuilding = pendingBuilding,
                        onBuildingSelected = { pendingBuilding = it },
                    )
                }
            }

            Button(
                onClick = {
                    if (postType == PostType.LOST) {
                        onSubmitLost(outdoorPins.toList(), indoorPlaces.toList())
                    } else {
                        onSubmitFound(outdoorPins.firstOrNull(), indoorPlaces.firstOrNull())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DeepGreen, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp),
            ) {
                Text("등록하기", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    pendingBuilding?.let { building ->
        FloorSelectDialog(
            building = building,
            onDismiss = { pendingBuilding = null },
            onConfirm = { floor ->
                val place = IndoorPlace(
                    buildingId = building.id,
                    buildingName = building.name,
                    floor = floor,
                )
                if (postType == PostType.LOST) {
                    if (indoorPlaces.size < 3 && indoorPlaces.none { it.buildingId == place.buildingId && it.floor == place.floor }) {
                        indoorPlaces.add(place)
                    }
                } else {
                    indoorPlaces.clear()
                    indoorPlaces.add(place)
                    outdoorPins.clear()
                }
                pendingBuilding = null
            }
        )
    }
}

@Composable
private fun OutdoorLocationBody(
    postType: PostType,
    boundary: CampusBoundary,
    buildings: List<CampusBuilding>,
    referencePaths: List<CampusPath>,
    pins: MutableList<OutdoorPin>,
) {
    Column {
        CampusMapCanvas(
            boundary = boundary,
            buildings = buildings,
            referencePaths = referencePaths,
            outdoorPins = pins.toList(),
            showRoute = postType == PostType.LOST,
            modifier = Modifier
                .fillMaxWidth()
                .height(430.dp),
            onMapTap = { point ->
                // 캠퍼스 바깥만 막고, 참고 경로 위에 찍었는지는 검사하지 않는다.
                // 사용자가 길을 살짝 벗어나 찍어도 허용한다.
                if (!GeoUtils.pointInPolygon(point, boundary.polygon)) return@CampusMapCanvas

                if (postType == PostType.LOST) {
                    if (pins.size < 10) {
                        pins.add(OutdoorPin(order = pins.size + 1, point = point))
                    }
                } else {
                    pins.clear()
                    pins.add(OutdoorPin(order = 1, point = point))
                }
            }
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (postType == PostType.LOST) {
                CancelChip(text = "전체 취소") { pins.clear() }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "외부 핀 ${pins.size}/10 · 경로개수: ${if (pins.isEmpty()) 0 else 1}개",
                    color = Color(0xFF777777),
                    fontSize = 12.sp,
                )
            } else {
                CancelChip(text = "선택 취소") { pins.clear() }
                Spacer(Modifier.width(10.dp))
                Text("외부 핀 ${pins.size}/1", color = Color(0xFF777777), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun IndoorLocationBody(
    postType: PostType,
    boundary: CampusBoundary,
    buildings: List<CampusBuilding>,
    referencePaths: List<CampusPath>,
    indoorPlaces: MutableList<IndoorPlace>,
    pendingBuilding: CampusBuilding?,
    onBuildingSelected: (CampusBuilding) -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        val selectedBuildingIds = indoorPlaces.map { it.buildingId }.toSet() +
            listOfNotNull(pendingBuilding?.id)

        CampusMapCanvas(
            boundary = boundary,
            buildings = buildings,
            referencePaths = referencePaths,
            selectedBuildingIds = selectedBuildingIds,
            modifier = Modifier
                .fillMaxWidth()
                .height(430.dp),
            onMapTap = { point ->
                // 내부 선택에서는 건물만 클릭 대상이다. 경로선은 참고용이라 선택되지 않는다.
                GeoUtils.findBuildingAt(point, buildings)?.let(onBuildingSelected)
            }
        )

        Spacer(Modifier.height(14.dp))
        Text(
            text = if (postType == PostType.LOST) "건물 내부 위치 ${indoorPlaces.size}/3" else "건물 내부 위치 ${indoorPlaces.size}/1",
            color = Color(0xFF777777),
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))

        indoorPlaces.forEach { place ->
            IndoorPlaceRow(
                place = place,
                onRemoveClick = { indoorPlaces.remove(place) },
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun IndoorPlaceRow(place: IndoorPlace, onRemoveClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(FieldGray, RoundedCornerShape(9.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${place.buildingName} ${place.floor.toFloorText()}",
            color = Color(0xFF444444),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "X",
            color = Color(0xFF555555),
            fontSize = 22.sp,
            modifier = Modifier.clickable(onClick = onRemoveClick),
        )
    }
}

@Composable
private fun CancelChip(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(Color(0xFFFFF0F0), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🗑", fontSize = 12.sp)
        Spacer(Modifier.width(2.dp))
        Text(text, color = DangerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FloorSelectDialog(
    building: CampusBuilding,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val floors = remember(building) {
        val underground = if (building.undergroundLevels > 0) {
            (building.undergroundLevels downTo 1).map { -it }
        } else {
            emptyList()
        }
        val ground = (1..building.levels.coerceAtLeast(1)).toList()
        underground + ground
    }
    var selectedFloor by remember(building) { mutableStateOf(floors.firstOrNull() ?: 1) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(building.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("몇 층인지 선택해주세요.", fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(FieldGray, RoundedCornerShape(9.dp))
                            .clickable { expanded = true }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selectedFloor.toFloorText(),
                            color = Color.Black,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text("▼", color = Color(0xFF777777), fontSize = 16.sp)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        containerColor = Color.White,
                    ) {
                        floors.forEach { floor ->
                            DropdownMenuItem(
                                text = { Text(floor.toFloorText(), color = Color.Black) },
                                onClick = {
                                    selectedFloor = floor
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedFloor) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF356D3C)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .width(120.dp)
                    .height(50.dp),
            ) {
                Text("확인", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .background(FieldGray, RoundedCornerShape(10.dp))
                    .width(120.dp)
                    .height(50.dp),
            ) {
                Text("취소", color = Color(0xFF555555), fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(18.dp),
        containerColor = Color.White,
    )
}

private fun Int.toFloorText(): String = if (this < 0) "B${-this}층" else "${this}층"
