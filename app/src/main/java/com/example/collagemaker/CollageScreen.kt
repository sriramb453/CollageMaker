package com.example.collagemaker

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.collagemaker.ui.theme.AccentCyan
import com.example.collagemaker.ui.theme.GrayText
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

enum class LayoutType {
    TWO_PHOTO, THREE_PHOTO, FOUR_PHOTO, FIVE_PHOTO, SCRAPBOOK
}

val Gradients = listOf(
    listOf(Color(0xFF84FAB0), Color(0xFF8FD3F4)),
    listOf(Color(0xFFA18CD1), Color(0xFFFBC2EB)),
    listOf(Color(0xFF4FACFE), Color(0xFF00F2FE)),
    listOf(Color(0xFFABECD6), Color(0xFFFBD786)),
    listOf(Color(0xFFFF9A9E), Color(0xFFFAD0C4)),
    listOf(Color(0xFF2196F3), Color(0xFF00BCD4))
)

data class ImageState(
    val id: String = UUID.randomUUID().toString(),
    val resId: Int? = null,
    val uri: Uri? = null,
    val offset: Offset = Offset.Zero,
    val scale: Float = 1f,
    val rotation: Float = 0f
)

data class TextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val offset: Offset = Offset(200f, 200f),
    val color: Color = Color.White,
    val fontSize: Float = 24f,
    val scale: Float = 1f,
    val rotation: Float = 0f
)

val ImageStateSaver = listSaver<ImageState, Any?>(
    save = { listOf(it.id, it.resId, it.uri?.toString(), it.offset.x, it.offset.y, it.scale, it.rotation) },
    restore = { data ->
        ImageState(
            id = data[0] as String,
            resId = data[1] as Int?,
            uri = (data[2] as String?)?.let { Uri.parse(it) },
            offset = Offset(data[3] as Float, data[4] as Float),
            scale = data[5] as Float,
            rotation = data[6] as Float
        )
    }
)

val TextOverlaySaver = listSaver<TextOverlay, Any?>(
    save = { listOf(it.id, it.text, it.offset.x, it.offset.y, it.color.toArgb(), it.fontSize, it.scale, it.rotation) },
    restore = {
        TextOverlay(
            id = it[0] as String,
            text = it[1] as String,
            offset = Offset(it[2] as Float, it[3] as Float),
            color = Color(it[4] as Int),
            fontSize = it[5] as Float,
            scale = it[6] as Float,
            rotation = it[7] as Float
        )
    }
)

enum class AppScreen {
    LAYOUTS, ADJUST, ADD, MODES, SETTINGS
}

