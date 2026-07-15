@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.LabelEntity
import com.example.ui.DashboardStats
import com.example.ui.LogisticsViewModel
import com.example.ui.UserRole
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AppNavigation(viewModel: LogisticsViewModel) {
    var activeTab by remember { mutableStateOf("dashboard") }
    val conferenceLabel by viewModel.conferenceLabel.collectAsState()

    if (conferenceLabel != null) {
        ConferenceScreen(
            label = conferenceLabel!!,
            viewModel = viewModel,
            onClose = { viewModel.setConferenceLabel(null) }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    val items = listOf(
                        Triple("dashboard", "Dashboard", Icons.Default.Dashboard),
                        Triple("capture", "Nova Leitura", Icons.Default.QrCodeScanner),
                        Triple("history", "Histórico", Icons.Default.ReceiptLong),
                        Triple("search", "Pesquisar", Icons.Default.Search),
                        Triple("settings", "Ajustes", Icons.Default.Settings)
                    )

                    items.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            selected = activeTab == route,
                            onClick = { activeTab = route },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (activeTab) {
                    "dashboard" -> DashboardScreen(viewModel, onNavigateToCapture = { activeTab = "capture" })
                    "capture" -> CaptureScreen(viewModel)
                    "history" -> HistoryScreen(viewModel)
                    "search" -> SearchScreen(viewModel)
                    "settings" -> SettingsScreen(viewModel)
                }
            }
        }
    }
}

