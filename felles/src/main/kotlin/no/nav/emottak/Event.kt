package no.nav.emottak


class Event (
    val processName: String,
    val eventStatus: Status,
    val description: String = processName,
    val correlationId: String,
) {
    enum class Status {
        STARTED,
        OK,
        FAILED,
        //NOT_COMPLETED,
    }

    companion object {
        fun <T> defaultProcessName(clazz: Class<T>): String {
            return clazz.simpleName
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is Event
                && eventStatus.name == other.eventStatus.name
                && processName.equals(other.processName)
                && correlationId.equals(other.correlationId)
    }

    override fun toString(): String {
        return "$processName ${eventStatus.name}"
    }

}