@Composable
fun CollageScreen(
    isDarkMode: Boolean = true,
    onDarkModeChange: (Boolean) -> Unit = {}
) {
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.LAYOUTS) }
    var selectedLayout by rememberSaveable { mutableStateOf(LayoutType.TWO_PHOTO) }
    var spacing by rememberSaveable { mutableFloatStateOf(8f) }
    var cornerRadius by rememberSaveable { mutableFloatStateOf(8f) }
    
    val defaultCanvasColor = if (isDarkMode) Color.Black else Color.White
    var canvasBackgroundColor by rememberSaveable(isDarkMode, stateSaver = Saver(
        save = { it.toArgb() },
        restore = { Color(it) }
    )) { mutableStateOf(defaultCanvasColor) }

    var canvasBackgroundGradient by rememberSaveable(stateSaver = Saver(
        save = { it?.map { c -> c.toArgb() } ?: emptyList() },
        restore = { if (it.isEmpty()) null else it.map { argb -> Color(argb) } }
    )) { mutableStateOf<List<Color>?>(null) }
    
    var canvasBackgroundImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    
    val scrapbookImages = rememberSaveable(saver = listSaver<MutableList<ImageState>, Any?>(
        save = { list -> list.map { item -> with(ImageStateSaver) { save(item) } } },
        restore = { data -> (data as List<*>).map { item -> ImageStateSaver.restore(item as List<Any?>)!! }.toMutableStateList() }
    )) { mutableStateListOf() }

    val gridImageStates = rememberSaveable(saver = listSaver<MutableMap<Int, ImageState>, Any?>(
        save = { map -> map.entries.map { entry -> listOf(entry.key, with(ImageStateSaver) { save(entry.value) }) } },
        restore = { data ->
            val map = mutableStateMapOf<Int, ImageState>()
            (data as List<*>).forEach { item ->
                val list = item as List<*>
                map[list[0] as Int] = ImageStateSaver.restore(list[1] as List<Any?>)!!
            }
            map
        }
    )) { mutableStateMapOf() }

    val textOverlays = rememberSaveable(saver = listSaver<MutableMap<String, TextOverlay>, Any?>(
        save = { map -> map.values.map { item -> with(TextOverlaySaver) { save(item) } } },
        restore = { data ->
            val map = mutableStateMapOf<String, TextOverlay>()
            (data as List<*>).forEach { item ->
                val overlay = TextOverlaySaver.restore(item as List<Any?>)!!
                map[overlay.id] = overlay
            }
            map
        }
    )) { mutableStateMapOf() }

    var showTextDialog by rememberSaveable { mutableStateOf(false) }
    var textToEdit by remember { mutableStateOf<TextOverlay?>(null) }

    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var colorPickerTarget by rememberSaveable { mutableStateOf("SOLID") } // SOLID, GRADIENT_1, GRADIENT_2

    val graphicsLayer = rememberGraphicsLayer()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            if (selectedLayout == LayoutType.SCRAPBOOK) {
                scrapbookImages.add(ImageState(uri = it))
            }
        }
    }

    val backgroundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            canvasBackgroundImageUri = it
            canvasBackgroundGradient = null
            canvasBackgroundColor = Color.Transparent
        }
    }

    // Grid slots count
    val gridSlotsCount = when (selectedLayout) {
        LayoutType.TWO_PHOTO -> 2
        LayoutType.THREE_PHOTO -> 3
        LayoutType.FOUR_PHOTO -> 4
        LayoutType.FIVE_PHOTO -> 5
        LayoutType.SCRAPBOOK -> 0
    }
    
    LaunchedEffect(gridSlotsCount) {
        for (i in 0 until gridSlotsCount) {
            if (!gridImageStates.containsKey(i)) {
                gridImageStates[i] = ImageState()
            }
        }
    }

    var activeSlotForPicker by rememberSaveable { mutableStateOf<Int?>(null) }

    val gridGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            activeSlotForPicker?.let { index ->
                val current = gridImageStates[index] ?: ImageState()
                gridImageStates[index] = current.copy(uri = it, resId = null)
            }
        }
        activeSlotForPicker = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomNavigationBar(
                currentScreen = currentScreen,
                onScreenSelect = { currentScreen = it }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (currentScreen) {
                AppScreen.LAYOUTS -> LayoutsScreen(
                    onLayoutSelect = {
                        selectedLayout = it
                        currentScreen = AppScreen.ADJUST
                    },
                    onOpenSettings = { currentScreen = AppScreen.SETTINGS }
                )
                AppScreen.ADJUST -> EditorScreen(
                    selectedLayout = selectedLayout,
                    gridImageStates = gridImageStates,
                    scrapbookImages = scrapbookImages,
                    spacing = spacing,
                    cornerRadius = cornerRadius,
                    backgroundColor = canvasBackgroundColor,
                    backgroundGradient = canvasBackgroundGradient,
                    backgroundImageUri = canvasBackgroundImageUri,
                    textOverlays = textOverlays,
                    graphicsLayer = graphicsLayer,
                    onSpacingChange = { spacing = it },
                    onCornerRadiusChange = { cornerRadius = it },
                    onSlotClick = { activeSlotForPicker = it },
                    onTransform = { index, pan, zoom, rotation ->
                        val current = gridImageStates[index] ?: ImageState()
                        gridImageStates[index] = current.copy(
                            offset = current.offset + pan,
                            scale = (current.scale * zoom).coerceIn(0.1f, 10f),
                            rotation = current.rotation + rotation
                        )
                    },
                    onReset = { index ->
                        gridImageStates[index] = gridImageStates[index]?.copy(scale = 1f, rotation = 0f, offset = Offset.Zero) ?: ImageState()
                    },
                    onBackgroundColorChange = { 
                        canvasBackgroundColor = it
                        canvasBackgroundGradient = null
                        canvasBackgroundImageUri = null
                    },
                    onBackgroundGradientChange = {
                        canvasBackgroundGradient = it
                        canvasBackgroundColor = Color.Transparent
                        canvasBackgroundImageUri = null
                    },
                    onBackgroundPickerClick = { backgroundPickerLauncher.launch(arrayOf("image/*")) },
                    onCustomColorClick = { 
                        colorPickerTarget = "SOLID"
                        showColorPicker = true 
                    },
                    onTextTransform = { id, pan, zoom, rot ->
                        textOverlays[id]?.let {
                            textOverlays[id] = it.copy(
                                offset = it.offset + pan,
                                scale = (it.scale * zoom).coerceIn(0.5f, 5f),
                                rotation = it.rotation + rot
                            )
                        }
                    },
                    onTextClick = { overlay ->
                        textToEdit = overlay
                        showTextDialog = true
                    },
                    onSave = {
                        coroutineScope.launch {
                            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                            saveBitmapToGallery(context, bitmap)
                        }
                    },
                    onExport = {
                        coroutineScope.launch {
                            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                            saveBitmapToGallery(context, bitmap)
                            Toast.makeText(context, "Exported in High Quality!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenSettings = { currentScreen = AppScreen.SETTINGS }
                )
                AppScreen.MODES -> ModesScreen(
                    selectedLayout = selectedLayout,
                    onLayoutTypeChange = { selectedLayout = it },
                    gridImageStates = gridImageStates,
                    scrapbookImages = scrapbookImages,
                    spacing = spacing,
                    cornerRadius = cornerRadius,
                    backgroundColor = canvasBackgroundColor,
                    backgroundGradient = canvasBackgroundGradient,
                    backgroundImageUri = canvasBackgroundImageUri,
                    textOverlays = textOverlays,
                    onSlotClick = { activeSlotForPicker = it },
                    onScrapbookTransform = { index, pan, zoom, rotation ->
                        val current = scrapbookImages[index]
                        scrapbookImages[index] = current.copy(
                            offset = current.offset + pan,
                            scale = (current.scale * zoom).coerceIn(0.1f, 10f),
                            rotation = current.rotation + rotation
                        )
                    },
                    onScrapbookReset = { index ->
                        scrapbookImages[index] = scrapbookImages[index].copy(scale = 1f, rotation = 0f, offset = Offset.Zero)
                    },
                    onAddText = {
                        textToEdit = null
                        showTextDialog = true
                    },
                    onBackgroundColorChange = { 
                        canvasBackgroundColor = it
                        canvasBackgroundGradient = null
                        canvasBackgroundImageUri = null
                    },
                    onBackgroundGradientChange = {
                        canvasBackgroundGradient = it
                        canvasBackgroundColor = Color.Transparent
                        canvasBackgroundImageUri = null
                    },
                    onBackgroundPickerClick = { backgroundPickerLauncher.launch(arrayOf("image/*")) },
                    onCustomColorClick = { 
                        colorPickerTarget = "SOLID"
                        showColorPicker = true 
                    },
                    onCustomGradientClick = {
                        colorPickerTarget = "GRADIENT_1"
                        showColorPicker = true
                    },
                    onImportImage = { galleryLauncher.launch(arrayOf("image/*")) },
                    onTextTransform = { id, pan, zoom, rot ->
                        textOverlays[id]?.let {
                            textOverlays[id] = it.copy(
                                offset = it.offset + pan,
                                scale = (it.scale * zoom).coerceIn(0.5f, 5f),
                                rotation = it.rotation + rot
                            )
                        }
                    },
                    onTextClick = { overlay ->
                        textToEdit = overlay
                        showTextDialog = true
                    }
                )
                AppScreen.SETTINGS -> SettingsScreen(
                    isDarkMode = isDarkMode,
                    onDarkModeChange = onDarkModeChange,
                    onBack = { currentScreen = AppScreen.LAYOUTS }
                )
                AppScreen.ADD -> {
                    LaunchedEffect(Unit) {
                        textToEdit = null
                        showTextDialog = true
                        currentScreen = AppScreen.ADJUST
                    }
                }
            }
        }
    }

 
    if (showColorPicker) {
        CustomColorPickerDialog(
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                when (colorPickerTarget) {
                    "SOLID" -> {
                        canvasBackgroundColor = color
                        canvasBackgroundGradient = null
                        canvasBackgroundImageUri = null
                        showColorPicker = false
                    }
                    "GRADIENT_1" -> {
                        val currentGradient = canvasBackgroundGradient ?: listOf(color, Color.White)
                        canvasBackgroundGradient = listOf(color, currentGradient[1])
                        canvasBackgroundColor = Color.Transparent
                        canvasBackgroundImageUri = null
                        colorPickerTarget = "GRADIENT_2"
                    }
                    "GRADIENT_2" -> {
                        val currentGradient = canvasBackgroundGradient ?: listOf(Color.White, color)
                        canvasBackgroundGradient = listOf(currentGradient[0], color)
                        canvasBackgroundColor = Color.Transparent
                        canvasBackgroundImageUri = null
                        showColorPicker = false
                    }
                }
            }
        )
    }


    if (activeSlotForPicker != null) {
        ImagePickerDialog(
            onDismiss = { activeSlotForPicker = null },
            onGalleryPick = { 
                gridGalleryLauncher.launch(arrayOf("image/*"))
            }
        )
    }


    if (showTextDialog) {
        TextOverlayDialog(
            overlay = textToEdit,
            onDismiss = { 
                showTextDialog = false
                textToEdit = null
            },
            onConfirm = { text, color, fontSize ->
                val id = textToEdit?.id ?: UUID.randomUUID().toString()
                val offset = textToEdit?.offset ?: Offset(200f, 200f)
                val scale = 1f 
                val rotation = textToEdit?.rotation ?: 0f
                textOverlays[id] = TextOverlay(id, text, offset, color, fontSize, scale, rotation)
                showTextDialog = false
                textToEdit = null
            },
            onDelete = {
                textToEdit?.let { textOverlays.remove(it.id) }
                showTextDialog = false
                textToEdit = null
            }
        )
    }
}

