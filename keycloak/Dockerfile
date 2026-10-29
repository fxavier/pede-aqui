# Local Keycloak image = upstream 26.8.1 + the Pede Aqui SMS-OTP provider (LAC-18, ADR-0004).
# Built by `docker compose up -d keycloak` (compose `build:`); rebuild after provider changes with
# `docker compose build keycloak`. Production images must use the same two stages and then `kc.sh build`.
FROM maven:3.9.9-eclipse-temurin-21 AS provider
WORKDIR /src
COPY extensions/sms-otp/pom.xml ./pom.xml
RUN mvn -q -B dependency:go-offline
COPY extensions/sms-otp/src ./src
RUN mvn -q -B -DskipTests package

FROM quay.io/keycloak/keycloak:26.8.1
COPY --from=provider /src/target/pede-aqui-keycloak-sms-otp.jar /opt/keycloak/providers/pede-aqui-keycloak-sms-otp.jar
