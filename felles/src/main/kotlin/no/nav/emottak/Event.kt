package no.nav.emottak

class Event(
    val processName: String,
    val eventStatus: Status,
    val description: String = processName,
    val correlationId: String
) {
    enum class Status {
        STARTED,
        OK,
        FAILED
        // NOT_COMPLETED,
    }

    override fun toString(): String {
        return "$processName ${eventStatus.name}"
    }
}