@Composable
fun BottomNavigationBar(
    currentScreen: AppScreen,
    onScreenSelect: (AppScreen) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            Triple(AppScreen.LAYOUTS, "Layouts", Icons.Default.GridView),
            Triple(AppScreen.ADJUST, "Adjust", Icons.Default.Tune),
            Triple(AppScreen.ADD, "Text", Icons.Default.TextFields),
            Triple(AppScreen.MODES, "Modes", Icons.Default.Layers)
        )

        items.forEach { (screen, label, icon) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) },
                selected = currentScreen == screen,
                onClick = { onScreenSelect(screen) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = Color.LightGray,
                    unselectedTextColor = Color.LightGray,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun LayoutsScreen(
    onLayoutSelect: (LayoutType) -> Unit,
    onOpenSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant) // Gray/SurfaceVariant background
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "GALLERY", 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        Icons.Default.Home, 
                        contentDescription = null, 
                        modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape).padding(4.dp).clickable { onOpenSettings() },
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        item {

            Text(
                "CHOOSE LAYOUT", 
                fontSize = 20.sp, 
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.weight(1f)) { 
                        LayoutCard("2 GRID", LayoutType.TWO_PHOTO, onLayoutSelect)
                    }
                    Box(Modifier.weight(1f)) { 
                        LayoutCard("3 GRID", LayoutType.THREE_PHOTO, onLayoutSelect)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.weight(1f)) { 
                        LayoutCard("4 GRID", LayoutType.FOUR_PHOTO, onLayoutSelect)
                    }
                    Box(Modifier.weight(1f)) { 
                        LayoutCard("5 GRID", LayoutType.FIVE_PHOTO, onLayoutSelect)
                    }
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun LayoutCard(
    label: String,
    type: LayoutType,
    onSelect: (LayoutType) -> Unit
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant) // Gray/SurfaceVariant background
                .padding(vertical = 4.dp, horizontal = 12.dp)
        ) {
            Text(
                label, 
                fontSize = 16.sp, 
                fontWeight = FontWeight.Black, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                .clickable { onSelect(type) }
                .padding(12.dp)
        ) {
            LayoutPreviewIcon(type)
        }
    }
}

