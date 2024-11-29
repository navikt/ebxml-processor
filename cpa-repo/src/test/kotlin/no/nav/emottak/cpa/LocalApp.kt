package no.nav.emottak.cpa

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.emottak.cpa.persistence.cpaDbConfig
import no.nav.emottak.cpa.persistence.cpaMigrationConfig
import no.nav.emottak.cpa.persistence.oracleConfig
import org.apache.commons.lang3.SystemUtils
import java.io.File
import java.io.IOException
import java.util.*
import java.util.regex.Pattern

val kubectlPath: String = getLocalKubectlPath()
var envVariables: MutableMap<String, String> = mutableMapOf()
var mountedValues = mapOf(
    "EMOTTAK_USERNAME" to "/secrets/oracle/creds/username",
    "EMOTTAK_PASSWORD" to "/secrets/oracle/creds/password",
    "EMOTTAK_JDBC_URL" to "/secrets/oracle/config/jdbc_url",
    "VAULT_TOKEN" to "/var/run/secrets/nais.io/vault/vault_token"
)
var ingresses = mapOf(
    "ebms-provider" to "https://ebms-provider-fss.intern.dev.nav.no",
    "ebms-payload" to "https://ebms-payload-fss.intern.dev.nav.no",
    "cpa-repo" to "https://cpa-repo-fss.intern.dev.nav.no",
    "ebms-send-in" to "https://ebms-send-in.intern.dev.nav.no",
    "cpa-sync" to "https://cpa-sync.intern.dev.nav.no",
    "ebms-http" to "https://ebms-http-fss.intern.dev.nav.no",
    "ebms-sync-router" to "https://ebms-sync-router.dev.intern.nav.no",
    "ebms-asynch-router-inn" to "https://ebms-asynch-router-inn.intern.dev.nav.no",
    "ebms-asynch-router-ut" to "https://ebms-asynch-router-ut.intern.dev.nav.no"
)

fun main() {
    prepareEnvironment()

    embeddedServer(
        Netty,
        port = 8080,
        module = cpaApplicationModule(
            cpaDbConfig.value,
            cpaMigrationConfig.value,
            oracleConfig.value
        )
    ).start(wait = true)
}

fun prepareEnvironment() {
    retrieveEnvVariables()
    retrieveMountedValues()
    setEnvVariables()
}

fun retrieveEnvVariables() {
    val podName: String = retrievePodName()
    val envVariablesString: String =
        runCommand(kubectlPath + " exec -it " + podName + " -- env")

    val lines = envVariablesString.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    for (line in lines) {
        val keyValue = line.split("=".toRegex(), limit = 2).toTypedArray()
        if (keyValue.size == 2) {
            envVariables[keyValue[0].trim { it <= ' ' }] = keyValue[1].trim { it <= ' ' }
        }
    }
}

@Throws(IOException::class)
fun retrieveMountedValues() {
    val podsName: String = retrievePodName()
    for ((key, path) in mountedValues.entries) {
        val value: String = runCommand(kubectlPath + " exec " + podsName + " -- cat " + path)
        envVariables.put(key, value)
    }
}

fun retrievePodName(): String {
    val regex = "cpa-repo-[^\\s]*"
    val pods: String = runCommand(kubectlPath + " get pods -o custom-columns=:metadata.name")
    val podMatcher = Pattern
        .compile(regex)
        .matcher(pods)
    podMatcher.find()
    return podMatcher.group()
}

@Throws(IOException::class)
fun runCommand(command: String): String {
    val cmdArray = command.split(" ").toTypedArray()
    val process = Runtime.getRuntime().exec(cmdArray, null)

    return process.inputStream.bufferedReader().use { it.readText() }
}

fun setEnvVariables() {
    // Check which properties are used by a particular service

    for ((key, value) in envVariables) {
        System.setProperty(key, value)
    }

    // Custom settings
}

fun getLocalKubectlPath(): String {
    return when {
        SystemUtils.IS_OS_WINDOWS -> {
            "kubectl"
        }
        SystemUtils.IS_OS_LINUX -> {
            "/usr/local/bin/kubectl"
        }
        SystemUtils.IS_OS_MAC -> {
            if (File("/opt/homebrew/bin/kubectl").exists()) {
                "/opt/homebrew/bin/kubectl"
            } else if (File("/usr/local/bin/kubectl").exists()) {
                "/usr/local/bin/kubectl"
            } else {
                throw IllegalStateException("kubectl not found on macOS")
            }
        }
        else -> {
            throw UnsupportedOperationException(
                "Unsupported OS: " + System.getProperty("os.name").lowercase(Locale.getDefault())
            )
        }
    }
}
