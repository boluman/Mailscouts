package com.mailscout.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    secondary = Color(0xFF0D9488),
    background = Color(0xFFF6F7FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEFF7),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF818CF8),
    secondary = Color(0xFF2DD4BF),
    background = Color(0xFF0F1117),
    surface = Color(0xFF181B24),
    surfaceVariant = Color(0xFF222633),
)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Accept a link shared from the browser ("Share" -> MailScout).
        intent?.getStringExtra(android.content.Intent.EXTRA_TEXT)?.let { if (vm.input.isBlank()) vm.input = it }

        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
                MailScoutScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailScoutScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val hits = vm.visible

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header() }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = vm.mode == Mode.URLS,
                                onClick = { vm.mode = Mode.URLS },
                                label = { Text("Website links") },
                            )
                            FilterChip(
                                selected = vm.mode == Mode.SEARCH,
                                onClick = { vm.mode = Mode.SEARCH },
                                label = { Text("Keyword search") },
                                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                            )
                        }
                        OutlinedTextField(
                            value = vm.input,
                            onValueChange = { vm.input = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = if (vm.mode == Mode.URLS) 3 else 1,
                            maxLines = 6,
                            shape = RoundedCornerShape(14.dp),
                            label = {
                                Text(
                                    if (vm.mode == Mode.URLS) "One website per line"
                                    else "e.g. dentists in Lagos"
                                )
                            },
                        )

                        Column {
                            Text(
                                "Pages per site: ${vm.maxPages.toInt()}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Slider(value = vm.maxPages, onValueChange = { vm.maxPages = it }, valueRange = 1f..15f)
                        }
                        ToggleRow("Verify mail servers (MX)", vm.verifyMx) { vm.verifyMx = it }
                        ToggleRow("Respect robots.txt", vm.respectRobots) { vm.respectRobots = it }

                        if (vm.running) {
                            Button(
                                onClick = { vm.stop() },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) {
                                Icon(Icons.Default.Close, null)
                                Spacer(Modifier.size(8.dp))
                                Text("Stop", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Button(
                                onClick = { vm.start() },
                                enabled = vm.input.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.size(8.dp))
                                Text("Extract emails", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (vm.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(vm.status, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (vm.results.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${hits.size} emails",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { clipboard.setText(AnnotatedString(vm.allEmails())) }) { Text("Copy all") }
                        IconButton(onClick = { vm.shareCsv(context) }) { Icon(Icons.Default.Share, "Export CSV") }
                        IconButton(onClick = { vm.clear() }) { Icon(Icons.Default.Delete, "Clear") }
                    }
                }
                item {
                    OutlinedTextField(
                        value = vm.filter,
                        onValueChange = { vm.filter = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text("Filter results") },
                    )
                }
                items(hits, key = { it.email }) { hit ->
                    HitRow(hit) { clipboard.setText(AnnotatedString(hit.email)) }
                }
            } else if (!vm.running) {
                item {
                    Text(
                        "Paste website links or search by keyword. Tap any result to copy it.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)) {
        Box(
            Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("@", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.size(12.dp))
        Column {
            Text("MailScout", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "Free email finder",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun HitRow(hit: Hit, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCopy() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(hit.email, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(hit.source, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val (label, color) = when (hit.mx) {
                true -> "Mail OK" to MaterialTheme.colorScheme.secondary
                false -> "No MX" to MaterialTheme.colorScheme.error
                null -> "" to Color.Unspecified
            }
            if (label.isNotEmpty()) Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
