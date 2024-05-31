package no.nav.emottak.nfs

import java.io.File

interface FileReader {
    fun read(filePath: String): ByteArray
}

class DefaultFileReader : FileReader {
    override fun read(filePath: String): ByteArray {
        return File(filePath).readBytes()
    }
}
