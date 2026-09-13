package com.proxyplatform.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale

private data class Product(val id: String, val name: String, val description: String, val protocol: String, val country: String, val city: String, val ipType: String, val daily: String, val monthly: String, val featured: Boolean)
private data class Profile(val email: String, val name: String, val role: String, val verified: Boolean)
private data class Subscription(val status: String, val expires: String, val product: String, val protocol: String)
private enum class Screen { MARKET, SUBSCRIPTIONS, PROXY, PROFILE }

private val ProxyColors = darkColorScheme(primary = Color(0xFF8EA7FF), onPrimary = Color(0xFF0B1534), primaryContainer = Color(0xFF283D83), onPrimaryContainer = Color(0xFFDCE2FF), secondary = Color(0xFF72D8C8), onSecondary = Color(0xFF003731), secondaryContainer = Color(0xFF14524B), onSecondaryContainer = Color(0xFF9CF2E2), background = Color(0xFF07111F), onBackground = Color(0xFFE7ECF7), surface = Color(0xFF0D1A2B), onSurface = Color(0xFFE7ECF7), surfaceVariant = Color(0xFF1A2A40), onSurfaceVariant = Color(0xFFB9C4D8), error = Color(0xFFFFB4AB))

@Composable private fun ProxyTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = ProxyColors, content = content) }

private class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("proxy_session", Context.MODE_PRIVATE)
    var accessToken: String? get() = prefs.getString("access_token", null); set(value) { prefs.edit().putString("access_token", value).apply() }
    var refreshToken: String? get() = prefs.getString("refresh_token", null); set(value) { prefs.edit().putString("refresh_token", value).apply() }
    fun clear() = prefs.edit().clear().apply()
}

private class ApiClient(context: Context) {
    private val store = SessionStore(context)
    private val client = OkHttpClient.Builder().connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS).readTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()
    private fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val builder = Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + path).header("Accept", "application/json")
        store.accessToken?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        if (body != null) builder.method(method, body.toString().toRequestBody(jsonType)) else builder.method(method, null)
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val result = if (raw.isBlank()) JSONObject() else JSONObject(raw)
            if (!response.isSuccessful) throw IllegalStateException(result.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: "Request failed (${response.code})")
            return result
        }
    }
    fun login(email: String, password: String) { val d = request("/auth/login", "POST", JSONObject().put("email", email.trim()).put("password", password)).getJSONObject("data"); store.accessToken = d.getString("accessToken"); store.refreshToken = d.optString("refreshToken") }
    fun register(email: String, password: String, name: String) { request("/auth/register", "POST", JSONObject().put("email", email.trim()).put("password", password).put("fullName", name.trim())) }
    fun products(): List<Product> { val a = request("/products").getJSONArray("data"); return (0 until a.length()).map { val x = a.getJSONObject(it); Product(x.getString("id"), x.getString("name"), x.optString("description"), x.optString("protocol").uppercase(Locale.ROOT), "${x.optString("country_name")} (${x.optString("country_code")})", x.optString("city"), x.optString("ip_type"), "${x.optDouble("price_daily")} USD/day", "${x.optDouble("price_monthly")} USD/month", x.optBoolean("is_featured")) } }
    fun profile(): Profile { val x = request("/me").getJSONObject("data"); return Profile(x.optString("email"), x.optString("full_name", "No name"), x.optString("role", "user"), x.optBoolean("is_email_verified")) }
    fun subscriptions(): List<Subscription> { val a = request("/me/subscriptions").getJSONArray("data"); return (0 until a.length()).map { val x = a.getJSONObject(it); val p = x.optJSONObject("proxy_products"); Subscription(x.optString("status"), x.optString("expires_at"), p?.optString("name", "Proxy") ?: "Proxy", p?.optString("protocol", "") ?: "") } }
    fun loggedIn() = store.accessToken != null
    fun logout() = store.clear()
}

