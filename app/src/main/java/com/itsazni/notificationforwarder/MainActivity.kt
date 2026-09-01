package com.itsazni.notificationforwarder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itsazni.notificationforwarder.data.NotificationRepository
import com.itsazni.notificationforwarder.data.QueueItem
import com.itsazni.notificationforwarder.data.QueueStats
import com.itsazni.notificationforwarder.data.QueueStatus
import com.itsazni.notificationforwarder.network.WebhookClient
import com.itsazni.notificationforwarder.settings.AppSettings
import com.itsazni.notificationforwarder.settings.AuthMode
import com.itsazni.notificationforwarder.settings.FilterMode
import com.itsazni.notificationforwarder.settings.SettingsStore
import com.itsazni.notificationforwarder.ui.theme.AppTheme
import com.itsazni.notificationforwarder.worker.WorkerScheduler
import com.google.gson.JsonParser
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Trang chủ", Icons.Filled.Home),
    WEBHOOK("Webhook", Icons.Filled.Link),
    FILTER("Bộ lọc", Icons.Filled.Tune),
    QUEUE("Hàng đợi", Icons.AutoMirrored.Filled.List)
}

private fun FilterMode.toDisplayLabel(): String = when (this) {
    FilterMode.ALL_APPS -> "Tất cả ứng dụng (ALL_APPS)"
    FilterMode.WHITELIST -> "Danh sách cho phép (WHITELIST)"
    FilterMode.BLACKLIST -> "Danh sách chặn (BLACKLIST)"
}

private fun AuthMode.toDisplayLabel(): String = when (this) {
    AuthMode.NONE -> "Không xác thực (NONE)"
    AuthMode.BEARER -> "Bearer Token"
    AuthMode.CUSTOM -> "Headers tùy chỉnh (CUSTOM)"
}

private fun QueueStatus.toDisplayLabel(): String = when (this) {
    QueueStatus.PENDING -> "Chờ gửi"
    QueueStatus.SENDING -> "Đang gửi"
    QueueStatus.SENT -> "Đã gửi"
    QueueStatus.FAILED -> "Thất bại"
}

private data class UiSettings(
    val webhookUrl: String,
    val webhookMethod: String,
    val forwardingEnabled: Boolean,
    val filterMode: FilterMode,
    val filterPackagesRaw: String,
    val authMode: AuthMode,
    val bearerToken: String,
    val customHeadersRaw: String,
    val queryParamsRaw: String,
    val payloadTemplateRaw: String,
    val maxRetriesRaw: String,
    val batchSizeRaw: String
)

