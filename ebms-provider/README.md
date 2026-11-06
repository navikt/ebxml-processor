# ebms-provider

Mottar og behandler ebXML-meldinger over HTTP, med synkront eller asynkront fagsvar. Validerer ebXML-konvolutten, slår opp CPA-konfigurasjon, videresender fagmeldingen til ebms-payload og leverer til fagsystemet via ebms-send-in.

## API

| Metode | Endepunkt | Beskrivelse |
|---|---|---|
| `POST` | `/ebms/sync` | Mottar og behandler synkron ebXML-melding |
| `POST` | `/ebms/async` | Mottar ebXML-melding og returnerer kvittering. Fagsvaret kommer asynkront |
| `GET` | `/internal/health/liveness` | Liveness-sjekk |
| `GET` | `/internal/health/readiness` | Readiness-sjekk |
| `GET` | `/internal/status` | Statuskontroll |
| `GET` | `/prometheus` | Prometheus-metrikker |

`/ebms/async` validerer og behandler meldingen før den videresendes til `/fagmelding/asynkron` i ebms-send-in.
Når dette lykkes, returnerer endpointet `202 Accepted` med en signert ebXML-acknowledgment. Svaret fra fagsystemet kommer asynkront.
`/ebms/sync` returnerer fagsvaret synkront

## Avhengigheter

Kaller disse tjenestene under behandling av en melding:

- **cpa-repo** — CPA-validering og prosesseringskonfigurasjon
- **ebms-payload** — behandling av fagmeldingen (dekryptering, validering, signering)
- **ebms-send-in** — videresending til fagsystem (kun `Direction.IN`)
