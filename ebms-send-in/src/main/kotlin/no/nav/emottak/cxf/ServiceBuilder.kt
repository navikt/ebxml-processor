package no.nav.emottak.cxf
import org.apache.cxf.frontend.ClientProxy
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.transport.http.HTTPConduit
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.cxf.ws.security.SecurityConstants
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor
import org.apache.wss4j.common.ConfigurationConstants
import org.apache.wss4j.common.ext.WSPasswordCallback
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import javax.security.auth.callback.Callback
import javax.security.auth.callback.CallbackHandler
import javax.xml.namespace.QName

internal val log = LoggerFactory.getLogger("no.nav.emottak.cxf")

class ServiceBuilder<T>(resultClass: Class<T>) {
    var resultClass: Class<T>
    private val factoryBean: JaxWsProxyFactoryBean

    init {
        factoryBean = JaxWsProxyFactoryBean()
        factoryBean.serviceClass = resultClass
        this.resultClass = resultClass
    }

    fun withWsdl(wsdl: String?): ServiceBuilder<T> {
        factoryBean.wsdlURL = wsdl
        return this
    }

    fun withServiceName(name: QName?): ServiceBuilder<T> {
        factoryBean.serviceName = name
        return this
    }

    fun withEndpointName(name: QName?): ServiceBuilder<T> {
        factoryBean.endpointName = name
        return this
    }

    fun withAddress(address: String?): ServiceBuilder<T> {
        factoryBean.address = address
        return this
    }

    fun withLogging(): ServiceBuilder<T> {
        factoryBean.features.add(LoggingFeature())
        return this
    }

    fun withAddressing(): ServiceBuilder<T> {
        factoryBean.features.add(WSAddressingFeature())
        return this
    }

    fun withTimeout(): ServiceBuilder<T> {
        factoryBean.features.add(TimeoutFeature(RECEIVE_TIMEOUT, CONNECTION_TIMEOUT))
        return this
    }

    fun get(): JaxWsProxyFactoryBean {
        return factoryBean
    }

    fun withProperties(): ServiceBuilder<T> {
        val props: MutableMap<String, Any> = HashMap()
        props["mtom-enabled"] = "true"
        props[SecurityConstants.MUST_UNDERSTAND] = false
        // Denne må settes for å unngå at CXF instansierer EhCache med en non-default konfigurasjon. Denne sørger
        // for at vår konfigurasjon faktisk blir lastet.
        //  props.put(SecurityConstants.CACHE_CONFIG_FILE, "ehcache.xml");
        factoryBean.properties = props
        return this
    }

    fun build(): PortTypeBuilder<T> {
        return PortTypeBuilder<T>(factoryBean.create(resultClass))
    }

    fun asStandardService(): ServiceBuilder<T> {
        return withAddressing()
            .withLogging()
            .withTimeout()
            .withProperties()
    }

    inner class PortTypeBuilder<R> constructor(val portType: R) {
        fun withBasicSecurity(): PortTypeBuilder<R> {
            val usernameKv = "/secret/serviceuser/username"
            val passwordKv = "/secret/serviceuser/password"
            val userName = String(FileInputStream(usernameKv).readAllBytes())
            val password = String(FileInputStream(passwordKv).readAllBytes())
            val conduit: HTTPConduit = ClientProxy.getClient(portType).conduit as HTTPConduit
            conduit.authorization.userName = userName
            conduit.authorization.password = password
            return this
        }

        fun withUserNameToken(username: String, password: String): PortTypeBuilder<R> {
            val map = HashMap<String, Any>()
            map[ConfigurationConstants.ACTION] = ConfigurationConstants.USERNAME_TOKEN
            map[ConfigurationConstants.PASSWORD_TYPE] = "PasswordText"
            map[ConfigurationConstants.USER] = username
            val passwordCallbackHandler = CallbackHandler { callbacks: Array<Callback> ->
                val callback = callbacks[0] as WSPasswordCallback
                callback.password = password
            }
            map.put(ConfigurationConstants.PW_CALLBACK_REF, passwordCallbackHandler)
            ClientProxy.getClient(portType).outInterceptors.add(WSS4JOutInterceptor(map))
            return this
        }

        fun get(): R {
            return portType
        }
    }

    companion object {
        const val RECEIVE_TIMEOUT = 30000
        const val CONNECTION_TIMEOUT = 10000
    }
}
