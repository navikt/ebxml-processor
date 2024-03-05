package no.nav.emottak.melding.apprec

import no.nav.emottak.util.signatur.SignatureException

enum class AppRecErrorCode(val description: String) {
    X99("Annen feil"),
    E10("Ugyldig meldingsidentifikator"),
    E20("Lege finnes ikke"),
    E21("Mottager finnes ikke"),
    E35("Pasienten finnes ikke i mottakersystemet"),
    T01("Ikke XML / ikke 'well formed' / uleselig"),
    T02("XML validerer ikke"),
    T10("Støtter ikke meldingsformatet"),
    T99("Annen feil på format"),
    S01("Feil på signatur"),
    S02("Ugyldig sertifikat"),
    S03("Tilbaketrukket sertifikat"),
    S99("Annen feil på signatur eller sertifikat");

    companion object{
        fun getCode(exception: Exception): AppRecErrorCode =
            when (exception) {
                is SignatureException -> S01
                else -> X99
            }
    }
}