// ==========================================
// 1. DASHBOARD SCREEN
// ==========================================
@Composable
fun DashboardScreen(viewModel: LogisticsViewModel, onNavigateToCapture: () -> Unit) {
    val stats by viewModel.dashboardStats.collectAsState()
    val labels by viewModel.labels.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Hero Header Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                // Try to load the beautiful custom generated hero image
                Image(
                    painter = painterResource(id = R.drawable.img_logistics_hero),
                    contentDescription = "Prudêncio Logistics Hub",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center
                )
                // Ambient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                )
                // Text Overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "PRUDÊNCIO OCR LOGISTICS",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Inteligência Artificial & OCR Corporativo em Tempo Real",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Summary Indicators (Row Grid)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsCard(
                    title = "Leituras Hoje",
                    value = stats.readingsToday.toString(),
                    icon = Icons.Default.Today,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    title = "Total Salvo",
                    value = stats.totalLabels.toString(),
                    icon = Icons.Default.AllInbox,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsCard(
                    title = "Esta Semana",
                    value = stats.readingsWeek.toString(),
                    icon = Icons.Default.DateRange,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    title = "Tempo Médio",
                    value = String.format("%.1fs", stats.avgReadTimeMs / 1000.0),
                    icon = Icons.Default.Timer,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Analytical Carrier Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📊 Distribuição Operacional",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    AnalyticRow(label = "Transportadora Principal:", value = stats.topCarrier, icon = Icons.Default.LocalShipping)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    AnalyticRow(label = "Cidade de Maior Volume:", value = stats.topCity, icon = Icons.Default.LocationCity)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    AnalyticRow(label = "Produto mais Recebido:", value = stats.topProduct, icon = Icons.Default.Inventory2)
                }
            }
        }

        // Daily Trend Chart
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📈 Tendência Diária de Leituras",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (stats.dailyTrend.isEmpty() || stats.dailyTrend.all { it.second == 0 }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Nenhum dado de histórico recente para exibir no gráfico.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LogisticsBarChart(
                            data = stats.dailyTrend,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        )
                    }
                }
            }
        }

        // Premium Module Quick link
        item {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToCapture() },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Premium Mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Módulo Premium Ativo 🚀",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Leitura contínua industrial, OCR multi-etiqueta e inteligência de layout ativos.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Go",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun StatsCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun AnalyticRow(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun LogisticsBarChart(data: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val maxVal = data.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { (label, count) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.7f)
                        .width(18.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (count > 0) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

// ==========================================
// 2. NOVA LEITURA (CAPTURE & OCR) SCREEN
// ==========================================
@Composable
fun CaptureScreen(viewModel: LogisticsViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isProcessing by viewModel.isProcessing.collectAsState()
    val progress by viewModel.processingProgress.collectAsState()
    val isContinuous by viewModel.continuousScanMode.collectAsState()
    val ocrEngine by viewModel.ocrEngine.collectAsState()

    val imageCapture = remember { ImageCapture.Builder().build() }

    var hasCameraPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Gallery Selector Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    viewModel.processLabelCapture(context, bitmap, null) {}
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao carregar imagem: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Interactive Demo Simulation Options for testing directly on Android emulator
    var showDemoLabelsDropdown by remember { mutableStateOf(false) }
    val demoLabels = listOf(
        "MERCADO LIVRE - Carlos Henrique, Av Civil 1770, Serra ES, CEP 29168-322, Pedido ML-4421, SKU-112, Peso 1.5kg",
        "SHOPEE EXPRESS - Maria Oliveira, Rua das Flôres 42 Bl A Ap 102, Vitória ES, CEP 29010-000, Pedido SHP-9004, SKU-554",
        "CORREIOS SEDEX - Distribuidora Prudêncio, Av Central 400 Condomínio Sierra, Cariacica ES, CEP 29140-100",
        "AMAZON LOGISTICS - André Santos, Lote 12 Quadra 3, Vila Velha ES, CEP 29101-000, Produto: Smart Hub",
        "FEDEX EXPRESS - Roberto Lima, Rodovia BR-101 Km 12, Linhares ES, CEP 29900-000, Peso 4.2kg"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "📷 Captura Inteligente",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Custom Scanner Lens Frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                .background(Color.Black)
        ) {
            if (hasCameraPermission && !isContinuous) {
                // CameraX Real View Integration
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                            } catch (exc: Exception) {
                                Log.e("CaptureScreen", "Camera binding failed", exc)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Beautiful Scanner Lens Graphic / Onboarding Visual
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isContinuous) Icons.Default.Cached else Icons.Default.CameraAlt,
                        contentDescription = "Lens Placeholder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isContinuous) "MODO INDUSTRIAL ATIVO 📦\nSinalizando e salvando automaticamente..." 
                               else "CameraX Pronta para Captura",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // High Tech Neon Target Alignment Guidelines
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .fillMaxHeight(0.5f)
                    .align(Alignment.Center)
                    .border(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            ) {
                // Red Laser Animation Line
                val infiniteTransition = rememberInfiniteTransition()
                val lineOffset by infiniteTransition.animateFloat(
                    initialValue = 0.1f,
                    targetValue = 0.9f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.015f)
                        .align(Alignment.TopCenter)
                        .offset(y = 200.dp * lineOffset) // dynamic scanning effect
                        .background(Color.Red.copy(alpha = 0.8f))
                )
            }

            // Mode Indicator Pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isContinuous) Color.Green else MaterialTheme.colorScheme.secondary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Motor: $ocrEngine",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            // High-tech AI Processing Overlay (Spinner + Skeleton)
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .clickable(enabled = true, onClick = {}), // prevent touch events
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        // Ambient Pulsating Scanner / Spinner Effect
                        val transition = rememberInfiniteTransition(label = "overlayShimmer")
                        val pulseAlpha by transition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseAlpha"
                        )

                        CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "IA Interpretando Etiqueta...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "O Gemini está extraindo dados estruturados de CEP, destinatário, volumes e transporte via OCR inteligente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Skeleton Screen Mockup with Shimmer Animation
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(0.95f)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "CONSTRUINDO MAPEAMENTO LOGÍSTICO...",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(14.dp)
                                            .weight(1f)
                                            .background(Color.White.copy(alpha = 0.12f * pulseAlpha), RoundedCornerShape(4.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .height(14.dp)
                                            .weight(2f)
                                            .background(Color.White.copy(alpha = 0.12f * pulseAlpha), RoundedCornerShape(4.dp))
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .height(14.dp)
                                        .fillMaxWidth(0.85f)
                                        .background(Color.White.copy(alpha = 0.12f * pulseAlpha), RoundedCornerShape(4.dp))
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(14.dp)
                                            .weight(1.5f)
                                            .background(Color.White.copy(alpha = 0.12f * pulseAlpha), RoundedCornerShape(4.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .height(14.dp)
                                            .weight(1f)
                                            .background(Color.White.copy(alpha = 0.12f * pulseAlpha), RoundedCornerShape(4.dp))
                                )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.White.copy(alpha = 0.1f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Industrial Mode stability and configuration panel
        if (isContinuous) {
            val isStable by viewModel.isDeviceStable.collectAsState()
            val stability by viewModel.stabilityProgress.collectAsState()
            val isSensorTrigger by viewModel.accelerometerActive.collectAsState()

            // Handle accelerometer lifecycle when active
            LaunchedEffect(isSensorTrigger) {
                if (isSensorTrigger) {
                    viewModel.registerAccelerometer(context)
                } else {
                    viewModel.unregisterAccelerometer()
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    viewModel.unregisterAccelerometer()
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isStable) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚙️ MODO INDUSTRIAL",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isStable) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isStable) "ESTÁVEL" else "MOVENDO",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isStable) Color(0xFF2E7D32) else Color(0xFFE65100)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Sensor Lock",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = isSensorTrigger,
                                onCheckedChange = { viewModel.toggleAccelerometerActive(context, it) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isSensorTrigger) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Estabilização do Sensor de Mira",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "${(stability * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isStable) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { stability },
                                color = if (isStable) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isStable) "🎯 Alvo travado! Captura automática autorizada." else "📸 Mantenha o aparelho imóvel para iniciar leitura...",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isStable) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "⏱️ Loop automático por tempo: Processando leituras cíclicas de etiquetas simuladas a cada 4 segundos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Active progressive AI interpretation feedback
        if (isProcessing) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "OCR + IA Interpretando Etiqueta...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Operational Command Center Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (isContinuous) {
                        viewModel.toggleContinuousScan(context, false)
                    } else if (hasCameraPermission) {
                        try {
                            val photoFile = java.io.File(context.cacheDir, "ocr_label_capture.jpg")
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                            
                            imageCapture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        try {
                                            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                            if (bitmap != null) {
                                                viewModel.processLabelCapture(context, bitmap, null) {}
                                            } else {
                                                Toast.makeText(context, "Erro ao processar imagem capturada", Toast.LENGTH_SHORT).show()
                                                viewModel.processLabelCapture(context, null, null) {}
                                            }
                                        } catch (e: Exception) {
                                            Log.e("CaptureScreen", "Error decoding capture: ${e.message}", e)
                                            viewModel.processLabelCapture(context, null, null) {}
                                        }
                                    }

                                    override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                                        Log.e("CaptureScreen", "Photo capture failed: ${exception.message}", exception)
                                        Toast.makeText(context, "Sem câmera física ativa. Ativando simulador de IA.", Toast.LENGTH_SHORT).show()
                                        viewModel.processLabelCapture(context, null, null) {}
                                    }
                                }
                            )
                        } catch (e: Exception) {
                            Log.e("CaptureScreen", "Failed to take picture: ${e.message}", e)
                            Toast.makeText(context, "Falha na câmera: ${e.message}. Usando simulador.", Toast.LENGTH_SHORT).show()
                            viewModel.processLabelCapture(context, null, null) {}
                        }
                    } else {
                        Toast.makeText(context, "Permissão de câmera necessária", Toast.LENGTH_SHORT).show()
                        viewModel.processLabelCapture(context, null, null) {}
                    }
                },
                modifier = Modifier
                    .weight(1.5f)
                    .testTag("action_capture_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isContinuous) Color.Red else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isContinuous) Icons.Default.Stop else Icons.Default.Camera,
                    contentDescription = "Capture"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isContinuous) "Parar Contínuo" else "Capturar Agora")
            }

            // continuous scan toggle
            IconButton(
                onClick = { viewModel.toggleContinuousScan(context, !isContinuous) },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isContinuous) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cached,
                    contentDescription = "Industrial Mode",
                    tint = if (isContinuous) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Image import button
            IconButton(
                onClick = { galleryLauncher.launch("image/*") },
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Importar Imagem")
            }
        }

        // Demo Labels Simulator Options (Extremely useful for demo/emulator runs!)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { showDemoLabelsDropdown = !showDemoLabelsDropdown },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simulador de Etiquetas (Demo)")
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }

            DropdownMenu(
                expanded = showDemoLabelsDropdown,
                onDismissRequest = { showDemoLabelsDropdown = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                demoLabels.forEach { labelStr ->
                    DropdownMenuItem(
                        text = { Text(labelStr, maxLines = 1, style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            showDemoLabelsDropdown = false
                            viewModel.processLabelCapture(context, null, labelStr) {}
                        }
                    )
                }
            }
        }
    }
}