private class AppViewModel(private val api: ApiClient) : ViewModel() {
    var loggedIn by mutableStateOf(api.loggedIn()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var products by mutableStateOf<List<Product>>(emptyList()); private set
    var profile by mutableStateOf<Profile?>(null); private set
    var subscriptions by mutableStateOf<List<Subscription>>(emptyList()); private set
    private fun readableError(error: Exception) = error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
    fun login(email: String, password: String) { loading = true; error = null; viewModelScope.launch(Dispatchers.IO) { try { api.login(email, password); withContext(Dispatchers.Main) { loggedIn = true; loading = false; loadProducts() } } catch (e: Exception) { withContext(Dispatchers.Main) { error = readableError(e); loading = false } } } }
    fun register(email: String, password: String, name: String, done: () -> Unit) { loading = true; error = null; viewModelScope.launch(Dispatchers.IO) { try { api.register(email, password, name); withContext(Dispatchers.Main) { loading = false; error = "Account created. Sign in to continue."; done() } } catch (e: Exception) { withContext(Dispatchers.Main) { error = readableError(e); loading = false } } } }
    fun loadProducts() = viewModelScope.launch(Dispatchers.IO) { try { val value = api.products(); withContext(Dispatchers.Main) { products = value } } catch (e: Exception) { withContext(Dispatchers.Main) { error = readableError(e) } } }
    fun loadProfile() = viewModelScope.launch(Dispatchers.IO) { try { val value = api.profile(); withContext(Dispatchers.Main) { profile = value } } catch (e: Exception) { withContext(Dispatchers.Main) { error = readableError(e) } } }
    fun loadSubscriptions() = viewModelScope.launch(Dispatchers.IO) { try { val value = api.subscriptions(); withContext(Dispatchers.Main) { subscriptions = value } } catch (e: Exception) { withContext(Dispatchers.Main) { error = readableError(e) } } }
    fun logout() { api.logout(); loggedIn = false; products = emptyList(); profile = null; subscriptions = emptyList(); error = null }
    fun clearError() { error = null }
}

private class AppViewModelFactory(private val api: ApiClient) : androidx.lifecycle.ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(api) as T }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); val api = ApiClient(this); setContent { ProxyTheme { val vm: AppViewModel = viewModel(factory = AppViewModelFactory(api)); ProxyPlatformApp(vm) } } }
}

@Composable private fun ProxyPlatformApp(vm: AppViewModel) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { if (vm.loggedIn) MainShell(vm) else AuthScreen(vm) } }

