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

## Container

### Docker Compose met PostgreSQL

Kopieer `.env.example` naar `.env`. Vul `SINT_DB_PASSWORD` in en stel voor een lege database ook `SINT_ADMIN_USERNAME` en `SINT_ADMIN_PASSWORD` in. Compose stelt de database-URL en uploadmap in via het voorbeeldbestand.

```sh
docker compose -f compose.example.yaml up --build -d
docker compose -f compose.example.yaml logs -f app
```

Open [localhost:8080](http://localhost:8080) zodra de app is gestart. Stop de services met `docker compose -f compose.example.yaml down`. De database en foto's blijven bewaard in Docker-volumes.

### Publiceren naar Docker Hub

Maak op Docker Hub een repository met de naam `sinterklaas`. Stel in de GitHub-repository onder **Settings → Secrets and variables → Actions** de variabele `DOCKERHUB_USERNAME` en het geheim `DOCKERHUB_TOKEN` in. Gebruik een Docker Hub-toegangstoken met schrijfrechten.

Push een versietag vanaf de commit die je wilt publiceren. GitHub Actions bouwt de image en publiceert die met dezelfde tag.

```sh
git tag v1.0.0
git push origin v1.0.0
```

Gebruik bij een privé-image eerst `docker login` op de host waar je de app draait. Stel `SINT_IMAGE` in `.env` in op de gepubliceerde image, bijvoorbeeld `jouwgebruikersnaam/sinterklaas:v1.0.0`, en start Compose met:

```sh
docker compose -f compose.example.yaml up --no-build --pull always -d
```

### Losse container

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
