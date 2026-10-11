package com.adminctl.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

@Composable
fun App() {
    var site by remember { mutableStateOf<Site?>(null) }
    var loggedIn by remember { mutableStateOf(false) }
    BackHandler(enabled = site != null) { site = null; loggedIn = false }
    val s = site
    when {
        s == null -> SitePicker { site = it }
        !loggedIn -> LoginScreen(s, onBack = { site = null }) { loggedIn = true }
        else -> AdminScreen(s) { site = null; loggedIn = false }
    }
}

@Composable
fun SitePicker(onPick: (Site) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp).statusBarsPadding()) {
        Text("Admin Control", style = MaterialTheme.typography.headlineMedium)
        Text("Choose a site to manage", Modifier.padding(bottom = 16.dp))
        SITES.forEach { s ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onPick(s) }) {
                Column(Modifier.padding(20.dp)) {
                    Text(s.name, style = MaterialTheme.typography.titleLarge)
                    Text(s.baseUrl, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun LoginScreen(site: Site, onBack: () -> Unit, onOk: () -> Unit) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp).statusBarsPadding(), verticalArrangement = Arrangement.Center) {
        Text(site.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(user, { user = it; err = null }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(pass, { pass = it; err = null }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true
            scope.launch {
                val r = withContext(Dispatchers.IO) { Api.login(site, user.trim(), pass) }
                busy = false
                if (r == null) onOk() else err = r
            }
        }) { Text(if (busy) "Signing in…" else "Sign in") }
        TextButton(onClick = onBack) { Text("← Back") }
    }
}

@Composable
fun AdminScreen(site: Site, onExit: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(site.name, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onExit) { Text("Switch site") }
        }
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Applications") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Payments") })
        }
        if (tab == 0) ListPane(site, "applications", payments = false) else ListPane(site, "payments", payments = true)
    }
}

@Composable
fun ListPane(site: Site, path: String, payments: Boolean) {
    var items by remember(path) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var msg by remember(path) { mutableStateOf<String?>("Loading…") }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(path, reload) {
        val r = withContext(Dispatchers.IO) { Api.list(site, path) }
        r.onSuccess { items = it; msg = if (it.isEmpty()) "Nothing here yet" else null }
        r.onFailure { msg = it.message }
    }
    Column(Modifier.fillMaxSize()) {
        Button(onClick = { reload++ }, modifier = Modifier.padding(8.dp)) { Text("Refresh") }
        msg?.let { Text(it, Modifier.padding(16.dp)) }
        LazyColumn(Modifier.weight(1f)) {
            items(items) { o ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        if (payments) {
                            Text(o.optString("applicantName"), style = MaterialTheme.typography.titleMedium)
                            Text("${o.optString("methodName")} · ${o.optString("amount")} · TrxID ${o.optString("transactionId")}")
                            Text("From ${o.optString("senderNumber")} · Status: ${o.optString("status")}")
                            Row {
                                TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { Api.setPaymentStatus(site, o.optString("id"), "verified") }; reload++ } }) { Text("Approve") }
                                TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { Api.setPaymentStatus(site, o.optString("id"), "rejected") }; reload++ } }) { Text("Reject") }
                            }
                        } else {
                            Text(o.optString("fullName"), style = MaterialTheme.typography.titleMedium)
                            Text("Passport ${o.optString("passportNumber")}")
                            Text("ID ${o.optString("id")}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