@Composable private fun BrandMark(size: Int = 54) { Box(Modifier.size(size.dp).background(Brush.linearGradient(listOf(Color(0xFF718BFF), Color(0xFF42D5C1))), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("P", color = Color(0xFF07111F), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) } }

@Composable private fun AuthScreen(vm: AppViewModel) {
    var register by remember { mutableStateOf(false) }; var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var showPassword by remember { mutableStateOf(false) }
    val canSubmit = !vm.loading && email.contains("@") && password.length >= 8 && (!register || name.isNotBlank())
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1830), Color(0xFF07111F))))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 44.dp), verticalArrangement = Arrangement.Center) {
            BrandMark(); Spacer(Modifier.height(22.dp)); Text("Proxy Platform", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(if (register) "Create a secure account and manage your proxy connections." else "A faster, cleaner way to route your connection.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge); Spacer(Modifier.height(26.dp))
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { TextButton(onClick = { register = false; vm.clearError() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColors(containerColor = if (!register) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, contentColor = if (!register) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)) { Text("Sign in", fontWeight = FontWeight.SemiBold) }; TextButton(onClick = { register = true; vm.clearError() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColors(containerColor = if (register) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, contentColor = if (register) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)) { Text("Create account", fontWeight = FontWeight.SemiBold) } }
            Spacer(Modifier.height(22.dp)); if (register) { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true); Spacer(Modifier.height(12.dp)) }; OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email address") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)); Spacer(Modifier.height(12.dp)); OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") } }); if (!register) Text("Use at least 8 characters.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            vm.error?.let { message -> Card(Modifier.fillMaxWidth().padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = if (message.startsWith("Account created")) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer)) { Text(message, Modifier.padding(14.dp), color = if (message.startsWith("Account created")) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer) } }
            Spacer(Modifier.height(20.dp)); Button(onClick = { if (register) vm.register(email, password, name) { register = false } else vm.login(email, password) }, enabled = canSubmit, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) { if (vm.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (register) "Create account" else "Continue", fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(18.dp)); Text("Your credentials stay protected and are only used to authenticate with the service.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable private fun MainShell(vm: AppViewModel) {
    var screen by remember { mutableStateOf(Screen.MARKET) }; var selectedProduct by remember { mutableStateOf<Product?>(null) }; if (selectedProduct != null) { ProductDetails(selectedProduct!!, { selectedProduct = null }); return }; LaunchedEffect(screen) { vm.clearError(); when (screen) { Screen.MARKET -> vm.loadProducts(); Screen.PROFILE -> vm.loadProfile(); Screen.SUBSCRIPTIONS -> vm.loadSubscriptions(); Screen.PROXY -> Unit } }; val title = when (screen) { Screen.MARKET -> "Marketplace"; Screen.SUBSCRIPTIONS -> "My subscriptions"; Screen.PROXY -> "Device proxy"; Screen.PROFILE -> "My profile" }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopAppBar(title = { Column { Text(title, fontWeight = FontWeight.Bold); Text("Proxy Platform", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, actions = { StatusPill("Secure", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }, colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }, bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surface) { NavigationBarItem(screen == Screen.MARKET, { screen = Screen.MARKET }, icon = { Text("⌂") }, label = { Text("Market") }, colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer, indicatorColor = MaterialTheme.colorScheme.primaryContainer)); NavigationBarItem(screen == Screen.SUBSCRIPTIONS, { screen = Screen.SUBSCRIPTIONS }, icon = { Text("▣") }, label = { Text("Plans") }); NavigationBarItem(screen == Screen.PROXY, { screen = Screen.PROXY }, icon = { Text("⚡") }, label = { Text("Proxy") }); NavigationBarItem(screen == Screen.PROFILE, { screen = Screen.PROFILE }, icon = { Text("●") }, label = { Text("Profile") }) } }) { padding -> when (screen) { Screen.MARKET -> Marketplace(vm, padding) { selectedProduct = it }; Screen.SUBSCRIPTIONS -> Subscriptions(vm, padding); Screen.PROXY -> ProxyScreen(padding); Screen.PROFILE -> ProfileScreen(vm, padding) } }
}

@Composable private fun StatusPill(text: String, background: Color, foreground: Color) { Surface(color = background, shape = RoundedCornerShape(50), modifier = Modifier.padding(end = 16.dp)) { Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Box(Modifier.size(6.dp).background(foreground, CircleShape)); Text(text, color = foreground, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) } } }

@Composable private fun ProxyScreen(padding: PaddingValues) {
    val context = LocalContext.current; val locationController = remember { ProxyLocationController(context) }; DisposableEffect(Unit) { onDispose { locationController.close() } }
    var protocol by remember { mutableStateOf("socks5") }; var host by remember { mutableStateOf("") }; var port by remember { mutableStateOf("") }; var auth by remember { mutableStateOf(false) }; var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var running by remember { mutableStateOf(ProxyVpnService.isRunning(context)) }; var starting by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; var mockLocation by remember { mutableStateOf(false) }; var locationMode by remember { mutableStateOf("auto") }; var latitude by remember { mutableStateOf("") }; var longitude by remember { mutableStateOf("") }
    fun startLocation() { if (!mockLocation) return; runCatching { if (locationMode == "auto") locationController.startAuto(protocol, host, port.toInt(), username, password) else locationController.startManual(latitude.toDouble(), longitude.toDouble()) }.onFailure { error = "Proxy started, but mock location could not start: ${it.message ?: "check Developer options"}" } }
    fun launchTunnel() { error = null; starting = true; ProxyVpnService.clearLastError(context); runCatching { startProxyService(context, protocol, host, port, username, password); startLocation() }.onFailure { starting = false; error = it.message ?: "Could not start the proxy service." } }
    val prepareVpn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> if (result.resultCode == Activity.RESULT_OK) launchTunnel() else error = "Android VPN permission is required to route device traffic." }
    fun requestStart() { val parsedPort = port.toIntOrNull(); if (host.isBlank() || parsedPort !in 1..65535 || (auth && username.isBlank())) { error = "Enter a valid host, port, and authentication details."; return }; val manualLatitude = latitude.toDoubleOrNull(); val manualLongitude = longitude.toDoubleOrNull(); if (mockLocation && locationMode == "manual" && (manualLatitude == null || manualLongitude == null || manualLatitude !in -90.0..90.0 || manualLongitude !in -180.0..180.0)) { error = "Enter valid manual latitude and longitude."; return }; val intent = VpnService.prepare(context); if (intent != null) prepareVpn.launch(intent) else launchTunnel() }
    LaunchedEffect(starting) { if (!starting) return@LaunchedEffect; repeat(40) { delay(250); if (ProxyVpnService.isRunning(context)) { running = true; starting = false; return@LaunchedEffect }; ProxyVpnService.lastError(context)?.let { error = it; starting = false; return@LaunchedEffect } }; if (starting) { starting = false; error = "The tunnel did not start. Check the proxy details and try again." } }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) { Column(Modifier.weight(1f)) { Text(if (running) "Your tunnel is active" else if (starting) "Starting secure tunnel" else "Ready to connect", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer); Text(if (running) "Device traffic is routed through your proxy." else "Configure a proxy endpoint and connect in seconds.", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .82f)) }; Text(if (running) "ON" else if (starting) "…" else "OFF", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Black) } } } }
        item { SectionTitle("Connection details", "Your proxy credentials remain on this device.") }; item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(protocol == "socks5", { protocol = "socks5" }, label = { Text("SOCKS5") }); FilterChip(protocol == "http", { protocol = "http" }, label = { Text("HTTP") }) } }; item { OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), label = { Text("Proxy host or IP") }, singleLine = true, enabled = !running && !starting) }; item { OutlinedTextField(port, { port = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Port") }, singleLine = true, enabled = !running && !starting, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }; item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Checkbox(auth, { auth = it }, enabled = !running && !starting); Text("Proxy requires authentication") } }
        if (auth) { item { OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true, enabled = !running && !starting) }; item { OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !running && !starting) } }
        item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Text("Optional mock location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Match the proxy exit location for supported apps. Android requires selecting this app under Developer options.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Checkbox(mockLocation, { mockLocation = it }, enabled = !running && !starting); Text("Enable mock location") }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(locationMode == "auto", { locationMode = "auto" }, label = { Text("Automatic") }); FilterChip(locationMode == "manual", { locationMode = "manual" }, label = { Text("Manual") }) }; if (locationMode == "manual") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(latitude, { latitude = it }, Modifier.weight(1f), label = { Text("Latitude") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); OutlinedTextField(longitude, { longitude = it }, Modifier.weight(1f), label = { Text("Longitude") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) } }; OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }, Modifier.fillMaxWidth()) { Text("Open Developer options") } } } }
        item { error?.let { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(it, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer) } } }; item { if (running) Button(onClick = { locationController.stop(); context.startService(Intent(context, ProxyVpnService::class.java).setAction(ProxyVpnService.ACTION_STOP)); running = false; starting = false }, Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), shape = RoundedCornerShape(16.dp)) { Text("Stop proxy", fontWeight = FontWeight.Bold) } else Button(onClick = ::requestStart, Modifier.fillMaxWidth().height(54.dp), enabled = !starting && host.isNotBlank() && port.isNotBlank(), shape = RoundedCornerShape(16.dp)) { if (starting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Start device proxy", fontWeight = FontWeight.Bold) } }; item { Text(if (running) "Connected • traffic is being routed through the configured proxy" else if (starting) "Waiting for the secure tunnel to come online…" else "Not connected", color = if (running) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun startProxyService(context: Context, protocol: String, host: String, port: String, username: String, password: String) { val intent = Intent(context, ProxyVpnService::class.java).apply { action = ProxyVpnService.ACTION_START; putExtra(ProxyVpnService.EXTRA_PROTOCOL, protocol); putExtra(ProxyVpnService.EXTRA_HOST, host.trim()); putExtra(ProxyVpnService.EXTRA_PORT, port.toInt()); putExtra(ProxyVpnService.EXTRA_USERNAME, username); putExtra(ProxyVpnService.EXTRA_PASSWORD, password) }; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent) else context.startService(intent) }
@Composable private fun SectionTitle(title: String, subtitle: String) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable private fun Marketplace(vm: AppViewModel, padding: PaddingValues, onProductClick: (Product) -> Unit) {
    var featured by remember { mutableStateOf(false) }; val visibleProducts = vm.products.filter { !featured || it.featured }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Find your ideal route", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Reliable proxy plans with clear locations, protocols, and pricing.", color = MaterialTheme.colorScheme.onSurfaceVariant); Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Text("${vm.products.size} plans available", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold); FilterChip(featured, { featured = !featured }, label = { Text("Featured only") }) } } } }; vm.error?.let { message -> item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(message, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer) } } }; if (visibleProducts.isEmpty()) item { EmptyState(if (vm.error == null) "No plans available yet" else "We could not load plans", if (vm.error == null) "New proxy plans will appear here when they are published." else "Check your connection and try again.") }; items(visibleProducts, key = { it.id }) { ProductCard(it, onProductClick) } }
}