private fun AppSettings.toUiSettings(): UiSettings {
    return UiSettings(
        webhookUrl = webhookUrl,
        webhookMethod = webhookMethod,
        forwardingEnabled = forwardingEnabled,
        filterMode = filterMode,
        filterPackagesRaw = filterPackages.joinToString("\n"),
        authMode = authMode,
        bearerToken = bearerToken,
        customHeadersRaw = customHeadersRaw,
        queryParamsRaw = queryParamsRaw,
        payloadTemplateRaw = payloadTemplateRaw,
        maxRetriesRaw = maxRetries.toString(),
        batchSizeRaw = batchSize.toString()
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsStore = SettingsStore(this)

        setContent {
            AppTheme {
                MainScreen(settingsStore = settingsStore)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { NotificationRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var uiSettings by remember { mutableStateOf(settingsStore.readAll().toUiSettings()) }
    var showTestDialog by remember { mutableStateOf(false) }

    val stats by repository.observeStats().collectAsState(
        initial = QueueStats(0, 0, 0, 0)
    )
    val recent by repository.observeRecent(30).collectAsState(initial = emptyList())

    Scaffold(
        topBar = { TopAppBar(title = { Text("AutoBankPay Pro") }) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp
            ) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            AppTab.HOME -> HomeScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                stats = stats
            )

            AppTab.WEBHOOK -> {
                val onScanQrCode: () -> Unit = {
                    try {
                        val options = GmsBarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()
                        val scanner = GmsBarcodeScanning.getClient(context, options)
                        scanner.startScan()
                            .addOnSuccessListener { barcode ->
                                val rawValue = barcode.rawValue?.trim().orEmpty()
                                if (rawValue.isBlank()) return@addOnSuccessListener
                                try {
                                    val json = JsonParser.parseString(rawValue).asJsonObject
                                    val newUrl = json.get("webhookUrl")?.asString ?: rawValue
                                    val newMethod = json.get("webhookMethod")?.asString ?: "POST"
                                    val newAuthMode = when (json.get("authMode")?.asString?.uppercase()) {
                                        "BEARER" -> AuthMode.BEARER
                                        "CUSTOM" -> AuthMode.CUSTOM
                                        else -> AuthMode.NONE
                                    }
                                    val newToken = json.get("bearerToken")?.asString ?: ""
                                    val newEnabled = json.get("forwardingEnabled")?.asBoolean ?: true
                                    val updated = uiSettings.copy(
                                        webhookUrl = newUrl,
                                        webhookMethod = newMethod,
                                        authMode = newAuthMode,
                                        bearerToken = newToken,
                                        forwardingEnabled = newEnabled
                                    )
                                    uiSettings = updated
                                    saveSettings(settingsStore, updated)
                                    WorkerScheduler.enqueueImmediate(context)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Đã quét và nạp cấu hình Webhook thành công!")
                                    }
                                    showTestDialog = true
                                } catch (_: Exception) {
                                    if (rawValue.startsWith("http://") || rawValue.startsWith("https://")) {
                                        val updated = uiSettings.copy(webhookUrl = rawValue, forwardingEnabled = true)
                                        uiSettings = updated
                                        saveSettings(settingsStore, updated)
                                        WorkerScheduler.enqueueImmediate(context)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Đã lưu URL Webhook từ mã QR!")
                                        }
                                        showTestDialog = true
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Mã QR không đúng định dạng Webhook")
                                        }
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                scope.launch {
                                    snackbarHostState.showSnackbar("Hủy quét hoặc lỗi: ${e.localizedMessage ?: "Đã hủy"}")
                                }
                            }
                    } catch (e: Exception) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Không thể mở trình quét QR: ${e.localizedMessage}")
                        }
                    }
                }

                WebhookScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    uiSettings = uiSettings,
                    onSettingsChange = { uiSettings = it },
                    onSave = {
                        saveSettings(settingsStore, uiSettings)
                        WorkerScheduler.enqueueImmediate(context)
                        scope.launch { snackbarHostState.showSnackbar("Đã lưu cài đặt Webhook") }
                    },
                    onTestWebhook = {
                        showTestDialog = true
                    },
                    onScanQrCode = onScanQrCode
                )
            }

            AppTab.FILTER -> FilterScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                uiSettings = uiSettings,
                onSettingsChange = { uiSettings = it },
                onSave = {
                    saveSettings(settingsStore, uiSettings)
                    scope.launch { snackbarHostState.showSnackbar("Đã lưu cài đặt bộ lọc & thử lại") }
                }
            )

            AppTab.QUEUE -> QueueScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                recent = recent,
                onDeleteItem = { itemId ->
                    scope.launch {
                        repository.deleteQueueItem(itemId)
                        snackbarHostState.showSnackbar("Đã xóa mục trong hàng đợi")
                    }
                },
                onClearQueue = {
                    scope.launch {
                        repository.clearQueue()
                        snackbarHostState.showSnackbar("Đã xóa sạch hàng đợi")
                    }
                }
            )
        }
    }

    if (showTestDialog) {
        TestBankWebhookDialog(
            uiSettings = uiSettings,
            onDismiss = { showTestDialog = false },
            onSendTest = { appName, pkg, title, text ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        WebhookClient().send(
                            url = uiSettings.webhookUrl,
                            method = uiSettings.webhookMethod,
                            headers = buildHeadersPreview(
                                authMode = uiSettings.authMode,
                                token = uiSettings.bearerToken,
                                customHeadersRaw = uiSettings.customHeadersRaw
                            ),
                            queryParams = parseKeyValuePairs(uiSettings.queryParamsRaw),
                            payloadTemplate = uiSettings.payloadTemplateRaw,
                            item = QueueItem(
                                packageName = pkg,
                                appName = appName,
                                title = title,
                                text = text,
                                postedAt = System.currentTimeMillis(),
                                notificationKey = "test-${System.currentTimeMillis()}"
                            ),
                            deviceId = "test-device"
                        )
                    }
                    snackbarHostState.showSnackbar(
                        if (result.success) "✅ Gửi thử thành công! Phản hồi: ${result.message}"
                        else "⚠️ Gửi thử thất bại: ${result.message}"
                    )
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        WorkerScheduler.ensurePeriodic(context)
    }
}

