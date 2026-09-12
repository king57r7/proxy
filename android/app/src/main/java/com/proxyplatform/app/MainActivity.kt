package com.proxyplatform.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private data class Product(val id: String, val name: String, val description: String, val protocol: String, val country: String, val city: String, val ipType: String, val daily: String, val monthly: String, val featured: Boolean)
private data class Profile(val email: String, val name: String, val role: String, val verified: Boolean)
private data class Subscription(val status: String, val expires: String, val product: String, val protocol: String)
private enum class Screen { MARKET, SUBSCRIPTIONS, PROFILE }

private class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("proxy_session", Context.MODE_PRIVATE)
    var accessToken: String? get() = prefs.getString("access_token", null) ; set(value) { prefs.edit().putString("access_token", value).apply() }
    var refreshToken: String? get() = prefs.getString("refresh_token", null) ; set(value) { prefs.edit().putString("refresh_token", value).apply() }
    fun clear() = prefs.edit().clear().apply()
}

private class ApiClient(context: Context) {
    private val store = SessionStore(context)
    private val client = OkHttpClient()
    private val jsonType = "application/json".toMediaType()
    private fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val builder = Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + path).header("Accept", "application/json")
        store.accessToken?.let { builder.header("Authorization", "Bearer $it") }
        if (body != null) builder.method(method, body.toString().toRequestBody(jsonType)) else builder.method(method, null)
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val result = if (raw.isBlank()) JSONObject() else JSONObject(raw)
            if (!response.isSuccessful) throw IllegalStateException(result.optJSONObject("error")?.optString("message") ?: "Request failed (${response.code})")
            return result
        }
    }
    fun login(email: String, password: String) { val d = request("/auth/login", "POST", JSONObject().put("email", email).put("password", password)).getJSONObject("data"); store.accessToken = d.getString("accessToken"); store.refreshToken = d.optString("refreshToken") }
    fun register(email: String, password: String, name: String) { request("/auth/register", "POST", JSONObject().put("email", email).put("password", password).put("fullName", name)) }
    fun products(): List<Product> { val a = request("/products").getJSONArray("data"); return (0 until a.length()).map { val x = a.getJSONObject(it); Product(x.getString("id"), x.getString("name"), x.optString("description"), x.optString("protocol").uppercase(), "${x.optString("country_name")} (${x.optString("country_code")})", x.optString("city"), x.optString("ip_type"), "${x.optDouble("price_daily")} USD/day", "${x.optDouble("price_monthly")} USD/month", x.optBoolean("is_featured")) } }
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
    fun login(email: String, password: String) = run { loading = true; error = null; viewModelScope.launch(Dispatchers.IO) { try { api.login(email, password); withContext(Dispatchers.Main) { loggedIn = true; loading = false; loadProducts() } } catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message; loading = false } } } }
    fun register(email: String, password: String, name: String, done: () -> Unit) = viewModelScope.launch(Dispatchers.IO) { try { api.register(email, password, name); withContext(Dispatchers.Main) { error = "Account created. Sign in to continue."; done() } } catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message } } }
    fun loadProducts() = viewModelScope.launch(Dispatchers.IO) { try { val value = api.products(); withContext(Dispatchers.Main) { products = value } } catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message } } }
    fun loadProfile() = viewModelScope.launch(Dispatchers.IO) { try { val value = api.profile(); withContext(Dispatchers.Main) { profile = value } } catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message } } }
    fun loadSubscriptions() = viewModelScope.launch(Dispatchers.IO) { try { val value = api.subscriptions(); withContext(Dispatchers.Main) { subscriptions = value } } catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message } } }
    fun logout() { api.logout(); loggedIn = false; products = emptyList(); profile = null; subscriptions = emptyList() }
    fun clearError() { error = null }
}

private class AppViewModelFactory(private val api: ApiClient) : androidx.lifecycle.ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(api) as T }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); val api = ApiClient(this); setContent { val vm: AppViewModel = viewModel(factory = AppViewModelFactory(api)); ProxyPlatformApp(vm) } }
}

@Composable private fun ProxyPlatformApp(vm: AppViewModel) { Surface(Modifier.fillMaxSize()) { if (vm.loggedIn) MainShell(vm) else AuthScreen(vm) } }

