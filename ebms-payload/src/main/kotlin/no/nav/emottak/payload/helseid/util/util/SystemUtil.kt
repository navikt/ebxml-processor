package no.nav.emottak.payload.helseid.util.util

object SystemUtil {

    /**
     * Gets environment variable or system property. Environment variable is checked first
     * @param name The name of the property
     * @param defaultValue the default value if the variable or property isn't set
     * @return The value of the variable or property or the dafult value
     */
    fun getEnvOrSystemProperty(name: String, defaultValue: String): String =
        getEnvOrSystemProperty(name) ?: defaultValue

    /**
     * Gets environment variable or system property. Environment variable is checked first
     * @param name The name of the property
     * @return The value of the variable or property
     */
    // Using environment variables is security-sensitive
    fun getEnvOrSystemProperty(name: String): String? {
        var value = System.getenv(name)
        if (value.isNullOrBlank()) {
            value = System.getProperty(name)
        }
        return value
    }
}