@Composable
fun LayoutPreviewIcon(type: LayoutType) {
    val img0 = R.drawable.img_10
    val img1 = R.drawable.img_11
    val img2 = R.drawable.img_2
    val img3 = R.drawable.img_3
    val img4 = R.drawable.img_4
    val img5 = R.drawable.img_5
    val img6 = R.drawable.img_6
    val img7 = R.drawable.img_7
    val img8 = R.drawable.img_8
    val img9 = R.drawable.img_9
    val img10=R.drawable.img
    val img11=R.drawable.img_1
    
    when (type) {
        LayoutType.TWO_PHOTO -> {
            Row(Modifier.fillMaxSize()) {
                Image(painterResource(img10), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color.Black))
                Image(painterResource(img9), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color.Black))
            }
        }
        LayoutType.THREE_PHOTO -> {
            Row(Modifier.fillMaxSize()) {
                Image(painterResource(img2), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color.Black))
                Column(Modifier.weight(1f)) {
                    Image(painterResource(img3), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth().border(0.5.dp, Color.Black))
                    Image(painterResource(img4), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth().border(0.5.dp, Color.Black))
                }
            }
        }
        LayoutType.FOUR_PHOTO -> {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    Image(painterResource(img5), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color.Black))
                    Image(painterResource(img6), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color.Black))
                }
                Row(Modifier.weight(1f)) {
                    Image(painterResource(img7), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color.Black))
                    Image(painterResource(img8), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color.Black))
                }
            }
        }
        LayoutType.FIVE_PHOTO -> {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    Image(painterResource(img9), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color.Black))
                    Image(painterResource(img0), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color.Black))
                }
                Row(Modifier.weight(1f)) {
                    Image(painterResource(img11), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color.Black))
                    Image(painterResource(img1), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color.Black))
                    Image(painterResource(img10), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color.Black))
                }
            }
        }
        LayoutType.SCRAPBOOK -> {
            Box(Modifier.fillMaxSize()) {
                Image(painterResource(img4), null, contentScale = ContentScale.Crop, modifier = Modifier.size(70.dp).offset(x = 10.dp, y = 20.dp).graphicsLayer(rotationZ = -12f).border(1.dp, Color.Black))
                Image(painterResource(img6), null, contentScale = ContentScale.Crop, modifier = Modifier.size(60.dp).align(Alignment.Center).graphicsLayer(rotationZ = 8f).border(1.dp, Color.Black))
            }
        }
    }
}

