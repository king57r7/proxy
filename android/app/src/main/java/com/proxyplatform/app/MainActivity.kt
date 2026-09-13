package com.proxyplatform.app

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private data class Product(val id: String, val name: String, val description: String, val protocol: String, val country: String, val city: String, val ipType: String, val daily: String, val monthly: String, val featured: Boolean)
private data class Profile(val email: String, val name: String, val role: String, val verified: Boolean)
private data class Subscription(val status: String, val expires: String, val product: String, val protocol: String)
private enum class Screen { MARKET, SUBSCRIPTIONS, PROXY, PROFILE }

private val ProxyColors = darkColorScheme(primary = Color(0xFF8EA7FF), primaryContainer = Color(0xFF283D83), onPrimaryContainer = Color(0xFFDCE2FF), secondary = Color(0xFF72D8C8), secondaryContainer = Color(0xFF14524B), onSecondaryContainer = Color(0xFF9CF2E2), background = Color(0xFF07111F), surface = Color(0xFF0D1A2B), surfaceVariant = Color(0xFF1A2A40), onSurfaceVariant = Color(0xFFB9C4D8), error = Color(0xFFFFB4AB))

@Composable private fun ProxyTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = ProxyColors, content = content) }

private class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("proxy_session", Context.MODE_PRIVATE)
    var accessToken: String? get() = prefs.getString("access_token", null); set(value) { prefs.edit().putString("access_token", value).apply() }
    var refreshToken: String? get() = prefs.getString("refresh_token", null); set(value) { prefs.edit().putString("refresh_token", value).apply() }
    fun clear() = prefs.edit().clear().apply()
}

private class ApiClient(context: Context) {
    private val store = SessionStore(context)
    private val client = OkHttpClient()
    private val jsonType = "application/json".toMediaType()
    private fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val builder = Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + path).header("Accept", "application/json")
        store.accessToken?.let { builder.header("Authorization", "Bearer " + it) }
        if (body != null) builder.method(method, body.toString().toRequestBody(jsonType)) else builder.method(method, null)
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty(); val result = if (raw.isBlank()) JSONObject() else JSONObject(raw)
            if (!response.isSuccessful) throw IllegalStateException(result.optJSONObject("error")?.optString("message") ?: "Request failed (" + response.code + ")")
            return result
        }
    }
    fun login(email: String, password: String) { val d = request("/auth/login", "POST", JSONObject().put("email", email.trim()).put("password", password)).getJSONObject("data"); store.accessToken = d.getString("accessToken"); store.refreshToken = d.optString("refreshToken") }
    fun register(email: String, password: String, name: String) { request("/auth/register", "POST", JSONObject().put("email", email.trim()).put("password", password).put("fullName", name.trim())) }
    fun products(): List<Product> { val a = request("/products").getJSONArray("data"); return (0 until a.length()).map { val x = a.getJSONObject(it); Product(x.getString("id"), x.getString("name"), x.optString("description"), x.optString("protocol").uppercase(), x.optString("country_name") + " (" + x.optString("country_code") + ")", x.optString("city"), x.optString("ip_type"), x.optDouble("price_daily").toString() + " USD/day", x.optDouble("price_monthly").toString() + " USD/month", x.optBoolean("is_featured")) } }
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
    fun login(email: String, password: String) { loading = true; error = null; viewModelScope.launch(Dispatchers.IO) { try { api.login(email, password); withContext(Dispatchers.Main) { loggedIn = true; loading = false; loadProducts() } } catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message ?: "Unable to sign in"; loading = false } } } }
    fun register(email: String, password: String, name: String, done: () -> Unit) { loading = true; error = null; viewModelScope.launch(Dispatchers.IO) { try { api.register(email, password, name); withContext(Dispatchers.Main) { loading = false; error = "Account created. Sign in to continue."; done() } } catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message ?: "Unable to create account"; loading = false } } } }
    fun loadProducts() = viewModelScope.launch(Dispatchers.IO) { try { val value = api.products(); withContext(Dispatchers.Main) { products = value } } catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message ?: "Unable to load plans" } } }
    fun loadProfile() = viewModelScope.launch(Dispatchers.IO) { try { val value = api.profile(); withContext(Dispatchers.Main) { profile = value } } catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message ?: "Unable to load profile" } } }
    fun loadSubscriptions() = viewModelScope.launch(Dispatchers.IO) { try { val value = api.subscriptions(); withContext(Dispatchers.Main) { subscriptions = value } } catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message ?: "Unable to load subscriptions" } } }
    fun logout() { api.logout(); loggedIn = false; products = emptyList(); profile = null; subscriptions = emptyList(); error = null }
    fun clearError() { error = null }
}
private class AppViewModelFactory(private val api: ApiClient) : androidx.lifecycle.ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(api) as T }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); val api = ApiClient(this); setContent { ProxyTheme { val vm: AppViewModel = viewModel(factory = AppViewModelFactory(api)); ProxyPlatformApp(vm) } } }
}

