# ebms-payload

Behandler fagmeldingen pakket inn i ebXML-konvolutten. Utfører dekryptering, dekomprimering, XSD-validering, signering, juridisk logging og OCSP-sjekk av sertifikater. Kalles av ebms-provider (synkront) og ebms-async (asynkront).

## API

| Metode | Endepunkt | Beskrivelse |
|---|---|---|
| `POST` | `/payload` | Behandler en fagmelding (inn eller ut) |
| `GET` | `/internal/health/liveness` | Liveness-sjekk |
| `GET` | `/internal/health/readiness` | Readiness-sjekk |
| `GET` | `/prometheus` | Prometheus-metrikker |

## CRL-oppdatering

Applikasjonen forsøker å hente alle konfigurerte CRL-er før serveren startes og oppdaterer deretter den prosesslokale CRL-cachen periodisk. Meldingsflyten bruker bare allerede innlastede CRL-er og gjør ingen nettverkskall. Oppdateringsintervallet styres av `CRL_REFRESH_INTERVAL` og er som standard `1h`.