@Composable
fun EditorScreen(
    selectedLayout: LayoutType,
    gridImageStates: Map<Int, ImageState>,
    scrapbookImages: List<ImageState>,
    spacing: Float,
    cornerRadius: Float,
    backgroundColor: Color,
    backgroundGradient: List<Color>?,
    backgroundImageUri: Uri?,
    textOverlays: Map<String, TextOverlay>,
    graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    onSpacingChange: (Float) -> Unit,
    onCornerRadiusChange: (Float) -> Unit,
    onSlotClick: (Int) -> Unit,
    onTransform: (Int, Offset, Float, Float) -> Unit,
    onReset: (Int) -> Unit,
    onBackgroundColorChange: (Color) -> Unit,
    onBackgroundGradientChange: (List<Color>) -> Unit,
    onBackgroundPickerClick: () -> Unit,
    onCustomColorClick: () -> Unit,
    onTextTransform: (String, Offset, Float, Float) -> Unit,
    onTextClick: (TextOverlay) -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "GALLERY", 
                    fontSize = 24.sp, 
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    Icons.Default.Home, 
                    contentDescription = null, 
                    modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape).padding(4.dp).clickable { onOpenSettings() },
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Text(
            "CUSTOM EDITOR", 
            fontSize = 20.sp, 
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Box(
            modifier = Modifier
                .height(400.dp) // Fixed height for canvas to allow scrolling in landscape
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(12.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .drawWithContent {
                    graphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(graphicsLayer)
                }
                .background(
                    if (backgroundGradient != null) Brush.linearGradient(backgroundGradient)
                    else Brush.verticalGradient(listOf(backgroundColor, backgroundColor))
                )
        ) {
            if (backgroundImageUri != null) {
                AsyncImage(
                    model = backgroundImageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Box(modifier = Modifier.fillMaxSize().padding(if (selectedLayout == LayoutType.SCRAPBOOK) 0.dp else spacing.dp)) {
                if (selectedLayout == LayoutType.SCRAPBOOK) {
                    ScrapbookLayout(
                        images = scrapbookImages,
                        onTransform = { _, _, _, _ -> },
                        onReset = { }
                    )
                } else {
                    CollageLayout(
                        layoutType = selectedLayout,
                        imageStates = gridImageStates,
                        spacing = spacing,
                        cornerRadius = cornerRadius,
                        onSlotClick = onSlotClick,
                        onTransform = onTransform,
                        onReset = onReset
                    )
                }
                
                textOverlays.values.forEach { overlay ->
                    InteractiveText(
                        overlay = overlay,
                        onTransform = { pan, zoom, rot -> onTextTransform(overlay.id, pan, zoom, rot) },
                        onClick = { onTextClick(overlay) }
                    )
                }
            }
        }

        AdjustmentPanel(
            spacing = spacing,
            onSpacingChange = onSpacingChange,
            radius = cornerRadius,
            onRadiusChange = onCornerRadiusChange,
            onBackgroundColorChange = onBackgroundColorChange,
            onBackgroundGradientChange = onBackgroundGradientChange,
            onBackgroundPickerClick = onBackgroundPickerClick,
            onCustomColorClick = onCustomColorClick,
            onSave = onSave,
            onExport = onExport
        )
    }
}

@Composable
fun InteractiveText(
    overlay: TextOverlay,
    onTransform: (Offset, Float, Float) -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(overlay.offset.x.roundToInt(), overlay.offset.y.roundToInt()) }
            .graphicsLayer(
                scaleX = overlay.scale,
                scaleY = overlay.scale,
                rotationZ = overlay.rotation
            )
            .pointerInput(overlay.id) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    onTransform(pan, zoom, rotation)
                }
            }
            .pointerInput(overlay.id) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(8.dp)
    ) {
        Text(
            text = overlay.text,
            color = overlay.color,
            fontSize = overlay.fontSize.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun AdjustmentPanel(
    spacing: Float,
    onSpacingChange: (Float) -> Unit,
    radius: Float,
    onRadiusChange: (Float) -> Unit,
    onBackgroundColorChange: (Color) -> Unit,
    onBackgroundGradientChange: (List<Color>) -> Unit,
    onBackgroundPickerClick: () -> Unit,
    onCustomColorClick: () -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit
) {
    val colors = listOf(
        Color.White, Color.Black, Color(0xFFF44336), Color(0xFF2196F3), 
        Color(0xFF4CAF50), Color(0xFFFFEB3B), Color(0xFF9C27B0), Color(0xFFFF9800)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("RADIUS:${radius.toInt()}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Slider(
                value = radius,
                onValueChange = onRadiusChange,
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.onBackground, activeTrackColor = MaterialTheme.colorScheme.onBackground)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("SPACING:${spacing.toInt()}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Slider(
                value = spacing,
                onValueChange = onSpacingChange,
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.onBackground, activeTrackColor = MaterialTheme.colorScheme.onBackground)
            )

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text("SAVE", fontWeight = FontWeight.Black)
                }
                Button(
                    onClick = onExport,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text("EXPORT", fontWeight = FontWeight.Black)
                }
            }

            Spacer(Modifier.height(16.dp))
            
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                item {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(32.dp).clickable { onBackgroundPickerClick() }, tint = MaterialTheme.colorScheme.onBackground)
                }
                items(colors) { color ->
                    Box(Modifier.size(32.dp).clip(CircleShape).background(color).border(1.dp, MaterialTheme.colorScheme.onBackground, CircleShape).clickable { onBackgroundColorChange(color) })
                }
                items(Gradients) { gradientColors ->
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Brush.linearGradient(gradientColors)).border(1.dp, MaterialTheme.colorScheme.onBackground, CircleShape).clickable { onBackgroundGradientChange(gradientColors) })
                }
                item {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(32.dp).clickable { onCustomColorClick() }, tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}

@Composable
fun ModesScreen(
    selectedLayout: LayoutType,
    onLayoutTypeChange: (LayoutType) -> Unit,
    gridImageStates: Map<Int, ImageState>,
    scrapbookImages: List<ImageState>,
    spacing: Float,
    cornerRadius: Float,
    backgroundColor: Color,
    backgroundGradient: List<Color>?,
    backgroundImageUri: Uri?,
    textOverlays: Map<String, TextOverlay>,
    onSlotClick: (Int) -> Unit,
    onScrapbookTransform: (Int, Offset, Float, Float) -> Unit,
    onScrapbookReset: (Int) -> Unit,
    onAddText: () -> Unit,
    onBackgroundColorChange: (Color) -> Unit,
    onBackgroundGradientChange: (List<Color>) -> Unit,
    onBackgroundPickerClick: () -> Unit,
    onCustomColorClick: () -> Unit,
    onCustomGradientClick: () -> Unit,
    onImportImage: () -> Unit,
    onTextTransform: (String, Offset, Float, Float) -> Unit,
    onTextClick: (TextOverlay) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "GALLERY", 
                    fontSize = 24.sp, 
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.LightGray.copy(alpha = 0.2f),
                modifier = Modifier.height(54.dp).padding(horizontal = 16.dp)
            ) {
                Row(modifier = Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val isFreeform = selectedLayout == LayoutType.SCRAPBOOK
                    
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (!isFreeform) Color.White else Color.Transparent)
                            .clickable { onLayoutTypeChange(LayoutType.TWO_PHOTO) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Grid", color = if (!isFreeform) Color.Black else Color.Gray, fontWeight = FontWeight.Bold)
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1.2f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isFreeform) AccentCyan else Color.Transparent)
                            .clickable { onLayoutTypeChange(LayoutType.SCRAPBOOK) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Gesture, contentDescription = null, modifier = Modifier.size(18.dp), tint = if(isFreeform) Color.White else Color.Gray)
                            Spacer(Modifier.width(6.dp))
                            Text("Freeform", color = if (isFreeform) Color.White else Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .height(400.dp) // Fixed height for landscape support
                .fillMaxWidth()
                .padding(20.dp)
                .shadow(12.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (backgroundGradient != null) Brush.linearGradient(backgroundGradient)
                    else Brush.verticalGradient(listOf(backgroundColor, backgroundColor))
                )
        ) {
             if (backgroundImageUri != null) {
                AsyncImage(
                    model = backgroundImageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            if (selectedLayout == LayoutType.SCRAPBOOK) {
                ScrapbookLayout(
                    images = scrapbookImages,
                    onTransform = onScrapbookTransform,
                    onReset = onScrapbookReset
                )
            } else {
                Box(Modifier.fillMaxSize().padding(spacing.dp)) {
                    CollageLayout(
                        layoutType = selectedLayout,
                        imageStates = gridImageStates,
                        spacing = spacing,
                        cornerRadius = cornerRadius,
                        onSlotClick = onSlotClick,
                        onTransform = { _, _, _, _ -> },
                        onReset = { }
                    )
                }
            }

            textOverlays.values.forEach { overlay ->
                InteractiveText(
                    overlay = overlay,
                    onTransform = { pan, zoom, rot -> onTextTransform(overlay.id, pan, zoom, rot) },
                    onClick = { onTextClick(overlay) }
                )
            }
        }

        ModesToolsPanel(
            onAddText = onAddText,
            onBackgroundColorChange = onBackgroundColorChange,
            onBackgroundGradientChange = onBackgroundGradientChange,
            onBackgroundPickerClick = onBackgroundPickerClick,
            onCustomColorClick = onCustomColorClick,
            onCustomGradientClick = onCustomGradientClick,
            onImportImage = onImportImage
        )
    }
}