// ==========================================
// 3. TELA DE CONFERÊNCIA & EDIÇÃO (EDIT SCREEN)
// ==========================================
@Composable
fun ConferenceScreen(
    label: LabelEntity,
    viewModel: LogisticsViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var isSaving by remember { mutableStateOf(false) }

    // Mutable states for editing
    var nome by remember { mutableStateOf(label.nome) }
    var empresa by remember { mutableStateOf(label.empresa) }
    var transportadora by remember { mutableStateOf(label.transportadora) }
    var rua by remember { mutableStateOf(label.rua) }
    var numero by remember { mutableStateOf(label.numero) }
    var complemento by remember { mutableStateOf(label.complemento) }
    var condominio by remember { mutableStateOf(label.condominio) }
    var bloco by remember { mutableStateOf(label.bloco) }
    var apartamento by remember { mutableStateOf(label.apartamento) }
    var bairro by remember { mutableStateOf(label.bairro) }
    var cidade by remember { mutableStateOf(label.cidade) }
    var estado by remember { mutableStateOf(label.estado) }
    var cep by remember { mutableStateOf(label.cep) }
    var telefone by remember { mutableStateOf(label.telefone) }
    var pedido by remember { mutableStateOf(label.pedido) }
    var sku by remember { mutableStateOf(label.sku) }
    var produto by remember { mutableStateOf(label.produto) }
    var codigoBarras by remember { mutableStateOf(label.codigoBarras) }
    var qrCode by remember { mutableStateOf(label.qrCode) }
    var peso by remember { mutableStateOf(label.peso) }
    var volume by remember { mutableStateOf(label.volume) }
    var observacoes by remember { mutableStateOf(label.observacoes) }

    val confidenceColor = if (label.confidence < 0.85f) Color(0xFFFFD166) else Color.Transparent

    Scaffold(
        topBar = {
            OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Conferência de Etiqueta", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Low confidence notice block
            if (label.confidence < 0.85f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Aviso", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Aviso: Alguns campos possuem baixa confiança. Verifique os destaques em amarelo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Editable Input fields
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Section 1: Recipient
                item {
                    Text("📦 Dados do Destinatário", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                item {
                    OutlinedTextField(
                        value = nome,
                        onValueChange = { nome = it },
                        label = { Text("Nome Completo") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = empresa,
                            onValueChange = { empresa = it },
                            label = { Text("Empresa") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = telefone,
                            onValueChange = { telefone = it },
                            label = { Text("Telefone") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Section 2: Endereço Inteligente (Smart Address Split)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🗺️ Endereço Inteligente", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = {
                                val addressStr = "$rua, $numero - $bairro, $cidade - $estado, CEP $cep"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(addressStr)}"))
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ver no Maps")
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = rua,
                        onValueChange = { rua = it },
                        label = { Text("Rua/Avenida") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (label.confidence < 0.85f) TextFieldDefaults.colors(focusedContainerColor = confidenceColor, unfocusedContainerColor = confidenceColor) else TextFieldDefaults.colors()
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = numero,
                            onValueChange = { numero = it },
                            label = { Text("Número") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = complemento,
                            onValueChange = { complemento = it },
                            label = { Text("Complemento") },
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = condominio,
                            onValueChange = { condominio = it },
                            label = { Text("Condomínio") },
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = bloco,
                            onValueChange = { bloco = it },
                            label = { Text("Bloco") },
                            modifier = Modifier.weight(0.8f)
                        )
                        OutlinedTextField(
                            value = apartamento,
                            onValueChange = { apartamento = it },
                            label = { Text("Apartamento") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bairro,
                            onValueChange = { bairro = it },
                            label = { Text("Bairro") },
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = cep,
                            onValueChange = { cep = it },
                            label = { Text("CEP") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cidade,
                            onValueChange = { cidade = it },
                            label = { Text("Cidade") },
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = estado,
                            onValueChange = { estado = it },
                            label = { Text("Estado") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Section 3: Logistic Data
                item {
                    Text("🚚 Informações de Transporte", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = transportadora,
                            onValueChange = { transportadora = it },
                            label = { Text("Transportadora") },
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = pedido,
                            onValueChange = { pedido = it },
                            label = { Text("Pedido") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sku,
                            onValueChange = { sku = it },
                            label = { Text("SKU") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = produto,
                            onValueChange = { produto = it },
                            label = { Text("Produto") },
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = peso,
                            onValueChange = { peso = it },
                            label = { Text("Peso") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = volume,
                            onValueChange = { volume = it },
                            label = { Text("Volume") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = codigoBarras,
                        onValueChange = { codigoBarras = it },
                        label = { Text("Código de Barras") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = observacoes,
                        onValueChange = { observacoes = it },
                        label = { Text("Observações") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            }

            // Conferência Button Action Panel
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            isSaving = true
                            viewModel.saveLabel(
                                context,
                                label.copy(
                                    nome = nome,
                                    empresa = empresa,
                                    transportadora = transportadora,
                                    rua = rua,
                                    numero = numero,
                                    complemento = complemento,
                                    condominio = condominio,
                                    bloco = bloco,
                                    apartamento = apartamento,
                                    bairro = bairro,
                                    cidade = cidade,
                                    estado = estado,
                                    cep = cep,
                                    telefone = telefone,
                                    pedido = pedido,
                                    sku = sku,
                                    produto = produto,
                                    codigoBarras = codigoBarras,
                                    qrCode = qrCode,
                                    peso = peso,
                                    volume = volume,
                                    observacoes = observacoes,
                                    status = "Conferido"
                                )
                            )
                            Toast.makeText(context, "Registro salvo com sucesso!", Toast.LENGTH_SHORT).show()
                            onClose()
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("submit_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Salvar")
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. HISTÓRICO DE ETIQUETAS SCREEN
// ==========================================
@Composable
fun HistoryScreen(viewModel: LogisticsViewModel) {
    val labels by viewModel.labels.collectAsState()
    val activeRole by viewModel.currentRole.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📋 Histórico Logístico",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            // Export quicksheet trigger
            Row {
                IconButton(onClick = { viewModel.shareCsv(context) }) {
                    Icon(Icons.Default.Share, contentDescription = "CSV", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { viewModel.sharePdf(context) }) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = Color.Red)
                }
            }
        }

        if (labels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.ReceiptLong,
                        contentDescription = "No data",
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Nenhuma etiqueta salva no banco ainda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(labels, key = { it.id }) { label ->
                    HistoryLabelCard(
                        label = label,
                        canDelete = activeRole.level >= UserRole.ADMINISTRATOR.level,
                        onEditClick = { viewModel.setConferenceLabel(label) },
                        onDeleteClick = { viewModel.deleteLabel(context, label) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryLabelCard(
    label: LabelEntity,
    canDelete: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = label.nome,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${label.cidade} - ${label.estado} | CEP ${label.cep}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label.transportadora,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pedido: ${label.pedido} | SKU: ${label.sku}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Lido em: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(label.dataCadastro))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.secondary)
                    }
                    if (canDelete) {
                        IconButton(onClick = onDeleteClick) {
                            Icon(Icons.Default.Delete, contentDescription = "Deletar", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. PESQUISA MULTICRITÉRIO SCREEN
// ==========================================
@Composable
fun SearchScreen(viewModel: LogisticsViewModel) {
    val context = LocalContext.current
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val labels by viewModel.labels.collectAsState()
    val activeRole by viewModel.currentRole.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🔍 Pesquisa Multicritério",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Search Bar with tags
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.searchQuery.value = it },
            label = { Text("Pesquise por Nome, Rua, CEP, SKU, Pedido...") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            }
        )

        // Informational criteria labels
        Text(
            text = "Campos indexados: Nome, Rua, CEP, Pedido, SKU, Barras, QR Code, Cidade, Condomínio, Bloco, Apto.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        if (query.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Digite para realizar uma busca em tempo real no banco SQLite.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nenhum resultado encontrado para \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { it.id }) { label ->
                    HistoryLabelCard(
                        label = label,
                        canDelete = activeRole.level >= UserRole.ADMINISTRATOR.level,
                        onEditClick = { viewModel.setConferenceLabel(label) },
                        onDeleteClick = { viewModel.deleteLabel(context, label) }
                    )
                }
            }
        }
    }
}

// ==========================================
// 6. CONFIGURAÇÕES & SEGURANÇA (SETTINGS) SCREEN
// ==========================================
@Composable
fun SettingsScreen(viewModel: LogisticsViewModel) {
    val context = LocalContext.current
    val currentRole by viewModel.currentRole.collectAsState()
    val ocrEngine by viewModel.ocrEngine.collectAsState()
    val isAutoBackup by viewModel.autoBackupEnabled.collectAsState()
    val isEncryption by viewModel.encryptionEnabled.collectAsState()
    val synonyms by viewModel.carrierSynonyms.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()

    var newSynonymAbbr by remember { mutableStateOf("") }
    var newSynonymFull by remember { mutableStateOf("") }

    var emailField by remember { mutableStateOf("") }
    var passwordField by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.checkCurrentUserState(context)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "⚙️ Configurações & Segurança",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Section 1: Security Profile Selector (LGPD / User Roles)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🔒 Segurança e Controle de Acesso (LGPD)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Perfil ativo atual:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UserRole.values().forEach { role ->
                            FilterChip(
                                selected = currentRole == role,
                                onClick = { viewModel.setRole(role) },
                                label = { Text(role.displayName, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when (currentRole) {
                            UserRole.OPERATOR -> "• Operador: Permissão apenas para ler e salvar etiquetas. Exclusão e relatórios analíticos bloqueados."
                            UserRole.SUPERVISOR -> "• Supervisor: Permissão para ler, salvar, analisar dashboard e exportar relatórios. Exclusão bloqueada."
                            UserRole.ADMINISTRATOR -> "• Administrador: Acesso completo, incluindo exclusão de registros e gerenciamento de banco de dados."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Section 2: OCR Settings
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🤖 Mecanismo de OCR Ativo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val engines = listOf("Google ML Kit", "Google Vision", "Tesseract", "Azure OCR")
                    engines.forEach { engine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.ocrEngine.value = engine }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = ocrEngine == engine,
                                onClick = { viewModel.ocrEngine.value = engine }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(engine, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Section 3: AI Learn Dictionary of carrier patterns (corrections)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🧠 Aprendizado Inteligente (Sinônimos)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Ensine padrões ao OCR. Ex: abreviações corrigidas automaticamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // List existing synonyms
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        synonyms.forEach { (abbr, full) ->
                            AssistChip(
                                onClick = {},
                                label = { Text("$abbr → $full") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Adicionar Nova Regra:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newSynonymAbbr,
                            onValueChange = { newSynonymAbbr = it },
                            placeholder = { Text("Abrav.") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newSynonymFull,
                            onValueChange = { newSynonymFull = it },
                            placeholder = { Text("Trad."); },
                            modifier = Modifier.weight(1.5f),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                if (newSynonymAbbr.isNotEmpty() && newSynonymFull.isNotEmpty()) {
                                    viewModel.addSynonym(newSynonymAbbr, newSynonymFull)
                                    newSynonymAbbr = ""
                                    newSynonymFull = ""
                                    Toast.makeText(context, "Regra adicionada!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Section 4: Firebase Authentication Card
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🔐 Autenticação Firebase Cloud",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Conecte sua conta de operador/supervisor para sincronizar leituras em nuvem de forma segura.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (userEmail != null) {
                        // User is signed in
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF4CAF50), shape = androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Conectado como:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    userEmail ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.logoutFirebase(context) {
                                    Toast.makeText(context, "Sessão encerrada", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sair da Conta")
                        }
                    } else {
                        // User is signed out
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = emailField,
                                onValueChange = { emailField = it },
                                label = { Text("E-mail do Operador") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                                )
                            )
                            OutlinedTextField(
                                value = passwordField,
                                onValueChange = { passwordField = it },
                                label = { Text("Senha") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                                )
                            )

                            if (authError != null) {
                                Text(
                                    text = authError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            if (isAuthLoading) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (emailField.isEmpty() || passwordField.isEmpty()) {
                                                authError = "Preencha todos os campos."
                                                return@Button
                                            }
                                            isAuthLoading = true
                                            authError = null
                                            viewModel.loginFirebase(
                                                context = context,
                                                email = emailField,
                                                password = passwordField,
                                                onSuccess = {
                                                    isAuthLoading = false
                                                    Toast.makeText(context, "Logado com sucesso!", Toast.LENGTH_SHORT).show()
                                                },
                                                onFailure = { error ->
                                                    isAuthLoading = false
                                                    authError = error
                                                }
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Entrar")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            if (emailField.isEmpty() || passwordField.isEmpty()) {
                                                authError = "Preencha todos os campos."
                                                return@OutlinedButton
                                            }
                                            isAuthLoading = true
                                            authError = null
                                            viewModel.signUpFirebase(
                                                context = context,
                                                email = emailField,
                                                password = passwordField,
                                                onSuccess = {
                                                    isAuthLoading = false
                                                    Toast.makeText(context, "Conta criada com sucesso!", Toast.LENGTH_SHORT).show()
                                                },
                                                onFailure = { error ->
                                                    isAuthLoading = false
                                                    authError = error
                                                }
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cadastrar")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 5: Backup and Cryptography Switchers (Sincronização e Resiliência)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🛡️ Sincronização & Segurança (SQLite + Firestore)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Criptografia Local do SQLite", style = MaterialTheme.typography.bodyMedium)
                            Text("Proteção LGPD contra vazamentos físicos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isEncryption,
                            onCheckedChange = { viewModel.encryptionEnabled.value = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sincronização Nuvem Direta", style = MaterialTheme.typography.bodyMedium)
                            Text("Salva automaticamente no Firestore", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isAutoBackup,
                            onCheckedChange = { viewModel.autoBackupEnabled.value = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.triggerCloudSync(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Forçar Sincronização Geral Cloud")
                    }
                }
            }
        }
    }
}
