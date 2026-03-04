package com.refayatul.iqra

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.refayatul.iqra.ui.*
import com.refayatul.iqra.ui.theme.*
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()
            
            IqraTheme(darkTheme = uiState.isDarkMode) {
                IqraApp(
                    viewModel = viewModel,
                    uiState = uiState,
                    onRequestPermission = {
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IqraApp(
    viewModel: MainViewModel,
    uiState: IqraUiState,
    onRequestPermission: () -> Unit
) {
    BackHandler(enabled = uiState.currentScreen !is AppScreen.Home) {
        viewModel.navigateBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = uiState.currentScreen,
            transitionSpec = {
                if (targetState is AppScreen.Home) {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                } else {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                }
            },
            label = "ScreenNavigation"
        ) { screen ->
            when (screen) {
                is AppScreen.Home -> HomeScreen(viewModel, uiState, onRequestPermission)
                is AppScreen.Search -> SearchScreen(viewModel, uiState)
                is AppScreen.SurahDetail -> SurahDetailScreen(
                    surahId = screen.surahId,
                    initialAyahId = screen.ayahId,
                    ayahs = uiState.fullSurahAyahs,
                    onBack = { viewModel.navigateBack() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    uiState: IqraUiState,
    onRequestPermission: () -> Unit
) {
    Scaffold(
        containerColor = ComposeColor.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Iqra",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 4.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateToSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleDarkMode() }) {
                        Icon(
                            imageVector = if (uiState.isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ComposeColor.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ErrorMessage(uiState.errorMessage)

                Spacer(modifier = Modifier.height(20.dp))

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = uiState.isProcessing,
                        label = "ProcessingAnimation"
                    ) { isProcessing ->
                        if (isProcessing) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        } else {
                            if (uiState.isNoMatch) {
                                NoMatchDisplay { viewModel.resetState() }
                            } else {
                                ResultDisplay(uiState, 
                                    onCardClick = { id, ayah -> viewModel.navigateToSurah(id, ayah) },
                                    onPlayClick = { id, ayah -> viewModel.playRecitation(id, ayah) },
                                    onStopClick = { viewModel.stopRecitation() },
                                    onFetchTafsir = { id, ayah -> viewModel.fetchTafsir(id, ayah) },
                                    onTafsirSourceSelect = { viewModel.selectTafsirSource(it) }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(120.dp))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            "Auto Mode",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = uiState.isContinuousModeEnabled,
                            onCheckedChange = { viewModel.toggleContinuousMode() },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    ListeningLabel(uiState.isRecording)
                    AudioWaveformVisualizer(uiState.currentAmplitude, uiState.isRecording)
                    Spacer(modifier = Modifier.height(16.dp))
                    RecordingButtonPremium(
                        enabled = uiState.isModelLoaded && !uiState.isProcessing,
                        isRecording = uiState.isRecording,
                        onClick = {
                            onRequestPermission()
                            viewModel.toggleRecording()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: MainViewModel, uiState: IqraUiState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Search Surah or Verse...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = ComposeColor.Transparent,
                            unfocusedContainerColor = ComposeColor.Transparent,
                            focusedIndicatorColor = ComposeColor.Transparent,
                            unfocusedIndicatorColor = ComposeColor.Transparent
                        ),
                        singleLine = true,
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.searchQuery.length < 3) {
                item {
                    Text(
                        "Surah Index",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(uiState.surahIndex) { surah ->
                    ListItem(
                        headlineContent = { Text(surah.nameEn) },
                        supportingContent = { 
                            Text(surah.name, fontFamily = QuranFont, fontSize = 20.sp)
                        },
                        leadingContent = { Text(surah.id.toString()) },
                        modifier = Modifier.clickable { viewModel.navigateToSurah(surah.id, 1) }
                    )
                }
            } else {
                if (uiState.searchResults.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No verses found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(uiState.searchResults) { ayah ->
                        ListItem(
                            headlineContent = { 
                                Text(
                                    ayah.text_uthmani, 
                                    fontFamily = QuranFont, 
                                    fontSize = 24.sp, 
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                ) 
                            },
                            supportingContent = { Text("${ayah.surah_name_en} : ${ayah.ayah}\n${ayah.translationEn}") },
                            modifier = Modifier.clickable { viewModel.navigateToSurah(ayah.surah, ayah.ayah) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahDetailScreen(
    surahId: Int,
    initialAyahId: Int,
    ayahs: List<Ayah>,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val surahName = ayahs.firstOrNull()?.surah_name_en ?: "Surah"

    LaunchedEffect(surahId, initialAyahId) {
        val index = ayahs.indexOfFirst { it.ayah == initialAyahId }
        if (index != -1) {
            listState.animateScrollToItem(index)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(surahName, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(ayahs) { ayah ->
                val isHighlighted = ayah.ayah == initialAyahId
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isHighlighted) 
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) 
                        else 
                            MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 2.dp else 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ayah.ayah.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = { shareAyahAsImage(context, ayah) }) {
                                Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                text = ayah.text_uthmani,
                                style = TextStyle(
                                    fontFamily = QuranFont,
                                    fontSize = 28.sp,
                                    lineHeight = 1.6.em,
                                    textAlign = TextAlign.Start,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = ayah.translationEn,
                            style = TextStyle(
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = ayah.translationBn,
                            style = TextStyle(
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}

fun shareAyahAsImage(context: Context, ayah: Ayah) {
    val width = 1080
    val height = 1920
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    
    val bgGradient = android.graphics.LinearGradient(
        0f, 0f, 0f, height.toFloat(),
        intArrayOf(android.graphics.Color.parseColor("#1A1A1A"), android.graphics.Color.BLACK),
        null, android.graphics.Shader.TileMode.CLAMP
    )
    paint.shader = bgGradient
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null

    val quranTypeface = ResourcesCompat.getFont(context, R.font.quran_font)

    paint.color = android.graphics.Color.parseColor("#D4AF37")
    paint.textSize = 50f
    paint.textAlign = android.graphics.Paint.Align.CENTER
    canvas.drawText("${ayah.surah_name_en} : ${ayah.ayah}", width / 2f, 200f, paint)

    val arabicPaint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 80f
        typeface = quranTypeface
    }
    val arabicLayout = StaticLayout.Builder.obtain(
        ayah.text_uthmani, 0, ayah.text_uthmani.length, arabicPaint, width - 200
    )
    .setAlignment(Layout.Alignment.ALIGN_CENTER)
    .setLineSpacing(0f, 1.6f)
    .build()

    canvas.save()
    canvas.translate(width / 2f - (width - 200) / 2f, 400f)
    arabicLayout.draw(canvas)
    canvas.restore()

    paint.color = android.graphics.Color.WHITE
    paint.alpha = 40
    val dividerY = 450f + arabicLayout.height
    canvas.drawLine(300f, dividerY, width - 300f, dividerY, paint)
    paint.alpha = 255

    val bnPaint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#BDBDBD")
        textSize = 45f
    }
    val bnLayout = StaticLayout.Builder.obtain(
        ayah.translationBn, 0, ayah.translationBn.length, bnPaint, width - 240
    )
    .setAlignment(Layout.Alignment.ALIGN_CENTER)
    .setLineSpacing(0f, 1.3f)
    .build()

    canvas.save()
    canvas.translate(width / 2f - (width - 240) / 2f, dividerY + 100f)
    bnLayout.draw(canvas)
    canvas.restore()

    paint.color = android.graphics.Color.WHITE
    paint.textSize = 60f
    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
    canvas.drawText("Iqra", width / 2f, height - 150f, paint)

    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val imageFile = File(cachePath, "ayah_share.png")
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val contentUri = FileProvider.getUriForFile(context, "com.refayatul.iqra.fileprovider", imageFile)
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_STREAM, contentUri)
            type = "image/png"
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Ayah via"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun AudioWaveformVisualizer(amplitudes: List<Float>, isRecording: Boolean) {
    AnimatedVisibility(
        visible = isRecording,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            amplitudes.forEach { amplitude ->
                val animatedHeight by animateFloatAsState(
                    targetValue = if (isRecording) 8.dp.value + (amplitude * 48.dp.value) else 4.dp.value,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "waveformHeight"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(animatedHeight.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultDisplay(
    uiState: IqraUiState, 
    onCardClick: (Int, Int) -> Unit,
    onPlayClick: (Int, Int) -> Unit,
    onStopClick: () -> Unit,
    onFetchTafsir: (Int, Int) -> Unit,
    onTafsirSourceSelect: (TafsirSource) -> Unit
) {
    val context = LocalContext.current
    if (uiState.matchedSurahName != null) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clickable { 
                    onCardClick(uiState.matchedSurahId!!, uiState.matchedAyahNumber!!)
                }
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            ComposeColor.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                ),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (uiState.isOnline) {
                        IconButton(onClick = { 
                            if (uiState.isPlaying) onStopClick() 
                            else onPlayClick(uiState.matchedSurahId!!, uiState.matchedAyahNumber!!)
                        }) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "Stop" else "Play"
                            )
                        }
                    }
                    IconButton(onClick = { 
                        shareAyahAsImage(context, Ayah(
                            uiState.matchedSurahId!!, 
                            uiState.matchedAyahNumber!!, 
                            uiState.arabicText!!, 
                            "", 
                            uiState.matchedSurahName!!, 
                            uiState.matchedSurahNameEn!!, 
                            uiState.translationEn!!, 
                            uiState.translationBn!!
                        )) 
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
                Text(
                    text = uiState.matchedSurahNameEn ?: "",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${uiState.matchedSurahName} • Ayah ${uiState.matchedAyahNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (uiState.verseCorrection.isNotEmpty()) {
                            uiState.verseCorrection.forEach { correction ->
                                Text(
                                    text = correction.word + " ",
                                    style = TextStyle(
                                        fontFamily = QuranFont,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 1.6.em,
                                        color = when(correction.state) {
                                            WordState.CORRECT -> MaterialTheme.colorScheme.onSurface
                                            WordState.WRONG -> ComposeColor.Red
                                            WordState.MISSING -> ComposeColor.Red.copy(alpha = 0.5f)
                                        },
                                        textDecoration = if (correction.state != WordState.CORRECT) 
                                            TextDecoration.Underline else TextDecoration.None
                                    )
                                )
                            }
                        } else {
                            Text(
                                text = uiState.arabicText ?: "",
                                style = TextStyle(
                                    fontFamily = QuranFont,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 1.6.em,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 24.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                Text(
                    text = uiState.translationEn ?: "",
                    style = TextStyle(
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = uiState.translationBn ?: "",
                    style = TextStyle(
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Tafsir Section
                if (uiState.isOnline) {
                    TafsirSection(
                        uiState = uiState,
                        onFetchTafsir = { onFetchTafsir(uiState.matchedSurahId!!, uiState.matchedAyahNumber!!) },
                        onSourceSelect = onTafsirSourceSelect
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Tap to view full Surah",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (uiState.isModelLoaded) "Recite a verse to begin" else "Initializing AI Engine...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                if (uiState.isModelLoaded) {
                    Text(
                        text = "একটি আয়াত তিলাওয়াত শুরু করুন",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TafsirSection(
    uiState: IqraUiState,
    onFetchTafsir: () -> Unit,
    onSourceSelect: (TafsirSource) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { 
                    expanded = !expanded
                    if (expanded && uiState.currentTafsir == null) onFetchTafsir()
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Tafsir (${uiState.selectedTafsirSource.displayName})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Change Source", tint = MaterialTheme.colorScheme.primary)
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            TafsirSource.entries.groupBy { it.language }.forEach { (lang, sources) ->
                Text(
                    text = lang,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                sources.forEach { source ->
                    DropdownMenuItem(
                        text = { Text(source.displayName) },
                        onClick = {
                            onSourceSelect(source)
                            menuExpanded = false
                            if (!expanded) expanded = true
                        }
                    )
                }
                HorizontalDivider()
            }
        }

        AnimatedVisibility(visible = expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                if (uiState.isTafsirLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = uiState.currentTafsir ?: "Tap to load Tafsir",
                        style = TextStyle(
                            fontSize = 16.sp,
                            lineHeight = 1.5.em,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun NoMatchDisplay(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Recitation not recognized",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Please try again in a quieter place.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Retry")
        }
    }
}

@Composable
fun ListeningLabel(isRecording: Boolean) {
    AnimatedVisibility(
        visible = isRecording,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "listening")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
        Text(
            text = "Listening...",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.graphicsLayer(alpha = alpha)
        )
    }
}

@Composable
fun ErrorMessage(error: String?) {
    AnimatedVisibility(
        visible = error != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Text(
            text = error ?: "",
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        )
    }
}

@Composable
fun RecordingButtonPremium(
    enabled: Boolean,
    isRecording: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = pulseAlpha
                    }
                    .background(ComposeColor.Red.copy(alpha = 0.5f), CircleShape)
                    .blur(20.dp)
            )
        }

        val backgroundColor by animateColorAsState(
            targetValue = when {
                !enabled -> ComposeColor.DarkGray
                isRecording -> ComposeColor.Red
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            },
            label = "color"
        )

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .then(if (!isRecording) Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape) else Modifier)
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = null,
                tint = if (isRecording) ComposeColor.White else if (enabled) MaterialTheme.colorScheme.primary else ComposeColor.Gray,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
