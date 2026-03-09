# ebms-payload

Behandler fagmeldingen pakket inn i ebXML-konvolutten. Utfører dekryptering, dekomprimering, XSD-validering, signering, juridisk logging og OCSP-sjekk av sertifikater. Kalles av ebms-provider (synkront) og ebms-async (asynkront).

## API

| Metode | Endepunkt | Beskrivelse |
|---|---|---|
| `POST` | `/payload` | Behandler en fagmelding (inn eller ut) |
| `GET` | `/internal/health/liveness` | Liveness-sjekk |
| `GET` | `/internal/health/readiness` | Readiness-sjekk |
| `GET` | `/prometheus` | Prometheus-metrikker |
