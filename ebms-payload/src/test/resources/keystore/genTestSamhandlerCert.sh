# Gen CA (representerer f.eks. buypass, comfides etc)
openssl genrsa -passout pass:1234 -aes256 -out NavTest-RootCA.key 4096
openssl req -passin pass:1234 -x509 -new -nodes -key NavTest-RootCA.key -sha256 -days 9999 -out NavTest-RootCA.crt -subj '/CN=NavTest Certificate Authority/C=NO/ST=OSLO/L=OSLO/O=NavTest Certificate Authority' -config opensslExtensions.cnf -extensions extensions #-addext "certificatePolicies=2.5.29.32"
#openssl x509 -in NavTest-RootCA.crt -out NavTest-RootCA.pem -outform PEM

# Gen Samhandler key & certificate signing request
openssl req -new -newkey rsa:4096 -nodes -keyout samhandler.key -out samhandler.csr -subj '/CN=EXTERN_SAMHANDLER_AS/C=NO/ST=OSLO/L=OSLO/O=EXTERN_SAMHANDLER_AS'
openssl x509 -req -sha256 -days 365 -in samhandler.csr -signkey samhandler.key -out samhandler.pem

# Signer Samhandler Cert med CA + Extensions
openssl x509 -passin pass:1234 -req -in samhandler.csr -CA NavTest-RootCA.crt -CAkey NavTest-RootCA.key -CAcreateserial -out samhandlerRequest_CA-SIGNED.crt -days 730 -sha256 -extensions extensions -extfile opensslExtensions.cnf

# Gen NAV test-virksomhet key & CSR
openssl req -new -newkey rsa:4096 -nodes -keyout nav_virksomhet.key -out nav_virksomhet.csr -subj '/CN=TEST ARBEID OG VELFERDSETATEN/C=NO/ST=OSLO/L=OSLO/O=TEST ARBEIDS OG VELFERDSETATEN'
openssl x509 -req -sha256 -days 365 -in nav_virksomhet.csr -signkey nav_virksomhet.key -out nav_virksomhet.pem

# Signer NAV test-virksomhetssertifikat med CA
openssl x509 -passin pass:1234 -req -in nav_virksomhet.csr -CA NavTest-RootCA.crt -CAkey NavTest-RootCA.key -CAcreateserial -out nav_virksomhet_CA-SIGNED.crt -days 730 -sha256 -extensions extensions -extfile opensslExtensions.cnf

## Lagre Root CA i p12 format
openssl pkcs12 -password pass:1234 -passin pass:1234 -export -in NavTest-RootCA.crt -inkey NavTest-RootCA.key \
               -out NavTest-RootCA.p12 -name navtest-ca \
               -CAfile NavTest-RootCA.crt -caname NavTest-RootCA

echo "Lagre samhandler sertifikat med key i p12 format"
# Lagre samhandler sertifikat med key i p12 format
openssl pkcs12 -export -in samhandlerRequest_CA-SIGNED.crt -inkey samhandler.key \
               -out samhandler.p12 -name samhandler-2024 -password pass:1234 \
               -CAfile NavTest-RootCA.crt -caname NavTest-RootCA

echo "Lagre NAV test-virksomhetssertifikat i p12 format"
# Lagre NAV test-virksomhetssertifikat i p12 format
openssl pkcs12 -export -in nav_virksomhet_CA-SIGNED.crt -inkey nav_virksomhet.key \
               -out nav_virksomhet.p12 -name nav_virksomhet -password pass:1234 \
               -CAfile NavTest-RootCA.crt -caname NavTest-RootCA

# import samhandlers p12 til keystore (Slik at testen kan signere test meldinger, representerer ikke virkelighet)
keytool -importkeystore -noprompt \
        -deststorepass changeit -destkeypass changeit -destkeystore test_keystore2024.p12 \
        -srckeystore samhandler.p12 -srcstoretype PKCS12 -srcstorepass 1234 \
        -alias samhandler-2024

# import samhandlers p12 til truststore // todo importcert
keytool -importkeystore -noprompt \
        -deststorepass changeit -destkeypass changeit -destkeystore test_truststore2024.p12 \
        -srckeystore samhandler.p12 -srcstoretype PKCS12 -srcstorepass 1234 \
        -alias samhandler-2024

# import nav_virksomhet p12 til keystore
keytool -importkeystore -noprompt \
        -deststorepass changeit -destkeypass changeit -destkeystore test_keystore2024.p12 \
        -srckeystore nav_virksomhet.p12 -srcstoretype PKCS12 -srcstorepass 1234 \
        -alias nav_virksomhet

## import navtest-ca til keystore (Fordi vi må kunne signere test meldinger, representerer ikke virkelighet)
keytool -delete -alias navtest-ca -keystore test_keystore2024.p12 -deststorepass changeit
echo "Adding RootCA to keystore"
keytool -importkeystore -noprompt -srckeystore NavTest-RootCA.p12 \
        -deststorepass changeit -destkeypass changeit -destkeystore test_keystore2024.p12 \
        -srcstoretype PKCS12 -srcstorepass 1234 \
        -alias navtest-ca

# Add CA til truststore
keytool -delete -alias navtest-ca -keystore test_truststore2024.p12 -deststorepass changeit
echo "Adding RootCA to truststore"
keytool -importcert -noprompt -file NavTest-RootCA.crt \
        -deststorepass changeit -destkeypass changeit -destkeystore test_truststore2024.p12 \
        -alias navtest-ca