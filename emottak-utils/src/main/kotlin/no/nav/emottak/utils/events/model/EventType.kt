package no.nav.emottak.utils.events.model

enum class EventType(val value: Int, val description: String) {
    MESSAGE_RECEIVED_VIA_SMTP(1, "Melding mottatt via SMTP"),
    ERROR_WHILE_RECEIVING_MESSAGE_VIA_SMTP(2, "Feil ved mottak av melding via SMTP"),
    MESSAGE_SENT_VIA_SMTP(3, "Melding sendt via SMTP"),
    ERROR_WHILE_SENDING_MESSAGE_VIA_SMTP(4, "Feil ved utsending melding via SMTP"),
    MESSAGE_RECEIVED_VIA_HTTP(5, "Melding mottatt via HTTP"),
    ERROR_WHILE_RECEIVING_MESSAGE_VIA_HTTP(6, "Feil ved mottak av melding via HTTP"),
    MESSAGE_SENT_VIA_HTTP(7, "Melding sendt via HTTP"),
    ERROR_WHILE_SENDING_MESSAGE_VIA_HTTP(8, "Feil ved utsending melding via HTTP"),
    PAYLOAD_SAVED_INTO_DATABASE(9, "Payload lagret i database"),
    ERROR_WHILE_SAVING_PAYLOAD_INTO_DATABASE(10, "Feil ved lagring payload i database"),
    PAYLOAD_READ_FROM_DATABASE(11, "Payload lest fra database"),
    ERROR_WHILE_READING_PAYLOAD_FROM_DATABASE(12, "Feil ved lesing payload fra database"),
    PAYLOAD_RECEIVED_VIA_HTTP(13, "Payload mottatt via HTTP"),
    ERROR_WHILE_RECEIVING_PAYLOAD_VIA_HTTP(14, "Feil ved mottak av payload via HTTP"),
    MESSAGE_PLACED_IN_QUEUE(15, "Melding lagt på kø"),
    ERROR_WHILE_STORING_MESSAGE_IN_QUEUE(16, "Feil ved lagring melding på kø"),
    MESSAGE_READ_FROM_QUEUE(17, "Melding lest fra kø"),
    ERROR_WHILE_READING_MESSAGE_FROM_QUEUE(18, "Feil ved lesing melding fra kø"),
    MESSAGE_SAVED_IN_JURIDISK_LOGG(19, "Melding lagret i juridisk logg"),
    ERROR_WHILE_SAVING_MESSAGE_IN_JURIDISK_LOGG(20, "Feil ved lagring melding i juridisk logg"),
    MESSAGE_ENCRYPTED(21, "Melding kryptert"),
    MESSAGE_ENCRYPTION_FAILED(22, "Kryptering av melding mislykket"),
    MESSAGE_DECRYPTED(23, "Melding dekryptert"),
    MESSAGE_DECRYPTION_FAILED(24, "Dekryptering av melding mislykket"),
    MESSAGE_COMPRESSED(25, "Melding komprimert"),
    MESSAGE_COMPRESSION_FAILED(26, "Komprimering av melding mislykket"),
    MESSAGE_DECOMPRESSED(27, "Melding dekomprimert"),
    MESSAGE_DECOMPRESSION_FAILED(28, "Dekomprimering av melding mislykket"),
    SIGNATURE_CHECK_SUCCESSFUL(29, "Signatursjekk vellykket"),
    SIGNATURE_CHECK_FAILED(30, "Signatursjekk mislykket"),
    OCSP_CHECK_SUCCESSFUL(31, "OCSP-sjekk vellykket"),
    OCSP_CHECK_FAILED(32, "OCSP-sjekk mislykket"),
    MESSAGE_SENT_TO_FAGSYSTEM(33, "Melding sendt til fagsystem"),
    ERROR_WHILE_SENDING_MESSAGE_TO_FAGSYSTEM(34, "Feil ved utsending melding til fagsystem"),
    MESSAGE_RECEIVED_FROM_FAGSYSTEM(35, "Melding mottatt fra fagsystem"),
    ERROR_WHILE_RECEIVING_MESSAGE_FROM_FAGSYSTEM(36, "Feil ved mottak av melding fra fagsystem"),
    MESSAGE_VALIDATED_AGAINST_CPA(37, "Melding validert mot CPA"),
    VALIDATION_AGAINST_CPA_FAILED(38, "Validering mot CPA mislykket"),
    MESSAGE_VALIDATED_AGAINST_XSD(39, "Melding validert mot XSD"),
    VALIDATION_AGAINST_XSD_FAILED(40, "Validering mot XSD mislykket"),
    UNKNOWN_ERROR_OCCURRED(41, "Ukjent feil oppsto!"),
    REFERENCE_RETRIEVED(42, "Reference hentet");

    companion object {
        fun fromInt(eventCode: Int): EventType {
            return EventType.entries.find {
                it.value == eventCode
            } ?: throw IllegalArgumentException("Unrecognized EventType code value: $eventCode")
        }
        fun fromString(description: String): EventType {
            return EventType.entries.find {
                it.description.equals(description)
            } ?: throw IllegalArgumentException("Unrecognized EventType description: '$description'")
        }
    }
}
