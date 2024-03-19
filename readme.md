[![Build and deploy ebms-provider](https://github.com/navikt/ebxml-processor/actions/workflows/build.yaml/badge.svg)](https://github.com/navikt/ebxml-processor/actions/workflows/build.yaml)

# eMottak ebXML
Dette prosjektet håndterer meldinger mottatt på ebXML-standarden.

Prosjektet består av tre hovedmoduler for behandling av ebXML-meldinger.
I tillegg er det en modul som lytter på epost, og router meldinger til ebMS-Provider for behandling.
* EbMS Provider
* EbMS Payload behandling
* CPA Repo
* SMTP-Listener

### EbMS Provider
Selve motoren i ebMS-håndteringen. Det er her ebMS-spesifikasjonen er implementert.
Validerer ebXML-konvolutten, pakker ut fagmeldingen og sender den til ebMS-payload.

### EbMS Payload
Behandling av fagmeldingene som er pakket inn i ebXML. Mottar meldinger fra ebMS-provider, og
validerer at innholdet er en korrekt fagmelding og er klar for videreformidling til fagsystem.

### CPA Repo
Holder på alle godkjente CPAer. Mottar ebXML-header informasjon fra ebMS-provider, og validerer innholdet mot
relevant CPA.

### SMTP Listener
Liten modul som henter eposter fra definert innboks, og router meldingene videre til ebMS-Provider.
Trigges av periodisk NAISJob, og leser alle mail i innboksen ved aktivering.


## Utvikling
### Teknologi
Alle modulene kjører som selvstendig applikasjoner, og er bygd opp av følgende teknologier
* Kotlin
* Ktor
* Gradle


### Bygg prosjektet
For å bygge prosjektet brukes gradle.

```
./gradlew build
```

Noen av testene bruker [testcontainers](https://github.com/testcontainers/testcontainers-java) for å bygge opp et mer komplett
kjøretidsmiljø. Disse er avhengig av et fungerende docker miljø. For eksempel docker eller [colima](https://github.com/abiosoft/colima) på mac.

### Oppsett av Github PAT
For å kunne dra inn de riktige dependenciene, må man opprette en github PAT.
Denne trenger rettigheten read:packages og må autoriseres for tilgang til organisasjonen [navikt](https://github.com/navikt).
![img](docs/img/read_packages.png)

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