@Composable private fun ProxyPlatformApp(vm: AppViewModel) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { if (vm.loggedIn) MainShell(vm) else AuthScreen(vm) } }
@Composable private fun BrandMark() { Box(Modifier.size(56.dp).background(Brush.linearGradient(listOf(Color(0xFF718BFF), Color(0xFF42D5C1))), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("P", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF07111F), fontWeight = FontWeight.Black) } }

@Composable private fun AuthScreen(vm: AppViewModel) {
    var register by remember { mutableStateOf(false) }; var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1830), Color(0xFF07111F))))) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) { BrandMark(); Spacer(Modifier.height(20.dp)); Text("Proxy Platform", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(if (register) "Create a secure account and manage your proxy connections." else "A faster, cleaner way to route your connection.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(24.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { TextButton(onClick = { register = false; vm.clearError() }) { Text("Sign in", fontWeight = if (!register) FontWeight.Bold else FontWeight.Normal) }; TextButton(onClick = { register = true; vm.clearError() }) { Text("Create account", fontWeight = if (register) FontWeight.Bold else FontWeight.Normal) } }; Spacer(Modifier.height(12.dp)); if (register) { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true); Spacer(Modifier.height(10.dp)) }; OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email address") }, singleLine = true); Spacer(Modifier.height(10.dp)); OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true); vm.error?.let { Text(it, Modifier.padding(top = 12.dp), color = if (it.startsWith("Account created")) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error) }; Spacer(Modifier.height(18.dp)); Button(onClick = { if (register) vm.register(email, password, name) { register = false } else vm.login(email, password) }, enabled = !vm.loading && email.isNotBlank() && password.length >= 8 && (!register || name.isNotBlank()), Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp)) { if (vm.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (register) "Create account" else "Continue", fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(16.dp)); Text("Your credentials are used only to authenticate with the service.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable private fun MainShell(vm: AppViewModel) {
    var screen by remember { mutableStateOf(Screen.MARKET) }; var selectedProduct by remember { mutableStateOf<Product?>(null) }; if (selectedProduct != null) { ProductDetails(selectedProduct!!, { selectedProduct = null }); return }; LaunchedEffect(screen) { vm.clearError(); when (screen) { Screen.MARKET -> vm.loadProducts(); Screen.PROFILE -> vm.loadProfile(); Screen.SUBSCRIPTIONS -> vm.loadSubscriptions(); Screen.PROXY -> Unit } }
    val title = when (screen) { Screen.MARKET -> "Marketplace"; Screen.SUBSCRIPTIONS -> "My subscriptions"; Screen.PROXY -> "Device proxy"; Screen.PROFILE -> "My profile" }
    Scaffold(topBar = { TopAppBar(title = { Text(title, fontWeight = FontWeight.Bold) }) }, bottomBar = { NavigationBar { NavigationBarItem(screen == Screen.MARKET, { screen = Screen.MARKET }, icon = { Text("⌂") }, label = { Text("Market") }); NavigationBarItem(screen == Screen.SUBSCRIPTIONS, { screen = Screen.SUBSCRIPTIONS }, icon = { Text("▣") }, label = { Text("Plans") }); NavigationBarItem(screen == Screen.PROXY, { screen = Screen.PROXY }, icon = { Text("⚡") }, label = { Text("Proxy") }); NavigationBarItem(screen == Screen.PROFILE, { screen = Screen.PROFILE }, icon = { Text("●") }, label = { Text("Profile") }) } }) { padding -> when (screen) { Screen.MARKET -> Marketplace(vm, padding) { selectedProduct = it }; Screen.SUBSCRIPTIONS -> Subscriptions(vm, padding); Screen.PROXY -> ProxyScreen(padding); Screen.PROFILE -> ProfileScreen(vm, padding) } }
}

@Composable private fun ProxyScreen(padding: PaddingValues) {
    val context = LocalContext.current; val locationController = remember { ProxyLocationController(context) }; DisposableEffect(Unit) { onDispose { locationController.close() } }
    var protocol by remember { mutableStateOf("socks5") }; var host by remember { mutableStateOf("") }; var port by remember { mutableStateOf("") }; var auth by remember { mutableStateOf(false) }; var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var running by remember { mutableStateOf(ProxyVpnService.isRunning(context)) }; var starting by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; var mockLocation by remember { mutableStateOf(false) }; var locationMode by remember { mutableStateOf("auto") }; var latitude by remember { mutableStateOf("") }; var longitude by remember { mutableStateOf("") }
    fun startLocation() { if (!mockLocation) return; runCatching { if (locationMode == "auto") locationController.startAuto(protocol, host, port.toInt(), username, password) else locationController.startManual(latitude.toDouble(), longitude.toDouble()) }.onFailure { error = "Proxy started, but mock location could not start." } }
    fun launchTunnel() { error = null; starting = true; ProxyVpnService.clearLastError(context); runCatching { startProxyService(context, protocol, host, port, username, password); startLocation() }.onFailure { starting = false; error = it.message ?: "Could not start the proxy service." } }
    val prepareVpn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> if (result.resultCode == android.app.Activity.RESULT_OK) launchTunnel() else error = "Android VPN permission is required to route device traffic." }
    fun requestStart() { val parsedPort = port.toIntOrNull(); if (host.isBlank() || parsedPort !in 1..65535 || (auth && username.isBlank())) { error = "Enter a valid host, port, and authentication details."; return }; val intent = VpnService.prepare(context); if (intent != null) prepareVpn.launch(intent) else launchTunnel() }
    LaunchedEffect(starting) { if (starting) { delay(1200); running = ProxyVpnService.isRunning(context); if (!running) error = ProxyVpnService.lastError(context) ?: "The tunnel did not start. Check the proxy details and try again."; starting = false } }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(if (running) "Your tunnel is active" else if (starting) "Starting secure tunnel" else "Ready to connect", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(if (running) "Device traffic is routed through your proxy." else "Configure an endpoint and connect in seconds.", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }; item { Text("Connection details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }; item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(protocol == "socks5", { protocol = "socks5" }, label = { Text("SOCKS5") }); FilterChip(protocol == "http", { protocol = "http" }, label = { Text("HTTP") }) } }; item { OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), label = { Text("Proxy host or IP") }, singleLine = true, enabled = !running && !starting) }; item { OutlinedTextField(port, { port = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Port") }, singleLine = true, enabled = !running && !starting) }; item { Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Checkbox(auth, { auth = it }, enabled = !running && !starting); Text("Proxy requires authentication") } }; if (auth) { item { OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true) }; item { OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) } }; item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Optional mock location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Android requires selecting this app in Developer options.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Checkbox(mockLocation, { mockLocation = it }, enabled = !running && !starting); Text("Enable mock location") }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(locationMode == "auto", { locationMode = "auto" }, label = { Text("Automatic") }); FilterChip(locationMode == "manual", { locationMode = "manual" }, label = { Text("Manual") }) }; if (locationMode == "manual") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(latitude, { latitude = it }, Modifier.weight(1f), label = { Text("Latitude") }, singleLine = true); OutlinedTextField(longitude, { longitude = it }, Modifier.weight(1f), label = { Text("Longitude") }, singleLine = true) } }; OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }, Modifier.fillMaxWidth()) { Text("Open Developer options") } } } }; item { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }; item { if (running) Button(onClick = { locationController.stop(); context.startService(Intent(context, ProxyVpnService::class.java).setAction(ProxyVpnService.ACTION_STOP)); running = false }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp)) { Text("Stop proxy", fontWeight = FontWeight.Bold) } else Button(onClick = ::requestStart, Modifier.fillMaxWidth().height(52.dp), enabled = !starting && host.isNotBlank() && port.isNotBlank(), shape = RoundedCornerShape(15.dp)) { if (starting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Start device proxy", fontWeight = FontWeight.Bold) } }; item { Text(if (running) "Connected" else if (starting) "Waiting for the secure tunnel to come online…" else "Not connected", color = if (running) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant) } }
}
private fun startProxyService(context: Context, protocol: String, host: String, port: String, username: String, password: String) { val intent = Intent(context, ProxyVpnService::class.java).apply { action = ProxyVpnService.ACTION_START; putExtra(ProxyVpnService.EXTRA_PROTOCOL, protocol); putExtra(ProxyVpnService.EXTRA_HOST, host.trim()); putExtra(ProxyVpnService.EXTRA_PORT, port.toInt()); putExtra(ProxyVpnService.EXTRA_USERNAME, username); putExtra(ProxyVpnService.EXTRA_PASSWORD, password) }; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent) }