@Composable private fun EmptyState(title: String, subtitle: String) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("○", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(subtitle, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun ProductCard(p: Product, onClick: (Product) -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); if (p.featured) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) { Text("FEATURED", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) } }; Text(p.description.ifBlank { "A reliable proxy route for your everyday connection." }, color = MaterialTheme.colorScheme.onSurfaceVariant); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); Text("${p.country}  •  ${p.city}", fontWeight = FontWeight.SemiBold); Text("${p.protocol}  •  ${p.ipType}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text(p.daily, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(p.monthly, fontWeight = FontWeight.Bold) }; Button(onClick = { onClick(p) }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) { Text("View details") } } } } }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable private fun ProductDetails(p: Product, onBack: () -> Unit) { Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopAppBar(title = { Text(p.name, fontWeight = FontWeight.Bold) }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }, colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }) { padding -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("${p.country}  •  ${p.city}", color = MaterialTheme.colorScheme.onPrimaryContainer); Text(p.protocol, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold) } }; Text(p.description.ifBlank { "Reliable proxy access configured for simple, consistent routing." }, style = MaterialTheme.typography.bodyLarge); DetailRow("Protocol", p.protocol); DetailRow("Location", "${p.country}, ${p.city}"); DetailRow("IP type", p.ipType); DetailRow("Daily plan", p.daily); DetailRow("Monthly plan", p.monthly); Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Purchase plan", fontWeight = FontWeight.Bold) }; Text("Purchasing will be available when billing is connected.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } } }
@Composable private fun DetailRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.SemiBold) } }
@Composable private fun Subscriptions(vm: AppViewModel, padding: PaddingValues) { LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { SectionTitle("Your plans", "Keep track of active proxy subscriptions.") }; if (vm.subscriptions.isEmpty()) item { EmptyState("No active subscriptions", "Your purchased proxy plans will appear here.") }; items(vm.subscriptions) { s -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(s.product, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); StatusPill(s.status.uppercase(Locale.ROOT), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }; Text("${s.protocol}  •  Expires ${s.expires}", color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
@Composable private fun ProfileScreen(vm: AppViewModel, padding: PaddingValues) { LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { SectionTitle("Account", "Manage your profile and session.") }; vm.profile?.let { p -> item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) { Box(Modifier.size(54.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) { Text(p.name.take(1).uppercase(Locale.ROOT), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }; Column { Text(p.name.ifBlank { "Proxy user" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(p.email, color = MaterialTheme.colorScheme.onSurfaceVariant) } }; HorizontalDivider(Modifier.padding(vertical = 8.dp)); DetailRow("Role", p.role); DetailRow("Verification", if (p.verified) "Email verified" else "Pending") } } } }; vm.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }; item { OutlinedButton(onClick = { vm.logout() }, Modifier.fillMaxWidth().height(52.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error), shape = RoundedCornerShape(16.dp)) { Text("Sign out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } } } }
