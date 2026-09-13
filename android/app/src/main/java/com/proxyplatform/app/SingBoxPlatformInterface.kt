package com.proxyplatform.app

import android.content.pm.PackageManager.NameNotFoundException
import android.net.IpPrefix
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.util.Log
import io.nekohasekai.libbox.*
import java.net.InetAddress

/** Android platform bridge required by the official Sing-box libbox runtime. */
class SingBoxPlatformInterface(private val service: VpnService) : PlatformInterface {
    override fun autoDetectInterfaceControl(fd: Int) {
        // Sing-box calls this for every upstream socket so it cannot be captured by its own TUN.
        if (!service.protect(fd)) error("VpnService.protect failed for upstream socket")
    }

    override fun openTun(options: TunOptions): Int {
        val builder = service.Builder()
            .setSession("Proxy Platform")
            .setMtu(options.mtu)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        addAddresses(builder, options.inet4Address)
        addAddresses(builder, options.inet6Address)

        if (options.autoRoute) {
            addDns(builder, options)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                addRoutes(builder, options.inet4RouteAddress)
                addRoutes(builder, options.inet6RouteAddress)
                addExcludedRoutes(builder, options.inet4RouteExcludeAddress)
                addExcludedRoutes(builder, options.inet6RouteExcludeAddress)
            } else {
                addRoutes(builder, options.inet4RouteRange)
                addRoutes(builder, options.inet6RouteRange)
            }
            addPackages(builder, options.includePackage, true)
            addPackages(builder, options.excludePackage, false)
        }

        if (options.isHTTPProxyEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setHttpProxy(ProxyInfo.buildDirectProxy(
                options.httpProxyServer,
                options.httpProxyServerPort,
                iteratorToList(options.httpProxyBypassDomain),
            ))
        }

        val descriptor = builder.establish() ?: error("Could not establish the Android TUN")
        return descriptor.detachFd()
    }

    private fun addDns(builder: VpnService.Builder, options: TunOptions) {
        val dns = options.dnsServerAddress
        while (dns.hasNext()) builder.addDnsServer(dns.next())
    }

    private fun addAddresses(builder: VpnService.Builder, addresses: RoutePrefixIterator) {
        while (addresses.hasNext()) {
            val address = addresses.next()
            builder.addAddress(address.address(), address.prefix())
        }
    }

    private fun addRoutes(builder: VpnService.Builder, routes: RoutePrefixIterator) {
        while (routes.hasNext()) {
            val route = routes.next()
            builder.addRoute(route.address(), route.prefix())
        }
    }

    private fun addExcludedRoutes(builder: VpnService.Builder, routes: RoutePrefixIterator) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        while (routes.hasNext()) {
            val route = routes.next()
            builder.excludeRoute(IpPrefix(InetAddress.getByName(route.address()), route.prefix()))
        }
    }

    private fun addPackages(builder: VpnService.Builder, packages: StringIterator, allow: Boolean) {
        while (packages.hasNext()) {
            val name = packages.next()
            try {
                if (allow) builder.addAllowedApplication(name) else builder.addDisallowedApplication(name)
            } catch (_: NameNotFoundException) {
                Log.w(TAG, "Package not found in Sing-box route list: $name")
            }
        }
    }

    private fun iteratorToList(iterator: StringIterator): List<String> {
        val result = mutableListOf<String>()
        while (iterator.hasNext()) result += iterator.next()
        return result
    }

    override fun includeAllNetworks() = false
    override fun usePlatformAutoDetectInterfaceControl() = true
    override fun usePlatformBridge() = false
    override fun usePlatformShell() = false
    override fun useProcFS() = false
    override fun registerMyInterface(name: String) = Unit
    override fun clearDNSCache() = Unit
    override fun localDNSTransport(): LocalDNSTransport? = null
    override fun readWIFIState(): WIFIState? = null
    override fun tailscaleHostname(): String = ""
    override fun underNetworkExtension() = false
    override fun getInterfaces(): NetworkInterfaceIterator = object : NetworkInterfaceIterator {
        override fun hasNext() = false
        override fun next(): NetworkInterface = error("No cached interface list")
    }
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) = Unit
    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) = Unit
    override fun startNeighborMonitor(listener: NeighborUpdateListener?) = Unit
    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) = Unit
    override fun sendNotification(notification: Notification) = Unit
    override fun cancelNotification(group: String?, id: Int) = Unit
    override fun findConnectionOwner(network: Int, sourceAddress: String, sourcePort: Int, destinationAddress: String, destinationPort: Int): ConnectionOwner? = null
    override fun lookupUser(username: String): PlatformUser? = null
    override fun lookupSFTPServer(): String = ""
    override fun checkPlatformShell() = error("Platform shell is not available")
    override fun openShellSession(user: PlatformUser, command: String, environ: StringIterator, term: String, rows: Int, cols: Int): ShellSession? = null
    override fun createBridge(options: BridgeOptions): BridgeSession? = null
    override fun readSystemSSHHostKey(): String = ""

    companion object { private const val TAG = "SingBoxPlatform" }
}
