
# Verktøy for å teste meldingsprosessering lokalt

### RunLocalContainers
Gir mulighet til å starte lokal Kafka, Postgres DB og Auth2-server.
NB: Når testing er ferdig, gi stoppsignal i konsollet så containerne stoppes kontrollert,
ikke bare drep prosessen.

### LocalApp
Kjører en modifisert utgave av ebms-async sin App,
hvor kall til eksterne tjenester som Duplikatsjekk og Sendin mockes til å gi ønsket resultat.
Bruker lokal Kafka/Postgres etc som kjøres i RunLocalContainers.

### LocalTestClient
Utility som kan modifiseres og kjøres for å gi input eller sjekke output fra prosessering, f.eks.
- sende inn melding på payload-inn topic
- sjekke innhold i kafka-topics eller DB-tabeller


### Slik foregår prosessering
**Input** leses fra `team-emottak.smtp.in.ebxml.payload` eller `.signal` :
- `requestId = record.key()`,
- `document = record.value().createDocument()`

`value` må altså være OK ebxml. Henter også attachments fra SMTP service (http)

**Signal**: validerer id'er + adresse mot CPA-repo (http), validerer evt signatur

**Payload**: sjekker/registrerer duplikat, val mot CPA-repo+sign, sender til PayloadProcessing (http), får ny payload,
sendes videre til SendIn (http), er da XML, får tilbake respons.

**Respons** valideres mot CPA-repo, kan sendes til PayloadProcessing (http), evt vedlegg lagres i DB, sendes til `out.payload`:
- `key = signedEbmsDocument.requestId`,
- `value = signedEbmsDocument.document.toByteArray()`,

Konvolutt er med på in/out-køer, kun payload til/fra SendIn

`PayloadProcessing` IN kan være juridisk logging, dekryptering, dekomprimering, validering av signatur for selve payload, henter ut ID

Fra SMTP får man en liste av `AsyncPayload`:
- `val referenceId: Uuid`,
- `val contentId: String`,
- `val contentType: String`,
- `val content: ByteArray`

På `IN.xml` legges XML i klartekst, som payloadmessage.xml, inneholder SOAP:Envelope. 
`Key` vil bli brukt til å slå opp attachments via SMTP
`messageId`, `convId` og `CPA-id` hentes fra soap-header (går til dupl-sjekk ++)
`cert-ID` hentes fra CPA-repo, `content-type + id` fra asyncpayload
`addressing` fra from/to i header, `service/action` også fra header

`Ack` får ny requestId/messageId, men samme convId, og `refTo` peker på messageId, `from/to` snudd, action Ack
Uvisst med respons, må evt finne ekte respons fra sendin
