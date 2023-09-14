package no.nav.emottak.melding.process

import java.io.FileInputStream

internal fun createInputstreamFromFile(filnavn: String) = FileInputStream(filnavn)

internal fun setupEnv() {
    System.setProperty("KEYSTORE_FILE", "src/test/resources/keystore/test_keystore.p12")
    System.setProperty("KEYSTORE_PWD", "changeit")
    System.setProperty("SIGNER_ALIAS", "test_signer_2023")
}