@Composable
private fun HomeScreen(modifier: Modifier, stats: QueueStats) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Trạng thái dịch vụ", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Quyền đọc thông báo")
                        val enabled = isNotificationListenerEnabled(context)
                        StatusBadge(
                            text = if (enabled) "Đã cấp quyền" else "Chưa cấp quyền",
                            success = enabled
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tối ưu hóa pin")
                        val unrestricted = isBatteryUnrestricted(context)
                        StatusBadge(
                            text = if (unrestricted) "Không hạn chế" else "Bị hạn chế",
                            success = unrestricted
                        )
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    ) {
                        Text("Mở cài đặt quyền thông báo")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { openBatterySettings(context) }
                    ) {
                        Text("Mở cài đặt tối ưu pin")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { WorkerScheduler.enqueueImmediate(context) }
                    ) {
                        Text("Đồng bộ hàng đợi ngay")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Thống kê hàng đợi", fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QueueStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Chờ gửi",
                            value = stats.pendingCount.toString(),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        QueueStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Đang gửi",
                            value = stats.sendingCount.toString(),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QueueStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Đã gửi",
                            value = stats.sentCount.toString(),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        QueueStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Thất bại",
                            value = stats.failedCount.toString(),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WebhookScreen(
    modifier: Modifier,
    uiSettings: UiSettings,
    onSettingsChange: (UiSettings) -> Unit,
    onSave: () -> Unit,
    onTestWebhook: () -> Unit,
    onScanQrCode: () -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cài đặt Webhook", fontWeight = FontWeight.SemiBold)

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onScanQrCode,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Filled.QrCodeScanner, contentDescription = "Quét mã QR")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Quét mã QR cấu hình tự động")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bật chuyển tiếp thông báo")
                        Switch(
                            checked = uiSettings.forwardingEnabled,
                            onCheckedChange = { onSettingsChange(uiSettings.copy(forwardingEnabled = it)) }
                        )
                    }

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiSettings.webhookUrl,
                        onValueChange = { onSettingsChange(uiSettings.copy(webhookUrl = it)) },
                        label = { Text("URL Webhook (vd: http://192.168.1.10:3000/webhook)") },
                        singleLine = true
                    )

                    DropdownSelector(
                        label = "Phương thức HTTP",
                        selectedValue = uiSettings.webhookMethod,
                        options = listOf("GET", "POST", "PUT", "PATCH"),
                        itemLabel = { it },
                        onSelected = {
                            onSettingsChange(uiSettings.copy(webhookMethod = it))
                        }
                    )

                    DropdownSelector(
                        label = "Chế độ xác thực (Auth mode)",
                        selectedValue = uiSettings.authMode,
                        options = AuthMode.entries,
                        itemLabel = { it.toDisplayLabel() },
                        onSelected = {
                            onSettingsChange(uiSettings.copy(authMode = it))
                        }
                    )

                    if (uiSettings.authMode == AuthMode.BEARER) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = uiSettings.bearerToken,
                            onValueChange = { onSettingsChange(uiSettings.copy(bearerToken = it)) },
                            label = { Text("Bearer Token") }
                        )
                    }

                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        value = uiSettings.customHeadersRaw,
                        onValueChange = { onSettingsChange(uiSettings.copy(customHeadersRaw = it)) },
                        label = { Text("Headers tùy chỉnh (Mỗi dòng một Header 'Key: Value')") }
                    )

                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        value = uiSettings.queryParamsRaw,
                        onValueChange = { onSettingsChange(uiSettings.copy(queryParamsRaw = it)) },
                        label = { Text("Tham số truy vấn (Mỗi dòng một 'key=value')") }
                    )

                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        value = uiSettings.payloadTemplateRaw,
                        onValueChange = { onSettingsChange(uiSettings.copy(payloadTemplateRaw = it)) },
                        label = { Text("Mẫu JSON Payload (hỗ trợ {title}, {text}, {appName}, {packageName}, {postedAt}, {deviceId})") }
                    )

                    Button(modifier = Modifier.fillMaxWidth(), onClick = onSave) {
                        Text("Lưu cài đặt Webhook")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onTestWebhook,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(imageVector = Icons.Filled.Send, contentDescription = "Gửi thử")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🧪 Thử nghiệm gửi Webhook (Mô phỏng Ngân hàng)")
                    }
                }
            }
        }
    }
}

