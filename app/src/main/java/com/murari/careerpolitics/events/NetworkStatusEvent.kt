package com.murari.careerpolitics.events

import com.murari.careerpolitics.util.network.NetworkStatus

/**
 * Event posted when network connectivity changes.
 *
 * @property networkStatus The current network status (e.g., CONNECTED, DISCONNECTED).
 */
data class NetworkStatusEvent(val networkStatus: NetworkStatus)
