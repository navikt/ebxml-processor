[![Build and deploy ebms-provider](https://github.com/navikt/ebxml-processor/actions/workflows/build.yaml/badge.svg)](https://github.com/navikt/ebxml-processor/actions/workflows/build.yaml)

# eMottak ebXML
Dette prosjektet håndterer meldinger mottatt på ebXML-standarden.

Prosjektet er et Gradle multi-modul-prosjekt med følgende moduler:

| Modul | Beskrivelse |
|---|---|
| `ebms-provider` | Mottar og behandler synkrone ebXML-meldinger |
| `ebms-payload` | Behandler fagmeldingen pakket inn i ebXML-konvolutten |
| `cpa-repo` | Lagrer og validerer CPAer (Collaboration Protocol Agreements) |
| `ebms-async` | Håndterer asynkron trafikk via Kafka |
| `ebxml-processing-model` | Felles domenemodell, publisert som eget Maven-artefakt |
| `felles` | Delte verktøy: krypto, signatur, XML-hjelpere og Kafka-konfig |

### EbMS Provider
Selve motoren i ebMS-håndteringen. Det er her ebMS-spesifikasjonen er implementert.
Validerer ebXML-konvolutten, slår opp mot CPA-repo for å hente prosesserings­konfigurasjon, og videresender fagmeldingen til ebMS-payload og deretter til fagsystemet.
REST-endepunkt: `POST https://ebms-provider-fss.intern.dev.nav.no/ebms/sync`

### EbMS Payload
Behandling av fagmeldingene som er pakket inn i ebXML. Mottar meldinger fra ebMS-provider og
utfører dekryptering, dekomprimering, XSD-validering, signering og OCSP-sjekk av sertifikater.
REST-endepunkt: `POST https://ebms-payload-fss.intern.dev.nav.no/payload`

### CPA Repo
Holder på alle godkjente CPAer. Validerer innkommende meldinger mot relevant CPA og returnerer prosesseringskonfigurasjon til ebMS-provider.
REST-baseUrl: `https://cpa-repo-fss.intern.dev.nav.no`

### EbMS Async
Håndterer asynkron meldingstrafikk. Konsumerer meldinger fra Kafka (levert av smtp-transport), gjenbruker prosesseringslogikken fra ebMS-provider og produserer kvitteringer og signaler tilbake på Kafka.

### Meldingsflyt

**Synkron:**
```
HTTP POST /ebms/sync
  ├─ MIME-validering
  ├─ cpa-repo (CPA-validering av innkommende melding)
  ├─ ebms-payload (dekryptering, dekomprimering, signaturvalidering, juridisk logg)
  ├─ [Direction.IN]  → ebms-send-in (fagsystem)
  │                    → svar behandles ut (kryptering, signering) via ebms-payload
  │                    → signert ebXML-svar returneres til avsender
  └─ [Direction.OUT] → svar behandles ut via ebms-payload
                      → signert ebXML-svar returneres til avsender

  Feil → EbmsException  → MessageError returneres til avsender (signert hvis mulig)
       → ukjent feil    → SOAP Fault
```

**Asynkron:**
```
smtp-transport (Kafka)
  ├─ payload-topic  ─→ ebms-async (PayloadMessageService)
  │                      ├─ cpa-repo (CPA-validering)
  │                      ├─ ebms-payload (dekryptering, validering, signering)
  │                      ├─ [Direction.IN]  → ebms-send-in (fagsystem)
  │                      │                   → svar pakkes ut og sendes på payload-topic
  │                      ├─ [Direction.OUT] → svar sendes direkte på payload-topic
  │                      ├─ kvittering (Acknowledgment) → signal-topic
  │                      └─ feil → retry-kø (maks 10 forsøk, respekterer TimeToLive)
  │
  └─ signal-topic   ─→ ebms-async (SignalMessageService)
                         └─ cpa-repo (validering av innkommende signal)
```

## Utvikling
### Teknologi
Alle modulene kjører som selvstendige applikasjoner og er bygd opp av følgende teknologier:
* Kotlin / JVM 21
* Ktor (server og klient)
* Arrow (SuspendApp, structured concurrency)
* Gradle (multi-modul)
* Exposed (ORM) + Flyway (databasemigrering)
* Hoplite (HOCON-basert konfigurasjon)
* Kafka (kotlin-kafka)

### Avhengigheter
Avhengigheter og relasjoner til andre repoer i teamet:
* https://github.com/navikt/emottak-utils
* https://github.com/navikt/emottak-payload-xsd
* https://github.com/navikt/ebxml-protokoll
* https://github.com/navikt/ebms-sync-router

### Bygg prosjektet
For å bygge prosjektet brukes Gradle. `build`-tasken kjører automatisk `ktlintFormat` og `ktlintCheck`.

```bash
# Bygg hele prosjektet
./gradlew build

# Kjør tester for én modul
./gradlew :ebms-provider:test

# Kjør én enkelt testklasse
./gradlew :ebms-provider:test --tests "no.nav.emottak.ebms.MinTestKlasse"

# Formatér kode
./gradlew ktlintFormat
```

Noen av testene bruker [Testcontainers](https://github.com/testcontainers/testcontainers-java) for å bygge opp et mer komplett kjøretidsmiljø (PostgreSQL, Oracle, Kafka). 
Disse er avhengig av et fungerende Docker-miljø. For eksempel Docker, [Colima](https://github.com/abiosoft/colima) på mac, eller [Rancher Desktop](https://rancherdesktop.io/) (win/mac/linux).

Her er steg for hvordan bruke Colima:
1. [Installér Colima](https://github.com/abiosoft/colima?tab=readme-ov-file#installation).
2. Sett DOCKER_HOST env-variabel til Colima (mulig dette må reverseres når man bygger andre prosjekter).
   - `export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"`
3. Start Colima.
   - `colima start`
4. Bygg prosjekt/kjør tester.

Oracle-imaget brukt i tester krever endel minne, og det anbefales å kjøre `colima` med minst 4GB minne: `colima start --memory 4`

### Oppsett av Github PAT
For å kunne dra inn de riktige dependenciene, må man opprette en github PAT.
Denne trenger rettigheten `read:packages` og må autoriseres for tilgang til organisasjonen [navikt](https://github.com/navikt).

Denne PATen må settes som en environment variable, for eksempel globalt i ditt foretrukne shell eller IDE.
```
export GITHUB_TOKEN=ghp_abcdefghijklmnopqrstuvwxyzABCD012345
```

# ebXML standarder og dokumentasjon
Målet for prosjektet er å være i tråd med spesifikasjon fra e-helse.

### ebXML hos e-helse
Les dokumentasjon fra e-helse for deres tolkning av ebXML-standarden.

* [ebXML Rammeverk](https://sarepta.helsedir.no/standard/EBXML%20rammeverk)
* [Basert på ebXML](https://git.sarepta.ehelse.no/publisert/standarder/raw/master/kravdokument/EBXMLrammeverk/HIS%201037_2011%20Rammeverk%20for%20meldingsutveksling%20v1.1%20-oppdatert.pdf)
* [Implementasjon og bruk av ebXML som rammeverk for meldingsutveksling](https://git.sarepta.ehelse.no/publisert/standarder/raw/master/kravdokument/VeiledningTilRiktigBrukAvebXML/HITS%201171_2017%20Implementasjon%20og%20bruk%20av%20ebXML%20-oppdatert.pdf)
* [Validering av ebXML-meldinger](https://git.sarepta.ehelse.no/publisert/standarder/raw/master/kravdokument/ValideringAvebXMLMeldinger/HITS%201172_2017%20Validering%20av%20ebXML-meldinger%20-oppdatert.pdf)
* [Profil for CPP/CPA](https://www.ehelse.no/standardisering/standarder/profil-for-cpp-cpa--partnerprofiler-og-avtaler/_/attachment/inline/8ff59f07-70be-459a-a644-6b11451d1dc2:b47b3b4b5740def9e91c0cd2795c872dd4196d3c/Profil%20for%20CPP%20CPA%20%E2%80%93%20partnerprofiler%20og%20avtaler.pdf)

### ebXML hos Oasis
Oasis står bak ebXML-standarden, og spesifikasjonen er dokumentert i detalj på følgende sider:
* [ebMS Specification 2.0](https://www.oasis-open.org/committees/ebxml-msg/documents/ebMS_v2_0.pdf)
* [CPPA Specification 2.0](https://www.oasis-open.org/committees/ebxml-cppa/documents/ebcpp-2.0.pdf)


## Har du spørsmål? :dizzy_face:
Vi er hovedsakelig tilgjengelige på slack i følgende kanal:

- [#emottak](https://nav-it.slack.com/archives/C01P0FUC78A)

Alle spørsmål relatert til dette prosjektet er velkomne. Alternativt setter vi også pris på pull requests og
issues direkte her på Github. 
