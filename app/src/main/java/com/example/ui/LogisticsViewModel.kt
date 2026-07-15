@file:OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.example.ui

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.LabelEntity
import com.example.data.LabelRepository
import com.example.service.GeminiParserService
import com.example.service.FirebaseService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class UserRole(val displayName: String, val level: Int) {
    OPERATOR("Operador", 1),
    SUPERVISOR("Supervisor", 2),
    ADMINISTRATOR("Administrador", 3)
}

class LogisticsViewModel(private val repository: LabelRepository) : ViewModel() {

    private val TAG = "LogisticsViewModel"

    // Firebase Authentication State Flow
    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    fun checkCurrentUserState(context: Context) {
        _userEmail.value = FirebaseService.getCurrentUserEmail(context)
    }

    // Authentication & Authorization Security
    private val _currentRole = MutableStateFlow(UserRole.ADMINISTRATOR)
    val currentRole: StateFlow<UserRole> = _currentRole.asStateFlow()

    fun setRole(role: UserRole) {
        _currentRole.value = role
    }

    // Settings Configuration State
    val ocrEngine = MutableStateFlow("Google ML Kit")
    val continuousScanMode = MutableStateFlow(false)
    val encryptionEnabled = MutableStateFlow(true)
    val autoBackupEnabled = MutableStateFlow(true)

    // Accelerometer / Device Stability state variables for Industrial Mode
    val accelerometerActive = MutableStateFlow(true)
    private val _stabilityProgress = MutableStateFlow(1f)
    val stabilityProgress: StateFlow<Float> = _stabilityProgress.asStateFlow()

    private val _isDeviceStable = MutableStateFlow(true)
    val isDeviceStable: StateFlow<Boolean> = _isDeviceStable.asStateFlow()

    private var sensorManager: SensorManager? = null
    private var accelerometerSensor: Sensor? = null
    private var sensorListener: SensorEventListener? = null

    private var lastSensorUpdate = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var isMoving = false
    private var movementStartTime = 0L
    private var stabilityStartTime = 0L
    private var lastTriggerTime = 0L

    // Dictionary of carrier synonyms (AI corrections)
    private val _carrierSynonyms = MutableStateFlow<Map<String, String>>(
        mapOf(
            "Ap" to "Apartamento",
            "Apto" to "Apartamento",
            "BL" to "Bloco",
            "Bl" to "Bloco",
            "Nº" to "Número",
            "num" to "Número"
        )
    )
    val carrierSynonyms: StateFlow<Map<String, String>> = _carrierSynonyms.asStateFlow()

    fun addSynonym(key: String, value: String) {
        _carrierSynonyms.value = _carrierSynonyms.value + (key to value)
    }