@Composable private fun AuthScreen(vm: AppViewModel) {
    var register by remember { mutableStateOf(false) }; var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Proxy Platform", style = MaterialTheme.typography.headlineLarge); Text(if (register) "Create your account" else "Sign in to your account", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(24.dp))
        if (register) { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Full name") }); Spacer(Modifier.height(10.dp)) }
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }); Spacer(Modifier.height(10.dp)); OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation()); Spacer(Modifier.height(16.dp))
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(10.dp)) }
        Button(enabled = !vm.loading && email.isNotBlank() && password.length >= 8, onClick = { if (register) vm.register(email, password, name) { register = false } else vm.login(email, password) }, Modifier.fillMaxWidth()) { if (vm.loading) CircularProgressIndicator() else Text(if (register) "Create account" else "Sign in") }
        TextButton(onClick = { register = !register; vm.clearError() }) { Text(if (register) "Already have an account? Sign in" else "New here? Create an account") }
    }
}

@Composable private fun MainShell(vm: AppViewModel) {
    var screen by remember { mutableStateOf(Screen.MARKET) }; var selectedProduct by remember { mutableStateOf<Product?>(null) }
    if (selectedProduct != null) { ProductDetails(selectedProduct!!, { selectedProduct = null }); return }
    LaunchedEffect(screen) { when (screen) { Screen.MARKET -> vm.loadProducts(); Screen.PROFILE -> vm.loadProfile(); Screen.SUBSCRIPTIONS -> vm.loadSubscriptions() } }
    Scaffold(topBar = { TopAppBar(title = { Text(when (screen) { Screen.MARKET -> "Marketplace"; Screen.SUBSCRIPTIONS -> "My subscriptions"; Screen.PROFILE -> "My profile" }) }) }, bottomBar = { NavigationBar { NavigationBarItem(screen == Screen.MARKET, { screen = Screen.MARKET }, icon = {}, label = { Text("Market") }); NavigationBarItem(screen == Screen.SUBSCRIPTIONS, { screen = Screen.SUBSCRIPTIONS }, icon = {}, label = { Text("Plans") }); NavigationBarItem(screen == Screen.PROFILE, { screen = Screen.PROFILE }, icon = {}, label = { Text("Profile") }) } }) { padding -> when (screen) { Screen.MARKET -> Marketplace(vm, padding) { selectedProduct = it }; Screen.SUBSCRIPTIONS -> Subscriptions(vm, padding); Screen.PROFILE -> ProfileScreen(vm, padding) } }
}

@Composable private fun Marketplace(vm: AppViewModel, padding: PaddingValues, onProductClick: (Product) -> Unit) { var featured by remember { mutableStateOf(false) }; Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) { Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Choose a proxy plan", style = MaterialTheme.typography.titleLarge); FilterChip(featured, { featured = !featured }, label = { Text("Featured") }) }; if (vm.error != null) Text(vm.error!!, color = MaterialTheme.colorScheme.error); LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) { items(vm.products.filter { !featured || it.featured }) { ProductCard(it, onProductClick) } } } }
@Composable private fun ProductCard(p: Product, onClick: (Product) -> Unit) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(p.name, style = MaterialTheme.typography.titleMedium); if (p.featured) Text("FEATURED", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }; Text(p.description); Text("${p.country} • ${p.city} • ${p.protocol}"); Text("${p.ipType} • ${p.daily} • ${p.monthly}", style = MaterialTheme.typography.labelLarge); OutlinedButton(onClick = { onClick(p) }) { Text("View details") } } } }
@Composable private fun ProductDetails(p: Product, onBack: () -> Unit) { Scaffold(topBar = { TopAppBar(title = { Text(p.name) }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text(p.name, style = MaterialTheme.typography.headlineSmall); Text(p.description, style = MaterialTheme.typography.bodyLarge); Text("Location: ${p.country}, ${p.city}"); Text("Protocol: ${p.protocol}"); Text("Type: ${p.ipType}"); Text("Daily: ${p.daily}"); Text("Monthly: ${p.monthly}"); Button(onClick = {}, Modifier.fillMaxWidth()) { Text("Purchase coming soon") } } } }
@Composable private fun Subscriptions(vm: AppViewModel, padding: PaddingValues) { LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { if (vm.subscriptions.isEmpty()) item { Text("No active subscriptions yet.") }; items(vm.subscriptions) { s -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(s.product, style = MaterialTheme.typography.titleMedium); Text("${s.protocol} • ${s.status}"); Text("Expires: ${s.expires}") } } } } }
@Composable private fun ProfileScreen(vm: AppViewModel, padding: PaddingValues) { Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { vm.profile?.let { p -> Text(p.name, style = MaterialTheme.typography.headlineSmall); Text(p.email); Text("Role: ${p.role}"); Text(if (p.verified) "Email verified" else "Email verification pending", color = if (p.verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }; Spacer(Modifier.height(8.dp)); Button(onClick = { vm.logout() }, Modifier.fillMaxWidth()) { Text("Sign out") } } }
