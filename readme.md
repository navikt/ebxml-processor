# lokalt utviklingsmiljø

ebMS Spec:
https://www.oasis-open.org/committees/ebxml-msg/documents/ebMS_v2_0.pdf

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


