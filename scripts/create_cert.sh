#!/bin/bash

set -ex

#openssl genrsa -des3 -out $1.key 2048
#openssl req -x509 -new -nodes -key $1.key -sha256 -days 1825 -out $1.pem

#keytool -genkeypair -keyalg RSA -alias selfsigned -keystore keystore.jks -storepass password -validity 360 -keysize 2048

keytool -export -alias selfsigned -keystore keystore.jks -storepass password -rfc -file X509_certificate.cer

keytool -import -alias selfsigned -storepass password -file X509_certificate.cer