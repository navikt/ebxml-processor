package no.nav.emottak.ebms.util

import org.apache.xml.security.c14n.Canonicalizer
import org.w3c.dom.Document
import java.io.ByteArrayOutputStream

fun Document.toByteArray(): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val canonicalizer = Canonicalizer.getInstance(Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS)
    canonicalizer.canonicalizeSubtree(this, byteArrayOutputStream)
    return byteArrayOutputStream.toByteArray()
}