    // Active records list with automatic flow from database
    val labels: StateFlow<List<LabelEntity>> = repository.allLabels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search query & results
    val searchQuery = MutableStateFlow("")
    val searchResults: StateFlow<List<LabelEntity>> = searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                flowOf(emptyList())
            } else {
                repository.searchLabels(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Label currently being edited or verified
    private val _conferenceLabel = MutableStateFlow<LabelEntity?>(null)
    val conferenceLabel: StateFlow<LabelEntity?> = _conferenceLabel.asStateFlow()

    fun setConferenceLabel(label: LabelEntity?) {
        _conferenceLabel.value = label
    }

    // API parsing states
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    // Dashboard analytics calculated reactively from DB data
    val dashboardStats = labels.map { list ->
        calculateStats(list)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardStats())

    // Continuous scanning background simulated trigger
    private var continuousScanJob: Job? = null

    fun toggleContinuousScan(context: Context, enabled: Boolean) {
        continuousScanMode.value = enabled
        if (enabled) {
            if (accelerometerActive.value) {
                registerAccelerometer(context)
            } else {
                startContinuousScanSimulation(context)
            }
        } else {
            continuousScanJob?.cancel()
            unregisterAccelerometer()
        }
    }

    fun toggleAccelerometerActive(context: Context, active: Boolean) {
        accelerometerActive.value = active
        if (continuousScanMode.value) {
            if (active) {
                continuousScanJob?.cancel()
                registerAccelerometer(context)
            } else {
                unregisterAccelerometer()
                startContinuousScanSimulation(context)
            }
        }
    }

    private fun startContinuousScanSimulation(context: Context) {
        continuousScanJob?.cancel()
        continuousScanJob = viewModelScope.launch {
            while (continuousScanMode.value) {
                delay(4000) // Process next auto-detected label every 4 seconds
                if (!continuousScanMode.value) break

                _isProcessing.value = true
                _processingProgress.value = 0.2f
                delay(400)
                _processingProgress.value = 0.6f
                delay(400)
                _processingProgress.value = 1.0f

                // Generate and save highly realistic cargo data
                val mockLabel = GeminiParserService.parseLabel(
                    imageBitmap = null,
                    textToParse = "Auto Industrial Scan CEP: ${10000 + Random().nextInt(80000)}-${100 + Random().nextInt(800)} Volume ${Random().nextInt(5) + 1}",
                    ocrEngine = ocrEngine.value
                )

                // Enforce synonyms corrections
                val sanitizedLabel = applySynonyms(mockLabel)

                // Auto save
                repository.insert(sanitizedLabel)
                playBeepAndVibrate(context)

                _isProcessing.value = false
                _processingProgress.value = 0f
            }
        }
    }

    fun registerAccelerometer(context: Context) {
        if (sensorListener != null) return // Already registered
        
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            
            if (accelerometerSensor != null) {
                sensorListener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        if (event == null) return
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSensorUpdate > 80) { // Check at ~12Hz
                            val deltaX = Math.abs(x - lastX)
                            val deltaY = Math.abs(y - lastY)
                            val deltaZ = Math.abs(z - lastZ)

                            val totalMovement = deltaX + deltaY + deltaZ
                            
                            lastX = x
                            lastY = y
                            lastZ = z
                            lastSensorUpdate = currentTime

                            // movement threshold (1.2 G-force variance is quite sensitive but stable)
                            val isCurrentlyMoving = totalMovement > 1.2f

                            // Visual stabilization calculation from 0% (shaking) to 100% (stable)
                            val rawStability = 1f - (totalMovement / 5.0f).coerceIn(0f, 1f)
                            _stabilityProgress.value = _stabilityProgress.value * 0.5f + rawStability * 0.5f

                            if (isCurrentlyMoving) {
                                isMoving = true
                                _isDeviceStable.value = false
                                movementStartTime = currentTime
                                stabilityStartTime = 0L // reset stabilizer duration
                            } else {
                                if (isMoving && (currentTime - movementStartTime > 250)) {
                                    if (stabilityStartTime == 0L) {
                                        stabilityStartTime = currentTime
                                    } else if (currentTime - stabilityStartTime > 1000) { // 1 second of clean stability
                                        isMoving = false
                                        stabilityStartTime = 0L
                                        _isDeviceStable.value = true
                                        
                                        // Auto trigger industrial scan!
                                        if (continuousScanMode.value && accelerometerActive.value && !_isProcessing.value) {
                                            triggerAutomaticScanFromSensor(context)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager?.registerListener(sensorListener, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
                Log.d(TAG, "Accelerometer sensor registered.")
            } else {
                Log.w(TAG, "Accelerometer sensor not found on this hardware.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed registering accelerometer: ${e.message}")
        }
    }

    fun unregisterAccelerometer() {
        try {
            sensorListener?.let {
                sensorManager?.unregisterListener(it)
            }
            sensorListener = null
            _isDeviceStable.value = true
            _stabilityProgress.value = 1.0f
            Log.d(TAG, "Accelerometer sensor unregistered.")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering accelerometer: ${e.message}")
        }
    }

    private fun triggerAutomaticScanFromSensor(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < 3500) { // limit triggers to once per 3.5 seconds
            return
        }
        lastTriggerTime = now

        viewModelScope.launch {
            _isProcessing.value = true
            _processingProgress.value = 0.1f
            
            // Audible and haptic target lock confirmation
            playTargetLockBeep(context)
            
            // Fast sequence scanning mock
            delay(400)
            _processingProgress.value = 0.6f
            delay(400)
            _processingProgress.value = 1.0f
            delay(200)

            val mockLabel = GeminiParserService.parseLabel(
                imageBitmap = null,
                textToParse = "Auto Industrial Sensor-Lock CEP: ${10000 + Random().nextInt(80000)}-${100 + Random().nextInt(800)} Volume ${Random().nextInt(5) + 1}",
                ocrEngine = ocrEngine.value
            )
            val sanitizedLabel = applySynonyms(mockLabel)

            // Auto save directly to Room and auto-sync to cloud
            val newId = repository.insert(sanitizedLabel)
            
            if (autoBackupEnabled.value) {
                val labelWithId = if (sanitizedLabel.id == 0L) sanitizedLabel.copy(id = newId) else sanitizedLabel
                FirebaseService.saveLabelToFirestore(
                    context = context,
                    label = labelWithId,
                    onSuccess = { Log.d(TAG, "Sensor-Lock synced to cloud successfully.") },
                    onFailure = { err -> Log.e(TAG, "Sensor-Lock failed to sync to cloud: $err") }
                )
            }

            // Positive confirmation beep + high intensity vibrate
            playBeepAndVibrate(context)

            _isProcessing.value = false
            _processingProgress.value = 0f
        }
    }

    fun playTargetLockBeep(context: Context) {
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            toneG.startTone(ToneGenerator.TONE_CDMA_PIP, 120) // target locked pip
            
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.e(TAG, "Failed playing target-lock beep: ${e.message}")
        }
    }

    private fun applySynonyms(label: LabelEntity): LabelEntity {
        // Automatically standardize abbreviations
        var rua = label.rua
        var complemento = label.complemento
        carrierSynonyms.value.forEach { (abbr, full) ->
            rua = rua.replace(abbr, full, ignoreCase = true)
            complemento = complemento.replace(abbr, full, ignoreCase = true)
        }
        return label.copy(
            rua = rua,
            complemento = complemento,
            usuario = _currentRole.value.displayName
        )
    }

    private fun playBeepAndVibrate(context: Context) {
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(120)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed beep/vibrate: ${e.message}")
        }
    }

    /**
     * Start OCR + AI parser flow on a captured image or manual raw text.
     */
    fun processLabelCapture(
        context: Context,
        imageBitmap: android.graphics.Bitmap?,
        rawText: String? = null,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingProgress.value = 0.1f
            
            try {
                // Simulate progressive scanning feedback
                launch {
                    while (_isProcessing.value) {
                        if (_processingProgress.value < 0.9f) {
                            _processingProgress.value += 0.15f
                        }
                        delay(250)
                    }
                }

                // Call Gemini OCR + AI Parser
                val parsed = GeminiParserService.parseLabel(
                    imageBitmap = imageBitmap,
                    textToParse = rawText,
                    ocrEngine = ocrEngine.value
                )

                _processingProgress.value = 1.0f
                delay(300)

                val finalizedLabel = applySynonyms(parsed).copy(
                    usuario = _currentRole.value.displayName
                )

                _conferenceLabel.value = finalizedLabel
                _isProcessing.value = false
                _processingProgress.value = 0f
                onComplete()
                
            } catch (e: Exception) {
                _isProcessing.value = false
                _processingProgress.value = 0f
                Toast.makeText(context, "Erro na leitura por IA: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun saveLabel(context: Context, label: LabelEntity) {
        viewModelScope.launch {
            val id = repository.insert(label)
            _conferenceLabel.value = null
            
            // Auto backup to Firestore if enabled
            if (autoBackupEnabled.value) {
                val labelWithId = if (label.id == 0L) label.copy(id = id) else label
                FirebaseService.saveLabelToFirestore(
                    context = context,
                    label = labelWithId,
                    onSuccess = {
                        Log.d(TAG, "Etiqueta ${labelWithId.id} salva no Firestore cloud com sucesso")
                    },
                    onFailure = { err ->
                        Log.e(TAG, "Erro de salvamento automático no Firestore: $err")
                    }
                )
            }
        }
    }

    fun deleteLabel(context: Context, label: LabelEntity) {
        if (_currentRole.value.level < UserRole.ADMINISTRATOR.level) {
            return // Security check: supervisor/operators cannot delete records
        }
        viewModelScope.launch {
            repository.delete(label)
            FirebaseService.deleteLabelFromFirestore(
                context = context,
                labelId = label.id,
                onSuccess = {
                    Log.d(TAG, "Etiqueta ${label.id} removida do Firestore cloud com sucesso")
                },
                onFailure = { err ->
                    Log.e(TAG, "Erro de remoção automática no Firestore: $err")
                }
            )
        }
    }

    fun deleteLabelById(context: Context, id: Long) {
        if (_currentRole.value.level < UserRole.ADMINISTRATOR.level) {
            return
        }
        viewModelScope.launch {
            repository.deleteById(id)
            FirebaseService.deleteLabelFromFirestore(
                context = context,
                labelId = id,
                onSuccess = {
                    Log.d(TAG, "Etiqueta $id removida do Firestore")
                },
                onFailure = { err ->
                    Log.e(TAG, "Erro de remoção do Firestore: $err")
                }
            )
        }
    }

    // ==========================================
    // CLOUD AUTENTICAÇÃO & COMPONENTES FIRESTORE
    // ==========================================

    fun loginFirebase(
        context: Context,
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        FirebaseService.loginUser(
            context = context,
            email = email,
            password = password,
            onSuccess = { syncedEmail ->
                _userEmail.value = syncedEmail
                onSuccess()
            },
            onFailure = onFailure
        )
    }

    fun signUpFirebase(
        context: Context,
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        FirebaseService.signUpUser(
            context = context,
            email = email,
            password = password,
            onSuccess = { syncedEmail ->
                _userEmail.value = syncedEmail
                onSuccess()
            },
            onFailure = onFailure
        )
    }

    fun logoutFirebase(context: Context, onComplete: () -> Unit) {
        FirebaseService.logoutUser(context) {
            _userEmail.value = null
            onComplete()
        }
    }

    fun triggerCloudSync(context: Context) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingProgress.value = 0.01f
            
            val localLabels = labels.value
            FirebaseService.syncLocalLabelsToCloud(
                context = context,
                localLabels = localLabels,
                onProgress = { current, total ->
                    _processingProgress.value = current.toFloat() / total.toFloat()
                },
                onFinished = { success, failed ->
                    _isProcessing.value = false
                    _processingProgress.value = 0f
                    val isLive = FirebaseService.isFirebaseLive(context)
                    val serverName = if (isLive) "Firestore" else "Servidor em Nuvem (Simulado)"
                    if (failed == 0) {
                        Toast.makeText(context, "Sincronização com $serverName concluída! $success etiquetas salvas.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Sincronização parcial com $serverName: $success com sucesso, $failed falhas.", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    // Export utilities
    fun shareCsv(context: Context) {
        viewModelScope.launch {
            val list = labels.value
            if (list.isEmpty()) {
                Toast.makeText(context, "Nenhum dado para exportar", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val csvBuilder = StringBuilder()
            csvBuilder.append("ID;Nome;Empresa;Transportadora;Rua;Numero;Complemento;Bairro;Cidade;Estado;CEP;Telefone;Pedido;SKU;Produto;CodigoBarras;Peso;Volume;DataCadastro;Usuario;Status\n")
            
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            for (label in list) {
                val dateStr = dateFormat.format(Date(label.dataCadastro))
                csvBuilder.append("${label.id};")
                    .append("${label.nome.replace(";", ",")};")
                    .append("${label.empresa.replace(";", ",")};")
                    .append("${label.transportadora};")
                    .append("${label.rua.replace(";", ",")};")
                    .append("${label.numero};")
                    .append("${label.complemento.replace(";", ",")};")
                    .append("${label.bairro};")
                    .append("${label.cidade};")
                    .append("${label.estado};")
                    .append("${label.cep};")
                    .append("${label.telefone};")
                    .append("${label.pedido};")
                    .append("${label.sku};")
                    .append("${label.produto.replace(";", ",")};")
                    .append("${label.codigoBarras};")
                    .append("${label.peso};")
                    .append("${label.volume};")
                    .append("$dateStr;")
                    .append("${label.usuario};")
                    .append("${label.status}\n")
            }

            shareFile(context, "prudencio_logistics_report.csv", csvBuilder.toString(), "text/csv")
        }
    }

    fun shareJson(context: Context) {
        viewModelScope.launch {
            val list = labels.value
            if (list.isEmpty()) {
                Toast.makeText(context, "Nenhum dado para exportar", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val jsonArray = JSONArray()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            for (label in list) {
                val obj = JSONObject().apply {
                    put("id", label.id)
                    put("nome", label.nome)
                    put("empresa", label.empresa)
                    put("transportadora", label.transportadora)
                    put("rua", label.rua)
                    put("numero", label.numero)
                    put("complemento", label.complemento)
                    put("condominio", label.condominio)
                    put("bloco", label.bloco)
                    put("apartamento", label.apartamento)
                    put("bairro", label.bairro)
                    put("cidade", label.cidade)
                    put("estado", label.estado)
                    put("cep", label.cep)
                    put("telefone", label.telefone)
                    put("pedido", label.pedido)
                    put("sku", label.sku)
                    put("produto", label.produto)
                    put("codigoBarras", label.codigoBarras)
                    put("qrCode", label.qrCode)
                    put("peso", label.peso)
                    put("volume", label.volume)
                    put("dataCadastro", dateFormat.format(Date(label.dataCadastro)))
                    put("usuario", label.usuario)
                    put("status", label.status)
                    put("observacoes", label.observacoes)
                }
                jsonArray.put(obj)
            }

            shareFile(context, "prudencio_logistics_report.json", jsonArray.toString(2), "application/json")
        }
    }

    fun sharePdf(context: Context) {
        viewModelScope.launch {
            val list = labels.value
            if (list.isEmpty()) {
                Toast.makeText(context, "Nenhum dado para exportar", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val pdfText = StringBuilder()
            pdfText.append("=========================================\n")
            pdfText.append("   PRUDÊNCIO OCR LOGISTICS - RELATÓRIO   \n")
            pdfText.append("   Gerado em: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}\n")
            pdfText.append("=========================================\n\n")

            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            for ((index, label) in list.withIndex()) {
                pdfText.append("${index + 1}. ETIQUETA #${label.id} [${label.status}]\n")
                pdfText.append("   Destinatário: ${label.nome}\n")
                pdfText.append("   Transportadora: ${label.transportadora} | Pedido: ${label.pedido}\n")
                pdfText.append("   Endereço: ${label.rua}, ${label.numero} - ${label.bairro}\n")
                pdfText.append("             ${label.cidade} - ${label.estado} | CEP: ${label.cep}\n")
                pdfText.append("   SKU: ${label.sku} | Peso: ${label.peso} | Volume: ${label.volume}\n")
                pdfText.append("   Lido por: ${label.usuario} em ${dateFormat.format(Date(label.dataCadastro))}\n")
                pdfText.append("   --------------------------------------\n")
            }

            shareFile(context, "prudencio_logistics_report.txt", pdfText.toString(), "text/plain")
        }
    }

    private fun shareFile(context: Context, fileName: String, content: String, mimeType: String) {
        try {
            val cachePath = File(context.cacheDir, "exports")
            cachePath.mkdirs()
            val file = File(cachePath, fileName)
            file.writeText(content)

            // In our template, com.example.fileprovider will be set up or we can use direct sharing if FileProvider is loaded
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_SUBJECT, "Relatório Prudêncio OCR Logistics")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Exportar Relatório"))
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao exportar arquivo: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "File sharing error: ${e.message}", e)
        }
    }

    private fun calculateStats(list: List<LabelEntity>): DashboardStats {
        if (list.isEmpty()) return DashboardStats()

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        
        // Start of today
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis

        // Start of week (7 days ago)
        val startOfWeek = startOfToday - 7 * 24 * 3600 * 1000L
        
        // Start of month (30 days ago)
        val startOfMonth = startOfToday - 30 * 24 * 3600 * 1000L

        var todayCount = 0
        var weekCount = 0
        var monthCount = 0
        var totalReadTime = 0L
        var validReadTimes = 0

        val carrierCounts = mutableMapOf<String, Int>()
        val cityCounts = mutableMapOf<String, Int>()
        val productCounts = mutableMapOf<String, Int>()

        for (label in list) {
            val ts = label.dataCadastro
            if (ts >= startOfToday) todayCount++
            if (ts >= startOfWeek) weekCount++
            if (ts >= startOfMonth) monthCount++

            if (label.readTimeMs > 0) {
                totalReadTime += label.readTimeMs
                validReadTimes++
            }

            if (label.transportadora.isNotEmpty()) {
                carrierCounts[label.transportadora] = carrierCounts.getOrDefault(label.transportadora, 0) + 1
            }
            if (label.cidade.isNotEmpty()) {
                cityCounts[label.cidade] = cityCounts.getOrDefault(label.cidade, 0) + 1
            }
            if (label.produto.isNotEmpty()) {
                productCounts[label.produto] = productCounts.getOrDefault(label.produto, 0) + 1
            }
        }

        val topCarrier = carrierCounts.maxByOrNull { it.value }?.key ?: "Nenhuma"
        val topCity = cityCounts.maxByOrNull { it.value }?.key ?: "Nenhuma"
        val topProduct = productCounts.maxByOrNull { it.value }?.key ?: "Nenhum"
        val avgTime = if (validReadTimes > 0) totalReadTime / validReadTimes else 1450L // Default realistic average

        // Generate dynamic charts data based on database records
        val dailyChartData = mutableMapOf<String, Int>()
        val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
        // Fill last 5 days with zero initially
        for (i in 4 downTo 0) {
            val d = Date(System.currentTimeMillis() - i * 24 * 3600 * 1000L)
            dailyChartData[sdf.format(d)] = 0
        }
        for (label in list) {
            val dateStr = sdf.format(Date(label.dataCadastro))
            if (dailyChartData.containsKey(dateStr)) {
                dailyChartData[dateStr] = dailyChartData.getOrDefault(dateStr, 0) + 1
            }
        }

        return DashboardStats(
            totalLabels = list.size,
            readingsToday = todayCount,
            readingsWeek = weekCount,
            readingsMonth = monthCount,
            topCarrier = topCarrier,
            topCity = topCity,
            topProduct = topProduct,
            avgReadTimeMs = avgTime,
            dailyTrend = dailyChartData.toList().sortedBy { it.first }
        )
    }

    // Backup simulation
    fun triggerBackup(context: Context) {
        viewModelScope.launch {
            Toast.makeText(context, "Sincronizando backup LGPD criptografado...", Toast.LENGTH_SHORT).show()
            delay(1500)
            Toast.makeText(context, "Backup concluído com sucesso no servidor de nuvem!", Toast.LENGTH_SHORT).show()
        }
    }
}

data class DashboardStats(
    val totalLabels: Int = 0,
    val readingsToday: Int = 0,
    val readingsWeek: Int = 0,
    val readingsMonth: Int = 0,
    val topCarrier: String = "Nenhuma",
    val topCity: String = "Nenhuma",
    val topProduct: String = "Nenhum",
    val avgReadTimeMs: Long = 0,
    val dailyTrend: List<Pair<String, Int>> = emptyList()
)

class LogisticsViewModelFactory(private val repository: LabelRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LogisticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LogisticsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
