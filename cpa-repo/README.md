# cpa-repo

Lagrer og validerer CPAer (Collaboration Protocol Agreements). Validerer innkommende og utgående meldinger mot relevant CPA og returnerer prosesseringskonfigurasjon til ebms-provider og ebms-async.

## API

| Metode | Endepunkt | Beskrivelse |
|---|---|---|
| `POST` | `/cpa` | Legg til en ny CPA |
| `GET` | `/cpa/{cpaId}` | Hent en CPA |
| `DELETE` | `/cpa/delete/{cpaId}` | Slett en CPA |
| `POST` | `/cpa/validate/{requestId}` | Valider en melding mot relevant CPA |
| `POST` | `/cpa/messagingCharacteristics` | Hent meldingskarakteristikker for en CPA |
| `GET` | `/cpa/timestamps/last_updated` | Hent tidsstempler for sist oppdaterte CPAer |
| `GET` | `/cpa/timestamps/last_updated/latest` | Hent siste oppdateringstidspunkt |
| `GET` | `/cpa/timestamps/last_used` | Hent tidsstempler for sist brukte CPAer |
| `GET` | `/cpa/{cpaId}/party/{partyType}/{partyId}/encryption/certificate` | Hent krypteringssertifikat for en part |
| `POST` | `/signing/certificate` | Hent signeringssertifikat |
| `GET` | `/cpa/partnerId/{herId}` | Hent partner-ID for et HER-ID |
| `GET` | `/partner/her/{herId}` | Hent partnerinformasjon for et HER-ID |
| `GET` | `/internal/health/liveness` | Liveness-sjekk |
| `GET` | `/internal/health/readiness` | Readiness-sjekk |
| `GET` | `/prometheus` | Prometheus-metrikker |

## Database

Bruker Oracle (legacy, via Vault) og PostgreSQL. Skjemamigrering håndteres av Flyway.