@Composable private fun Marketplace(vm: AppViewModel, padding: PaddingValues, onProductClick: (Product) -> Unit) { var featured by remember { mutableStateOf(false) }; val visible = vm.products.filter { !featured || it.featured }; LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text("Find your ideal route", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Reliable proxy plans with clear locations, protocols, and pricing.", color = MaterialTheme.colorScheme.onSurfaceVariant); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(vm.products.size.toString() + " plans available", color = MaterialTheme.colorScheme.secondary); FilterChip(featured, { featured = !featured }, label = { Text("Featured") }) } } } }; if (visible.isEmpty()) item { Text("No plans available yet", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }; items(visible) { ProductCard(it, onProductClick) } } }
@Composable private fun ProductCard(p: Product, onClick: (Product) -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); if (p.featured) Text("FEATURED", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall) }; Text(p.description.ifBlank { "A reliable proxy route for your everyday connection." }, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(p.country + " • " + p.city); Text(p.protocol + " • " + p.ipType, color = MaterialTheme.colorScheme.secondary); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text(p.daily, style = MaterialTheme.typography.bodySmall); Text(p.monthly, fontWeight = FontWeight.Bold) }; Button(onClick = { onClick(p) }) { Text("View details") } } } } }
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable private fun ProductDetails(p: Product, onBack: () -> Unit) { Scaffold(topBar = { TopAppBar(title = { Text(p.name) }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(p.description, style = MaterialTheme.typography.bodyLarge); Text("Location: " + p.country + ", " + p.city); Text("Protocol: " + p.protocol); Text("Type: " + p.ipType); Text("Daily: " + p.daily); Text("Monthly: " + p.monthly); Button(onClick = {}, Modifier.fillMaxWidth()) { Text("Purchase plan") } } } }
@Composable private fun Subscriptions(vm: AppViewModel, padding: PaddingValues) { LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Your plans", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }; if (vm.subscriptions.isEmpty()) item { Text("No active subscriptions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }; items(vm.subscriptions) { s -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(s.product, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(s.protocol + " • " + s.status); Text("Expires: " + s.expires, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
@Composable private fun ProfileScreen(vm: AppViewModel, padding: PaddingValues) { Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); vm.profile?.let { p -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(p.email, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Role: " + p.role); Text(if (p.verified) "Email verified" else "Email verification pending", color = if (p.verified) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error) } } }; Button(onClick = { vm.logout() }, Modifier.fillMaxWidth()) { Text("Sign out") } } }