@Composable
fun ModesToolsPanel(
    onAddText: () -> Unit,
    onBackgroundColorChange: (Color) -> Unit,
    onBackgroundGradientChange: (List<Color>) -> Unit,
    onBackgroundPickerClick: () -> Unit,
    onCustomColorClick: () -> Unit,
    onCustomGradientClick: () -> Unit,
    onImportImage: () -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    
    val colors = listOf(
        Color.White, Color.Black, Color(0xFFF44336), Color(0xFF2196F3), 
        Color(0xFF4CAF50), Color(0xFFFFEB3B), Color(0xFF9C27B0), Color(0xFFFF9800)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        shadowElevation = 24.dp
    ) {
        Column {
            if (showColorPicker) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, start = 20.dp, end = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                         Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color.LightGray, CircleShape)
                                .clickable { onBackgroundPickerClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    items(colors) { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(1.dp, Color.LightGray.copy(alpha = 0.3f), CircleShape)
                                .clickable { onBackgroundColorChange(color) }
                        )
                    }
                    items(Gradients) { gradientColors ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(gradientColors))
                                .border(1.dp, Color.LightGray.copy(alpha = 0.3f), CircleShape)
                                .clickable { onBackgroundGradientChange(gradientColors) }
                        )
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color.LightGray, CircleShape)
                                .clickable { onCustomColorClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color.Red, Color.Blue)))
                                .border(1.dp, Color.LightGray.copy(alpha = 0.3f), CircleShape)
                                .clickable { onCustomGradientClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                        }
                    }
                }
            }
            
            Row(
                modifier = Modifier.padding(24.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeToolItem("Text", Icons.Default.TextFields, onAddText, modifier = Modifier.weight(1f))
                ModeToolItem("Background", Icons.Default.FormatColorFill, { showColorPicker = !showColorPicker }, modifier = Modifier.weight(1f), isSelected = showColorPicker)
                ModeToolItem("Gallery", Icons.Default.Image, onImportImage, modifier = Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(40.dp).background(Color(0xFFEEEEEE)))
                ModeToolItem("Layers", Icons.Default.Layers, {}, modifier = Modifier.weight(1f), isSelected = true)
            }
        }
    }
}

@Composable
fun ModeToolItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable { onClick() }
    ) {
        Icon(
            icon, 
            contentDescription = label, 
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label, 
            fontSize = 12.sp, 
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
        )
        if (isSelected) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "Settings", 
                fontSize = 24.sp, 
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBack) {
                Text("Save", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            }
        }

        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("PREFERENCES", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column {
                    SettingsToggleItem("Theme", Icons.Outlined.DarkMode, isDarkMode, onDarkModeChange, "Light", "Dark")
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    SettingsToggleItem("Export Quality", Icons.Outlined.Hd, true, {}, "PNG", "JPEG")
                }
            }
        }
    }
}


@Composable
fun SettingsToggleItem(
    label: String, 
    icon: ImageVector, 
    checked: Boolean, 
    onCheckedChange: (Boolean) -> Unit,
    leftLabel: String,
    rightLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.width(16.dp))
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (!checked) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .shadow(if (!checked) 1.dp else 0.dp, RoundedCornerShape(12.dp))
                        .clickable { onCheckedChange(false) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(leftLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if(!checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (checked) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .shadow(if (checked) 1.dp else 0.dp, RoundedCornerShape(12.dp))
                        .clickable { onCheckedChange(true) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(rightLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if(checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun ScrapbookLayout(
    images: List<ImageState>,
    onTransform: (Int, Offset, Float, Float) -> Unit,
    onReset: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        images.forEachIndexed { index, imageState ->
            ScrapbookItem(
                imageState = imageState,
                onTransform = { pan, zoom, rot -> onTransform(index, pan, zoom, rot) },
                onReset = { onReset(index) }
            )
        }
    }
}

@Composable
fun ScrapbookItem(
    imageState: ImageState,
    onTransform: (Offset, Float, Float) -> Unit,
    onReset: () -> Unit
) {
    Box(
        modifier = Modifier
            .offset(imageState.offset.x.dp, imageState.offset.y.dp)
            .graphicsLayer(
                scaleX = imageState.scale,
                scaleY = imageState.scale,
                rotationZ = imageState.rotation
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    onTransform(pan, zoom, rotation)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onReset() }
                )
            }
    ) {
        ImageContent(imageState, modifier = Modifier.size(200.dp).shadow(4.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)))
    }
}

@Composable
fun ImageContent(imageState: ImageState, modifier: Modifier = Modifier) {
    if (imageState.uri != null) {
        AsyncImage(
            model = imageState.uri,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else if (imageState.resId != null) {
        Image(
            painter = painterResource(id = imageState.resId),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun CollageLayout(
    layoutType: LayoutType,
    imageStates: Map<Int, ImageState>,
    spacing: Float,
    cornerRadius: Float,
    onSlotClick: (Int) -> Unit,
    onTransform: (Int, Offset, Float, Float) -> Unit,
    onReset: (Int) -> Unit
) {
    when (layoutType) {
        LayoutType.TWO_PHOTO -> {
            Row(modifier = Modifier.fillMaxSize()) {
                CollageSlot(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    imageState = imageStates[0] ?: ImageState(),
                    cornerRadius = cornerCornerRadiusToDp(cornerRadius),
                    onClick = { onSlotClick(0) },
                    onTransform = { pan, zoom, rot -> onTransform(0, pan, zoom, rot) },
                    onReset = { onReset(0) }
                )
                Spacer(modifier = Modifier.width(spacing.dp))
                CollageSlot(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    imageState = imageStates[1] ?: ImageState(),
                    cornerRadius = cornerCornerRadiusToDp(cornerRadius),
                    onClick = { onSlotClick(1) },
                    onTransform = { pan, zoom, rot -> onTransform(1, pan, zoom, rot) },
                    onReset = { onReset(1) }
                )
            }
        }
        LayoutType.THREE_PHOTO -> {
            Row(modifier = Modifier.fillMaxSize()) {
                CollageSlot(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    imageState = imageStates[0] ?: ImageState(),
                    cornerRadius = cornerCornerRadiusToDp(cornerRadius),
                    onClick = { onSlotClick(0) },
                    onTransform = { pan, zoom, rot -> onTransform(0, pan, zoom, rot) },
                    onReset = { onReset(0) }
                )
                Spacer(modifier = Modifier.width(spacing.dp))
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    CollageSlot(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        imageState = imageStates[1] ?: ImageState(),
                        cornerRadius = cornerCornerRadiusToDp(cornerRadius),
                        onClick = { onSlotClick(1) },
                        onTransform = { pan, zoom, rot -> onTransform(1, pan, zoom, rot) },
                        onReset = { onReset(1) }
                    )
                    Spacer(modifier = Modifier.height(spacing.dp))
                    CollageSlot(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        imageState = imageStates[2] ?: ImageState(),
                        cornerRadius = cornerCornerRadiusToDp(cornerRadius),
                        onClick = { onSlotClick(2) },
                        onTransform = { pan, zoom, rot -> onTransform(2, pan, zoom, rot) },
                        onReset = { onReset(2) }
                    )
                }
            }
        }
        LayoutType.FOUR_PHOTO -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollageSlot(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageState = imageStates[0] ?: ImageState(),
                        cornerRadius = cornerCornerRadiusToDp(cornerRadius),
                        onClick = { onSlotClick(0) },
                        onTransform = { pan, zoom, rot -> onTransform(0, pan, zoom, rot) },
                        onReset = { onReset(0) }
                    )
                    Spacer(modifier = Modifier.width(spacing.dp))
                    CollageSlot(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageState = imageStates[1] ?: ImageState(),
                        cornerRadius = cornerCornerRadiusToDp(cornerRadius),
                        onClick = { onSlotClick(1) },
                        onTransform = { pan, zoom, rot -> onTransform(1, pan, zoom, rot) },
                        onReset = { onReset(1) }
                    )
                }
                Spacer(modifier = Modifier.height(spacing.dp))
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollageSlot(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageState = imageStates[2] ?: ImageState(),
                        cornerRadius = cornerCornerRadiusToDp(cornerRadius),
                        onClick = { onSlotClick(2) },
                        onTransform = { pan, zoom, rot -> onTransform(2, pan, zoom, rot) },
                        onReset = { onReset(2) }
                    )
                    Spacer(modifier = Modifier.width(spacing.dp))
                    CollageSlot(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageState = imageStates[3] ?: ImageState(),
                        cornerRadius = cornerCornerRadiusToDp(cornerRadius),
                        onClick = { onSlotClick(3) },
                        onTransform = { pan, zoom, rot -> onTransform(3, pan, zoom, rot) },
                        onReset = { onReset(3) }
                    )
                }
            }
        }
        LayoutType.FIVE_PHOTO -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollageSlot(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageState = imageStates[0] ?: ImageState(),
                        cornerRadius = cornerCornerRadiusToDp(cornerRadius),
                        onClick = { onSlotClick(0) },
                        onTransform = { pan, zoom, rot -> onTransform(0, pan, zoom, rot) },
                        onReset = { onReset(0) }
                    )
                    Spacer(modifier = Modifier.width(spacing.dp))
                    CollageSlot(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageState = imageStates[1] ?: ImageState(),
                        cornerRadius = cornerCornerRadiusToDp(cornerRadius),
                        onClick = { onSlotClick(1) },
                        onTransform = { pan, zoom, rot -> onTransform(1, pan, zoom, rot) },
                        onReset = { onReset(1) }
                    )
                }
                Spacer(modifier = Modifier.height(spacing.dp))
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollageSlot(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageState = imageStates[2] ?: ImageState(),
                        cornerRadius = cornerCornerRadiusToDp(cornerRadius),
                        onClick = { onSlotClick(2) },
                        onTransform = { pan, zoom, rot -> onTransform(2, pan, zoom, rot) },
                        onReset = { onReset(2) }
                    )
                    Spacer(modifier = Modifier.width(spacing.dp))
                    CollageSlot(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageState = imageStates[3] ?: ImageState(),
                        cornerRadius = cornerCornerRadiusToDp(cornerRadius),
                        onClick = { onSlotClick(3) },
                        onTransform = { pan, zoom, rot -> onTransform(3, pan, zoom, rot) },
                        onReset = { onReset(3) }
                    )
                    Spacer(modifier = Modifier.width(spacing.dp))
                    CollageSlot(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        imageState = imageStates[4] ?: ImageState(),
                        cornerRadius = cornerRadius,
                        onClick = { onSlotClick(4) },
                        onTransform = { pan, zoom, rot -> onTransform(4, pan, zoom, rot) },
                        onReset = { onReset(4) }
                    )
                }
            }
        }
        else -> {}
    }
}

fun cornerCornerRadiusToDp(radius: Float): Float {
    return radius
}

@Composable
fun CollageSlot(
    modifier: Modifier,
    imageState: ImageState,
    cornerRadius: Float,
    onClick: () -> Unit,
    onTransform: (Offset, Float, Float) -> Unit,
    onReset: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(Color.White)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onReset() }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    onTransform(pan, zoom, rotation)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if ((imageState.resId != null) || (imageState.uri != null)) {
            ImageContent(
                imageState = imageState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = imageState.scale,
                        scaleY = imageState.scale,
                        rotationZ = imageState.rotation,
                        translationX = imageState.offset.x,
                        translationY = imageState.offset.y
                    )
            )
        } else {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun TextOverlayDialog(
    overlay: TextOverlay?,
    onDismiss: () -> Unit,
    onConfirm: (String, Color, Float) -> Unit,
    onDelete: () -> Unit
) {
    var text by remember { mutableStateOf(overlay?.text ?: "") }
    var color by remember { mutableStateOf(overlay?.color ?: Color.Black) }
    var fontSize by remember { mutableFloatStateOf(overlay?.fontSize ?: 24f) }

    val colors = listOf(Color.Black, Color.White, Color.Red, Color.Blue, Color.Green, Color.Yellow, Color.Magenta)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (overlay == null) "Add Text" else "Edit Text", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Enter text here...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan, 
                        cursorColor = AccentCyan,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                Text("TEXT COLOR", fontSize = 12.sp, color = GrayText, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(colors) { c ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    if (color == c) 2.dp else 1.dp, 
                                    if (color == c) AccentCyan else Color.LightGray.copy(alpha = 0.3f),
                                    CircleShape
                                )
                                .clickable { color = c }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("FONT SIZE", fontSize = 12.sp, color = GrayText, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Text("${fontSize.toInt()}px", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 12f..100f,
                    colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onConfirm(text, color, fontSize) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (overlay != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = GrayText, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
fun ImagePickerDialog(
    onDismiss: () -> Unit,
    onGalleryPick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Select Image", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Button(
                    onClick = onGalleryPick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Browse Phone Files", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = GrayText, fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
fun CustomColorPickerDialog(
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    var red by remember { mutableFloatStateOf(0f) }
    var green by remember { mutableFloatStateOf(0f) }
    var blue by remember { mutableFloatStateOf(0f) }

    val color = Color(red / 255f, green / 255f, blue / 255f)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Pick Custom Color", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                ColorSlider("Red", red, { red = it }, Color.Red)
                ColorSlider("Green", green, { green = it }, Color.Green)
                ColorSlider("Blue", blue, { blue = it }, Color.Blue)
            }
        },
        confirmButton = {
            Button(
                onClick = { onColorSelected(color) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Select", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GrayText, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun ColorSlider(label: String, value: Float, onValueChange: (Float) -> Unit, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(value.toInt().toString(), fontSize = 12.sp, color = GrayText)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
        )
    }
}

fun saveBitmapToGallery(context: android.content.Context, bitmap: Bitmap) {
    val filename = "Collage_${System.currentTimeMillis()}.png"

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val contentResolver = context.contentResolver
    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    uri?.let {
        contentResolver.openOutputStream(it)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        
        contentValues.clear()
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
        contentResolver.update(it, contentValues, null, null)
        Toast.makeText(context, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
    } ?: run {
        Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
    }
}
