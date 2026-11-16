# ebms-async

Håndterer asynkron ebXML-meldingstrafikk via Kafka. Konsumerer meldinger fra smtp-transport, behandler dem gjennom samme kjede som ebms-provider (CPA-validering → ebms-payload → ebms-send-in), og produserer kvitteringer og signaler tilbake på Kafka.

## API

| Metode | Endepunkt | Beskrivelse |
|---|---|---|
| `GET` | `/api/payloads/{referenceId}` | Hent lagrede payloads for en referanse-ID (krever Azure AD-autentisering) |
| `GET` | `/api/retry/{retryLimit}` | Kjør retry-kø manuelt med angitt grense |
| `GET` | `/api/rerun/{offset}` | Kjør en spesifikk melding på nytt basert på Kafka-offset |
| `GET` | `/api/pauseretry` | Sett automatisk retry på pause |
| `GET` | `/api/resumeretry` | Gjenoppta automatisk retry |
| `GET` | `/api/forceretry/{offset}` | Send melding til retry-kø (kun ikke-prod) |
| `GET` | `/internal/health/liveness` | Liveness-sjekk |
| `GET` | `/internal/health/readiness` | Readiness-sjekk |
| `GET` | `/prometheus` | Prometheus-metrikker |

## Kafka

Konsumerer to topics og produserer til to topics, konfigurert via `ebms_kafka_queues.conf`:

| Topic | Retning | Beskrivelse |
|---|---|---|
| `team-emottak.smtp.in.ebxml.payload` | inn | Fagmeldinger fra smtp-transport |
| `team-emottak.smtp.in.ebxml.signal` | inn | Signalmeldinger (kvitteringer m.m.) fra smtp-transport |
| `team-emottak.smtp.out.ebxml.payload` | ut | Utgående fagmeldingssvar |
| `team-emottak.smtp.out.ebxml.signal` | ut | Kvitteringer (Acknowledgment) og feilmeldinger (MessageError) |

## Utgående svar over HTTP

Når mottakerens DeliveryChannel i CPA-en bruker HTTP, sender ebms-async svaret direkte til HTTP-endepunktet i CPA-en, ikke til `team-emottak.smtp.out.ebxml.payload`. Kallet går gjennom webproxy.

Mottakeren skal svare synkront med en ebXML-melding:

- Acknowledgment: meldingen regnes som levert og venter ikke lenger på kvittering. Resend-jobben sender den ikke på nytt.
- MessageError: ebms-async prøver på nytt via retry-køen.
- En annen status enn 2xx, eller 2xx uten en ebXML-melding vi kjenner igjen: ebms-async prøver på nytt via retry-køen.

## Database

Bruker PostgreSQL for mellomlagring av payloads og sporing av meldinger som venter på kvittering. Skjemamigrering håndteres av Flyway.
