package no.nav.emottak.payload.helseid.util.util.xmldsig

class AlgorithmValidator(private val minimumAlgorithms: List<String>) {

    fun validateMinimum(algorithm: String, minimum: String, type: String): Boolean {
        val minIndex = minimumAlgorithms.indexOf(minimum)
        isMinimumAlgorithmSupported(minIndex, minimum, type)
        val index = minimumAlgorithms.indexOf(algorithm)
        isAlgorithmSupported(index, algorithm, type)
        isAlgorithmLessThanMinimum(index, minIndex, algorithm, minimum)
        return true
    }

    private fun isMinimumAlgorithmSupported(minIndex: Int, minimum: String, type: String) {
        if (minIndex < 0) {
            throw RuntimeException("Unsupported specified minimum $type algorithm: $minimum")
        }
    }

    private fun isAlgorithmSupported(index: Int, algorithm: String, type: String) {
        if (index < 0) {
            throw RuntimeException("Unsupported $type algorithm: $algorithm")
        }
    }

    private fun isAlgorithmLessThanMinimum(index: Int, minIndex: Int, algorithm: String, minimum: String) {
        if (index < minIndex) {
            throw RuntimeException(
                "Signature algorithm ($algorithm) is less than the specified " +
                    "minimum signature algorithm ($minimum)"
            )
        }
    }
}