@Composable
private fun TestBankWebhookDialog(
    uiSettings: UiSettings,
    onDismiss: () -> Unit,
    onSendTest: (appName: String, pkg: String, title: String, text: String) -> Unit
) {
    var selectedBank by remember { mutableStateOf("MB Bank") }
    var testAmount by remember { mutableStateOf("50000") }
    var testOrderId by remember { mutableStateOf("123") }

    val bankList = listOf(
        "MB Bank" to "com.mbmobile",
        "Vietcombank" to "com.VCB",
        "Techcombank" to "vn.com.techcombank.bb.app",
        "ACB" to "mobile.acb.com.vn",
        "MoMo" to "com.mservice.momopay"
    )

    val previewText = when (selectedBank) {
        "MB Bank" -> "TK 123456789 | GD: +${testAmount}VND | SD: 10,000,000VND | ND: MEPET ${testOrderId} chuyen tien"
        "Vietcombank" -> "SD TK 0123456789 +${testAmount}VND vao 12:00. Ref VCB.1234. ND: DH ${testOrderId}"
        "Techcombank" -> "Giao dịch thành công +${testAmount} VND tai Techcombank. Noi dung: MEPET ${testOrderId}"
        "ACB" -> "ACB: +${testAmount} VND vao TK ... ND: DONHANG ${testOrderId}"
        "MoMo" -> "Bạn vừa nhận được ${testAmount}đ từ Khach Hang. Lời nhắn: #${testOrderId}"
        else -> "+${testAmount}VND ND: MEPET ${testOrderId}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🧪 Thử Nghiệm Gửi Webhook", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Giả lập thông báo nhận tiền gửi tới website để kiểm tra đơn hàng tự kích hoạt:",
                    style = MaterialTheme.typography.bodySmall
                )

                Text("Chọn Ngân hàng giả lập:", fontWeight = FontWeight.SemiBold)
                bankList.forEach { (name, _) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (selectedBank == name),
                                onClick = { selectedBank = name }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedBank == name),
                            onClick = { selectedBank = name }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(name)
                    }
                }

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = testAmount,
                    onValueChange = { testAmount = it },
                    label = { Text("Số tiền giả lập (VNĐ)") },
                    singleLine = true
                )

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = testOrderId,
                    onValueChange = { testOrderId = it },
                    label = { Text("Mã đơn hàng test (vd: 123)") },
                    singleLine = true
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Nội dung gửi thử tới Web:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text(previewText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val pkg = bankList.find { it.first == selectedBank }?.second ?: "com.mbmobile"
                onSendTest(selectedBank, pkg, "Biến động số dư $selectedBank", previewText)
                onDismiss()
            }) {
                Text("🚀 Bắn Test Tới Web")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

@Composable
private fun FilterScreen(
    modifier: Modifier,
    uiSettings: UiSettings,
    onSettingsChange: (UiSettings) -> Unit,
    onSave: () -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Bộ lọc & Thử lại", fontWeight = FontWeight.SemiBold)

                    DropdownSelector(
                        label = "Chế độ lọc",
                        selectedValue = uiSettings.filterMode,
                        options = FilterMode.entries,
                        itemLabel = { it.toDisplayLabel() },
                        onSelected = {
                            onSettingsChange(uiSettings.copy(filterMode = it))
                        }
                    )

                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        value = uiSettings.filterPackagesRaw,
                        onValueChange = { onSettingsChange(uiSettings.copy(filterPackagesRaw = it)) },
                        label = { Text("Danh sách Package Name (phân cách bằng dấu phẩy hoặc dòng mới)") }
                    )

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiSettings.maxRetriesRaw,
                        onValueChange = {
                            onSettingsChange(uiSettings.copy(maxRetriesRaw = it.filter { c -> c.isDigit() }))
                        },
                        label = { Text("Số lần thử lại tối đa") },
                        singleLine = true
                    )

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiSettings.batchSizeRaw,
                        onValueChange = {
                            onSettingsChange(uiSettings.copy(batchSizeRaw = it.filter { c -> c.isDigit() }))
                        },
                        label = { Text("Số lượng gửi mỗi đợt (Batch size)") },
                        singleLine = true
                    )

                    Button(modifier = Modifier.fillMaxWidth(), onClick = onSave) {
                        Text("Lưu cài đặt bộ lọc & thử lại")
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueScreen(
    modifier: Modifier,
    recent: List<QueueItem>,
    onDeleteItem: (Long) -> Unit,
    onClearQueue: () -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Danh sách hàng đợi gần đây", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onClearQueue,
                        enabled = recent.isNotEmpty()
                    ) {
                        Text("Xóa toàn bộ hàng đợi")
                    }
                }
            }
        }
        items(recent) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.appName.ifBlank { item.packageName }, fontWeight = FontWeight.SemiBold)
                        QueueStatusBadge(status = item.status)
                    }
                    Text(item.title.ifBlank { "(không có tiêu đề)" })
                    Text(item.text.ifBlank { "(không có nội dung)" })
                    Text("Gói: ${item.packageName}")
                    Text("Số lần thử: ${item.attemptCount}")
                    if (!item.lastError.isNullOrBlank()) {
                        Text("Lỗi: ${item.lastError}")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onDeleteItem(item.id) }
                    ) {
                        Text("Xóa mục này")
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownSelector(
    label: String,
    selectedValue: T,
    options: List<T>,
    itemLabel: (T) -> String = { it.toString() },
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = itemLabel(selectedValue),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(itemLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, success: Boolean) {
    val container = if (success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun QueueStatusBadge(status: QueueStatus) {
    val (container, content) = when (status) {
        QueueStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        QueueStatus.SENDING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        QueueStatus.SENT -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        QueueStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = status.toDisplayLabel(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun QueueStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private fun saveSettings(settingsStore: SettingsStore, uiSettings: UiSettings) {
    settingsStore.webhookUrl = uiSettings.webhookUrl
    settingsStore.webhookMethod = uiSettings.webhookMethod
    settingsStore.forwardingEnabled = uiSettings.forwardingEnabled
    settingsStore.filterMode = uiSettings.filterMode
    settingsStore.filterPackages = SettingsStore.parsePackages(uiSettings.filterPackagesRaw)
    settingsStore.authMode = uiSettings.authMode
    settingsStore.bearerToken = uiSettings.bearerToken
    settingsStore.customHeadersRaw = uiSettings.customHeadersRaw
    settingsStore.queryParamsRaw = uiSettings.queryParamsRaw
    settingsStore.payloadTemplateRaw = uiSettings.payloadTemplateRaw
    settingsStore.maxRetries = uiSettings.maxRetriesRaw.toIntOrNull() ?: 10
    settingsStore.batchSize = uiSettings.batchSizeRaw.toIntOrNull() ?: 20
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (enabled.isNullOrBlank()) {
        return false
    }
    val target = ComponentName(context, com.itsazni.notificationforwarder.service.AppNotificationListenerService::class.java)
    return enabled.contains(target.flattenToString())
}

private fun parseKeyValuePairs(raw: String): Map<String, String> {
    val map = linkedMapOf<String, String>()
    raw.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@forEach
        val idx = trimmed.indexOf('=')
        if (idx > 0) {
            val key = trimmed.substring(0, idx).trim()
            val value = trimmed.substring(idx + 1).trim()
            if (key.isNotEmpty()) map[key] = value
        }
    }
    return map
}

private fun buildHeadersPreview(
    authMode: AuthMode,
    token: String,
    customHeadersRaw: String
): Map<String, String> {
    val headers = linkedMapOf("Content-Type" to "application/json")
    if (authMode == AuthMode.BEARER && token.isNotBlank()) {
        headers["Authorization"] = "Bearer $token"
    }
    customHeadersRaw.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.contains(':')) {
            val idx = trimmed.indexOf(':')
            headers[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim()
        }
    }
    return headers
}

private fun openBatterySettings(context: Context) {
    val primaryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    val fallbackIntent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )

    runCatching { context.startActivity(primaryIntent) }
        .onFailure { context.startActivity(fallbackIntent) }
}

private fun isBatteryUnrestricted(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
