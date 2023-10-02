# lokalt utviklingsmiljø

Oasis ebMS Spec:
https://www.oasis-open.org/committees/ebxml-msg/documents/ebMS_v2_0.pdf


Videre er målet å være i tråd med spesifikasjon fra e-helse ["Basert på ebXML"](https://git.sarepta.ehelse.no/publisert/standarder/raw/master/kravdokument/EBXMLrammeverk/HIS%201037_2011%20Rammeverk%20for%20meldingsutveksling%20v1.1%20-oppdatert.pdf)
Validering krav: https://git.sarepta.ehelse.no/publisert/standarder/raw/master/kravdokument/ValideringAvebXMLMeldinger/HITS%201172_2017%20Validering%20av%20ebXML-meldinger%20-oppdatert.pdf

## Start opp docker:
```
docker-compose up -d
```
## Containers:
### Epostserver: greenmail
* http://localhost:8081/
  ![img.png](readme-resources/greenmail-login.png)

### Epostklient: roundcube
* http://localhost:8000/
* user/pass: peder@epost.com/peder
* user/pass: thomas@epost.com/thomas
  ![img.png](readme-resources/roundcube.png)
  ![img.png](readme-resources/greenmail.png)

### MQ
https://localhost:9443/ibmmq/console/
* user/pass: admin/passw0rd
  ![img_1.png](readme-resources/mq-login.png)
  ![img_2.png](readme-resources/mq.png)


