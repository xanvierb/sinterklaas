# Sinterklaas

Een Nederlandstalige familie-app voor verlanglijstjes, cadeaus reserveren en lootjes trekken.

## Lokaal starten

Gebruik Java 25 en de Maven-wrapper. Stel voor de eerste start op een lege database `SINT_ADMIN_USERNAME` en `SINT_ADMIN_PASSWORD` in als omgevingsvariabelen. Gebruik een uniek wachtwoord van minstens 12 tekens en maximaal 72 bytes. Een bestaand account wordt bij het starten niet gewijzigd.

```sh
./mvnw spring-boot:run
```

Open [localhost:8080](http://localhost:8080), meld je aan als beheerder en maak de eerste uitnodiging aan via **Beheer → Deelnemers → Uitnodigingen**. Deel de uitnodigingslink rechtstreeks met de deelnemer.

De lokale H2-database staat standaard in `data/` en foto's in `uploads/`. Beide mappen zijn lokaal. Flyway past migraties toe bij het starten.

## Testen

```sh
SINT_LOCAL_DB_URL='jdbc:h2:mem:sinterklaastest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE' ./mvnw test
```

## PostgreSQL

Gebruik `SPRING_PROFILES_ACTIVE=postgres` en stel de databasevariabelen uit [.env.example](.env.example) in. Spring leest de omgevingsvariabelen van het proces. Bewaar database en uploads op persistente opslag. Achter een HTTPS-reverseproxy stel je `SINT_BASE_URL` in op de openbare HTTPS-URL en zet je `SINT_SECURE_COOKIES=true`.
