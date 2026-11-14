package com.mosquednd.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class MdnsDiscoveryManager(private val context: Context, private val onServiceFound: (String, Int) -> Unit) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val serviceType = "_telemetry._tcp"

    fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) { Log.d("MdnsDiscovery", "Started") }
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType == serviceType) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            onServiceFound(serviceInfo.host.hostAddress ?: "", serviceInfo.port)
                        }
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { }
                    })
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) { }
            override fun onDiscoveryStopped(service: String) { }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { }
        }
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
    }
}
