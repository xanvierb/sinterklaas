# Sinterklaas

Een Nederlandstalige familie-app voor verlanglijstjes, cadeaus reserveren en lootjes trekken.

## Starten met Docker Compose

Gebruik Docker met de Compose-plugin en voer de opdrachten uit vanuit de projectmap. Maak bij de eerste installatie een lokaal configuratiebestand aan.

```sh
cp .env.example .env
```

Open `.env` en vul `SINT_DB_PASSWORD` in. Stel bij een lege database ook `SINT_ADMIN_USERNAME` en `SINT_ADMIN_PASSWORD` in. Kies voor het beheerdersaccount een uniek wachtwoord van minstens 8 tekens en maximaal 72 bytes. Compose stelt de database-URL en uploadmap zelf in.

Compose haalt standaard `xanvier/sinterklaas:latest` op. Gebruik bij een privé-image eerst `docker login`. Wil je een specifieke gepubliceerde versie draaien, zet dan bijvoorbeeld `SINT_IMAGE=xanvier/sinterklaas:v1.0.0` in `.env`.

```sh
docker compose -f compose.example.yaml up -d
docker compose -f compose.example.yaml logs -f app
```

Zodra de app is gestart, open je [localhost:8080](http://localhost:8080) en meld je je aan als beheerder. Maak de eerste uitnodiging aan via **Beheer → Deelnemers → Uitnodigingen** en deel de link rechtstreeks met de deelnemer. Flyway past de databasemigraties bij het opstarten toe.

Met `Ctrl+C` verlaat je de logweergave. Stop de app en PostgreSQL met de volgende opdracht. De database en foto's blijven bewaard in Docker-volumes.

```sh
docker compose -f compose.example.yaml down
```

## Lokaal ontwikkelen met Maven

Gebruik Java 25 en de Maven-wrapper. Stel voor de eerste start op een lege database `SINT_ADMIN_USERNAME` en `SINT_ADMIN_PASSWORD` in als omgevingsvariabelen. Gebruik een uniek wachtwoord van minstens 8 tekens en maximaal 72 bytes. Een bestaand account wordt bij het starten niet gewijzigd.

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

## Publiceren naar Docker Hub

Maak op Docker Hub een repository met de naam `sinterklaas`. Stel in de GitHub-repository onder **Settings → Secrets and variables → Actions** de variabele `DOCKERHUB_USERNAME` en het geheim `DOCKERHUB_TOKEN` in. Gebruik een Docker Hub-toegangstoken met schrijfrechten.

Voer het releasescript uit vanaf de commit die je wilt publiceren. Het toont de vijf recentste versietags en vraagt om een nieuwe versie, bijvoorbeeld `1.0.2`. GitHub Actions bouwt de image en publiceert die met dezelfde tag en als `latest`.

```sh
./release.sh
```

## Losse container

Bouw de image vanuit de projectmap.

```sh
docker build -t sinterklaas:local .
```

De container gebruikt standaard het `postgres`-profiel en luistert op poort 8080. Geef de PostgreSQL-instellingen uit `.env.example` door bij het starten. Gebruik een databaseadres dat vanuit de container bereikbaar is. Koppel een volume aan `/app/uploads` voor de foto's. De container draait als gebruiker en groep 10001. Geef die gebruiker schrijfrechten als je een bestaande hostmap koppelt.

Voor een lokale proef zonder PostgreSQL kun je H2 gebruiken. Stel voor de eerste start de beheerdervariabelen in zoals hierboven beschreven.

```sh
docker run --rm --name sinterklaas -p 127.0.0.1:8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e SINT_ADMIN_USERNAME -e SINT_ADMIN_PASSWORD \
  --mount source=sinterklaas-data,target=/app/data \
  --mount source=sinterklaas-uploads,target=/app/uploads \
  sinterklaas:local
```
