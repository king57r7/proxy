package com.proxyplatform.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class Product(val name: String, val location: String, val protocol: String, val price: String)
private val demoProducts = listOf(Product("Starter Proxy", "Global", "HTTPS", "$4.99/day"), Product("Premium Residential", "United States", "SOCKS5", "$9.99/day"))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { ProxyPlatformApp() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyPlatformApp() {
    var connected by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Proxy Platform") }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Secure proxy marketplace", style = MaterialTheme.typography.headlineSmall); Text("Choose a plan and manage your connections.", style = MaterialTheme.typography.bodyLarge) }
            item { Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(if (connected) "Connection active" else "Not connected", style = MaterialTheme.typography.titleMedium); Button(onClick = { connected = !connected }) { Text(if (connected) "Disconnect" else "Connect") } } } }
            item { Text("Available plans", style = MaterialTheme.typography.titleLarge) }
            items(demoProducts) { product -> Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(product.name, style = MaterialTheme.typography.titleMedium); Text("${product.location} • ${product.protocol}"); Text(product.price, style = MaterialTheme.typography.labelLarge); Button(onClick = {}) { Text("View details") } } } }
        }
    }
}
