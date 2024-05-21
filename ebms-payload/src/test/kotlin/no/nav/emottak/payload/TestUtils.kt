package no.nav.emottak.payload

import java.io.FileInputStream

internal fun createInputstreamFromFile(filnavn: String) = FileInputStream(filnavn)

internal fun setupEnv() {
    System.setProperty("KEYSTORE_FILE_DEKRYPT", "src/test/resources/keystore/test_keystore.p12")
    System.setProperty("KEYSTORE_FILE_SIGN", "src/test/resources/keystore/test_keystore.p12")
    System.setProperty("KEYSTORE_PWD_FILE", "src/test/resources/keystore/credentials-test.json")
    System.setProperty("SIGNER_ALIAS", "test_signer_2023")
}
