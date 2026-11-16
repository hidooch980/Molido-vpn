package com.molido.vpn.vpn

import android.net.VpnService

/**
 * VPN lifecycle owner. Engine adapters must be coordinated here; this service
 * deliberately does not claim connectivity until a real engine is integrated
 * and traffic verification succeeds.
 */
class MolidoVpnService : VpnService()
