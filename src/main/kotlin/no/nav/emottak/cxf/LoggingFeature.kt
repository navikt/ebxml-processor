package no.nav.emottak.cxf

import org.apache.cxf.Bus
import org.apache.cxf.feature.AbstractFeature
import org.apache.cxf.interceptor.AttachmentInInterceptor
import org.apache.cxf.interceptor.AttachmentOutInterceptor
import org.apache.cxf.interceptor.InterceptorProvider
import org.apache.cxf.interceptor.LoggingInInterceptor
import org.apache.cxf.interceptor.LoggingOutInterceptor

class LoggingFeature : AbstractFeature() {
    override fun initializeProvider(provider: InterceptorProvider, bus: Bus) {
        provider.inInterceptors.add(IN)
        provider.inFaultInterceptors.add(IN)
        provider.outInterceptors.add(OUT)
        provider.outFaultInterceptors.add(OUT)
    }

    companion object {
        private const val DEFAULT_LIMIT = 64 * 1024
        private val IN = LoggingInInterceptor(DEFAULT_LIMIT)
        private val OUT = LoggingOutInterceptor(DEFAULT_LIMIT)

        init {
            IN.addAfter(AttachmentInInterceptor::class.java.name)
            OUT.addAfter(AttachmentOutInterceptor::class.java.name)
        }
    }
}
