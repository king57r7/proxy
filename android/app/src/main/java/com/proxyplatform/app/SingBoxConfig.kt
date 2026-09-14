package com.proxyplatform.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SingBoxConfig {
    fun write(
        context: Context,
        protocol: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        localPort: Int = 10808,
    ): File {
        val outbound = JSONObject()
            .put("type", if (protocol == "socks5") "socks" else protocol)
            .put("tag", "proxy")
            .put("server", host)
            .put("server_port", port)
        if (protocol in setOf("vmess", "vless", "trojan", "shadowsocks")) {
            outbound.put("multiplex", JSONObject().put("enabled", true).put("protocol", "smux").put("max_connections", 4))
        }
        if (username.isNotBlank()) {
            outbound.put("username", username)
            outbound.put("password", password)
        }

        val dns = JSONObject()
            .put("type", "https")
            .put("tag", "cloudflare-doh")
            .put("server", "1.1.1.1")
            .put("server_port", 443)
            .put("path", "/dns-query")
            .put("detour", "proxy")

        val config = JSONObject()
            .put("log", JSONObject().put("level", "error"))
            .put("dns", JSONObject()
                .put("servers", JSONArray().put(dns))
                .put("final", "cloudflare-doh")
                .put("strategy", "prefer_ipv4"))
            .put("inbounds", JSONArray().put(JSONObject()
                .put("type", "mixed")
                .put("tag", "mixed-in")
                .put("listen", "127.0.0.1")
                .put("listen_port", localPort)))
            .put("outbounds", JSONArray()
                .put(outbound)
                .put(JSONObject().put("type", "direct").put("tag", "direct")))
            .put("route", JSONObject()
                .put("auto_detect_interface", true)
                .put("final", "proxy"))

        return File(context.filesDir, CONFIG_FILE).also {
            it.parentFile?.mkdirs()
            it.writeText(config.toString(2))
        }
    }

    private const val CONFIG_FILE = "Config.json"
}
