package com.nexus.iptv.feature.sources.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.iptv.core.common.security.UrlRedactor
import com.nexus.iptv.core.database.entity.SourceEntity
import com.nexus.iptv.core.uitv.focus.nexusTvFocusable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AddSourceTab { M3U_URL, XTREAM_CODES }

@Composable
fun SourcesManagerScreen(
    sources: List<SourceEntity>,
    statusBannerMessage: String?,
    accentColor: Color,
    onAddM3uUrl: (name: String, url: String, epgUrl: String?) -> Unit,
    onAddXtream: (name: String, serverUrl: String, user: String, pass: String) -> Unit,
    onSyncSource: (Long) -> Unit,
    onVerifySource: (SourceEntity) -> Unit,
    onDeleteSourceConfirmed: (Long) -> Unit,
    onExportBackup: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var activeTab by remember { mutableStateOf(AddSourceTab.M3U_URL) }
    var sourceName by remember { mutableStateOf("") }
    var endpointUrl by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    var xtreamUser by remember { mutableStateOf("") }
    var xtreamPass by remember { mutableStateOf("") }
    var pendingDeleteSourceId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A12))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Менеджер источников IPTV",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Поддержка M3U, M3U8, Xtream Codes API, XMLTV EPG и AES-256 Backup",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    text = "Экспорт Backup",
                    accentColor = accentColor,
                    onClick = onExportBackup
                )
                ActionButton(
                    text = "На главную",
                    accentColor = Color(0xFF64748B),
                    onClick = onNavigateBack
                )
            }
        }

        if (!statusBannerMessage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.16f))
                    .padding(12.dp)
            ) {
                Text(text = statusBannerMessage, color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF121A2A))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    text = "Плейлист M3U / M3U8",
                    accentColor = if (activeTab == AddSourceTab.M3U_URL) accentColor else Color(0xFF334155),
                    onClick = { activeTab = AddSourceTab.M3U_URL }
                )
                ActionButton(
                    text = "Xtream Codes API",
                    accentColor = if (activeTab == AddSourceTab.XTREAM_CODES) accentColor else Color(0xFF334155),
                    onClick = { activeTab = AddSourceTab.XTREAM_CODES }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = sourceName,
                    onValueChange = { sourceName = it },
                    label = { Text("Название провайдера") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endpointUrl,
                    onValueChange = { endpointUrl = it },
                    label = {
                        Text(
                            if (activeTab == AddSourceTab.M3U_URL) "URL ссылки M3U / M3U8"
                            else "Адрес сервера (http://host:port)"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1.5f)
                )
            }

            if (activeTab == AddSourceTab.M3U_URL) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = epgUrl,
                        onValueChange = { epgUrl = it },
                        label = { Text("Ссылка XMLTV EPG (определяется автоматически из #EXTM3U)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        text = "Добавить и загрузить M3U",
                        accentColor = accentColor,
                        onClick = {
                            if (sourceName.isNotBlank() && endpointUrl.isNotBlank()) {
                                onAddM3uUrl(sourceName, endpointUrl, epgUrl.takeIf { it.isNotBlank() })
                                sourceName = ""
                                endpointUrl = ""
                                epgUrl = ""
                            }
                        }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = xtreamUser,
                        onValueChange = { xtreamUser = it },
                        label = { Text("Логин (Username)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = xtreamPass,
                        onValueChange = { xtreamPass = it },
                        label = { Text("Пароль (шифруется AES-256)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        text = "Подключить Xtream",
                        accentColor = accentColor,
                        onClick = {
                            if (sourceName.isNotBlank() && endpointUrl.isNotBlank() && xtreamUser.isNotBlank()) {
                                onAddXtream(sourceName, endpointUrl, xtreamUser, xtreamPass)
                                sourceName = ""
                                endpointUrl = ""
                                xtreamUser = ""
                                xtreamPass = ""
                            }
                        }
                    )
                }
            }
        }

        Text(
            text = "Подключённые источники (${sources.size})",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sources, key = { it.id }) { source ->
                SourceItemRow(
                    source = source,
                    accentColor = accentColor,
                    isDeleteConfirmationAsked = pendingDeleteSourceId == source.id,
                    onSync = { onSyncSource(source.id) },
                    onVerify = { onVerifySource(source) },
                    onAskDelete = { pendingDeleteSourceId = source.id },
                    onConfirmDelete = {
                        onDeleteSourceConfirmed(source.id)
                        pendingDeleteSourceId = null
                    },
                    onCancelDelete = { pendingDeleteSourceId = null }
                )
            }
        }
    }
}

@Composable
private fun SourceItemRow(
    source: SourceEntity,
    accentColor: Color,
    isDeleteConfirmationAsked: Boolean,
    onSync: () -> Unit,
    onVerify: () -> Unit,
    onAskDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val lastUpdatedText = if (source.lastUpdatedEpochMs > 0L) {
        dateFormat.format(Date(source.lastUpdatedEpochMs))
    } else "Ещё не обновлялся"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF141C2B))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = source.name,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(text = source.sourceType, color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = UrlRedactor.redact(source.endpointUrl),
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
            Text(
                text = "Статус: ${source.lastSyncStatus} • Обновлено: $lastUpdatedText • Автообновление: каждые ${source.autoUpdateIntervalHours} ч.",
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(text = "Обновить", accentColor = accentColor, onClick = onSync)
            ActionButton(text = "Проверить", accentColor = Color(0xFF38BDF8), onClick = onVerify)

            if (!isDeleteConfirmationAsked) {
                ActionButton(text = "Удалить", accentColor = Color(0xFFEF4444), onClick = onAskDelete)
            } else {
                ActionButton(text = "Подтвердить удаление?", accentColor = Color(0xFFDC2626), onClick = onConfirmDelete)
                ActionButton(text = "Отмена", accentColor = Color(0xFF475569), onClick = onCancelDelete)
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = 0.22f))
            .nexusTvFocusable(accentColor = accentColor, cornerRadius = 10.dp, onSelect = onClick)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
