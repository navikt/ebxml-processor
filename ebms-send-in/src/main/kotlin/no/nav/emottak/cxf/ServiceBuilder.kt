package no.nav.emottak.cxf
import no.nav.emottak.util.getEnvVar
import org.apache.cxf.frontend.ClientProxy
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.transport.http.HTTPConduit
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.cxf.ws.security.SecurityConstants
import javax.xml.namespace.QName

class ServiceBuilder<T>(resultClass: Class<T>) {
    var resultClass: Class<T>
    private val factoryBean: JaxWsProxyFactoryBean

    init {
        factoryBean = JaxWsProxyFactoryBean()
        factoryBean.serviceClass = resultClass
        this.resultClass = resultClass
    }

    fun withExtraClasses(classes: Array<Class<*>?>?): ServiceBuilder<T> {
        factoryBean.properties["jaxb.additionalContextClasses"] = classes
        return this
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
            val conduit: HTTPConduit = ClientProxy.getClient(portType).conduit as HTTPConduit
            conduit.authorization.userName = "srvTokt"
            conduit.authorization.password = getEnvVar("toktPassword")
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
