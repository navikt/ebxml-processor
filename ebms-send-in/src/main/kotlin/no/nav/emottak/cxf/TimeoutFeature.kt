package no.nav.emottak.cxf

import org.apache.cxf.Bus
import org.apache.cxf.endpoint.Client
import org.apache.cxf.feature.AbstractFeature
import org.apache.cxf.transport.http.HTTPConduit
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy

/**
 * Timeoutfeature for å konfigurer timeout på cxf-klient
 */
class TimeoutFeature : AbstractFeature {
    private var receiveTimeout = DEFAULT_RECEIVE_TIMEOUT
    private var connectionTimeout = DEFAULT_CONNECTION_TIMEOUT

    constructor()
    constructor(receiveTimeout: Int, connectionTimeout: Int) {
        this.receiveTimeout = receiveTimeout
        this.connectionTimeout = connectionTimeout
    }

    override fun initialize(client: Client, bus: Bus) {
        val conduit = client.conduit
        if (conduit is HTTPConduit) {
            val policy = HTTPClientPolicy()
            policy.receiveTimeout = receiveTimeout.toLong()
            policy.connectionTimeout = connectionTimeout.toLong()
            conduit.client = policy
        }
        super.initialize(client, bus)
    }

    fun withReceiveTimeout(timeoutInMillis: Int): TimeoutFeature {
        receiveTimeout = timeoutInMillis
        return this
    }

    fun withConnectionTimeout(timeoutInMillis: Int): TimeoutFeature {
        connectionTimeout = timeoutInMillis
        return this
    }

    companion object {
        const val DEFAULT_RECEIVE_TIMEOUT = 10000
        const val DEFAULT_CONNECTION_TIMEOUT = 10000
    }
}
