package no.nav.emottak.ebms

import org.junit.jupiter.api.Test

private const val SYNC_PATH = "/ebms/sync"

class EbmsRouteSyncIT : EbmsRoutFellesIT(SYNC_PATH) {

    @Test
    fun test() {
        println("Hello")
    }
}
