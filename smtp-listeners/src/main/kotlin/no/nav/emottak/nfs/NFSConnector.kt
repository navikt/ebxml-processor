package no.nav.emottak.nfs

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import no.nav.emottak.smtp.getEnvVar
import java.io.InputStream
import java.util.*

class NFSConnector(
    jSch: JSch = JSch(),
    fileReader: FileReader = DefaultFileReader()
) : AutoCloseable {

    private val privateKeyFile = "/var/run/secrets/privatekey"
    private val publicKeyFile = "/var/run/secrets/publickey"
    private val usernameMount = "/var/run/secrets/nfsusername"
    private val passphraseMount = "/var/run/secrets/passphrase"

    private val passphrase = String(fileReader.read(passphraseMount))
    private val username = String(fileReader.read(usernameMount))
    private val host = getEnvVar("NFS_HOST", "10.183.32.98")
    private val port = getEnvVar("NFS_PORT", "22").toInt()
    private val channelType = "sftp"
    private val jsch: JSch = jSch
    private val session: Session
    private val sftpChannel: ChannelSftp

    init {
        val knownHosts = Thread.currentThread().getContextClassLoader().getResourceAsStream("known_hosts")
        jsch.setKnownHosts(knownHosts)
        jsch.addIdentity(privateKeyFile, publicKeyFile, passphrase.toByteArray())
        session = jsch.getSession(username, host, port)
        session.userInfo = DummyUserInfo()
        session.connect()

        sftpChannel = session.openChannel(channelType) as ChannelSftp
        sftpChannel.connect()
        sftpChannel.cd("/outbound/cpa")
    }

    fun folder(path: String = "/outbound/cpa"): Vector<ChannelSftp.LsEntry> =
        sftpChannel.ls(path) as Vector<ChannelSftp.LsEntry>

    fun file(filename: String): InputStream = sftpChannel.get(filename)

    override fun close() {
        sftpChannel.disconnect()
        session.disconnect()
    }
}

class DummyUserInfo : UserInfo {
    override fun getPassword(): String? {
        return passwd
    }

    override fun promptYesNo(str: String): Boolean {
        return true
    }

    var passwd: String? = null
    override fun getPassphrase(): String? {
        return null
    }

    override fun promptPassphrase(message: String): Boolean {
        return true
    }

    override fun promptPassword(message: String): Boolean {
        return true
    }

    override fun showMessage(message: String) {}
}
