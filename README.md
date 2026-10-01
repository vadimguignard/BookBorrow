# Biblio Online

Application web de gestion et de réservation de livres.

## Architecture

- Frontend : Angular 21 + TypeScript
- Backend : Spring Boot 4 + Java 25
- Base de données : PostgreSQL
- API : REST

## Structure

```text
biblio-online/
├── Backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/biblio/backend/
│       │   ├── config/         WebConfig (CORS), SecurityConfig
│       │   ├── controller/     LivreController, HealthController, ApiExceptionHandler
│       │   ├── domain/         Livre, Statut
│       │   ├── dto/            LivreDto, PageLivresDto, LivreCatalogue, SearchDoc, SearchResponse
│       │   ├── repository/     LivreRepository
│       │   └── service/        LivreService, CatalogueCache, OpenLibraryService
│       └── resources/
│           ├── application.properties
│           ├── schema.sql
│           └── data.sql
├── Frontend/
│   ├── proxy.conf.json
│   └── src/app/
│       ├── livre.model.ts
│       ├── livre.service.ts
│       ├── app.html / app.css
│       └── app.ts
├── docker-compose.yml
└── README.md
```

## Lancer le projet

```bash
docker compose up --build
```

| Service  | URL                            |
|----------|--------------------------------|
| Frontend | http://localhost:4200          |
| Backend  | http://localhost:8080          |
| PostgreSQL | http://localhost:5432         |

Arrêter : `docker compose down` (et `docker compose down -v` pour supprimer le volume PostgreSQL).

### Frontend en développement

```bash
cd Frontend
npm install
npm start
```

`proxy.conf.json` relaie `/api` vers `http://localhost:8080`.

## API backend

```bash
curl "http://localhost:8080/api/livres?page=1"
```

| Paramètre | Défaut | Description                             |
|-----------|--------|-----------------------------------------|
| `q`       | —      | Recherche sur le titre OU l'auteur      |
| `auteur`  | —      | Filtre par auteur                       |
| `titre`   | —      | Filtre par titre                        |
| `annee`   | —      | Filtre par année de publication         |
| `page`    | `1`    | Numéro de page (25 livres par page)     |

## Page d'accueil

Affiche le catalogue avec titre, auteur, statut (LIBRE / RÉSERVÉ) et
disponibilité, paginé par 25 livres.

**Recherche et filtres se combinent en AND**, avec une logique OR à
l'intérieur de la recherche libre :

```
(titre contient "q" OU auteur contient "q")
ET auteur LIKE filtreAuteur
ET titre  LIKE filtreTitre
ET année = annee
```

Toutes les comparaisons sont insensibles à la casse.

## Deux sources de données

| Information                            | Source          |
|----------------------------------------|-----------------|
| titre, auteur, année, couverture        | Open Library    |
| statut LIBRE/RÉSERVÉ, date de disponibilité | PostgreSQL    |

**Open Library ne fournit jamais le statut ni la date de disponibilité** :
ces informations appartiennent à l'application et proviennent uniquement de
la base de données. Un livre présent dans le catalogue mais absent de la base
est considéré LIBRE.

Couvertures : `https://covers.openlibrary.org/b/id/{cover_i}-M.jpg`
(si absent → placeholder dans l'interface).

### Diversification du catalogue

L'API Open Library rend jusqu'à 100 documents par page et peut renvoyer
plusieurs livres du même auteur ou du même titre. Après récupération, le
catalogue est donc **diversifié** : ni deux livres du même auteur, ni deux
livres de même titre. Un livre absent de couverture est écarté en priorité.

Résultat : 100 livres tous distincts, soit 4 pages de 25.

### Cache

L'API Open Library met 5 à 10 secondes pour répondre. Le catalogue est donc
conservé 5 minutes en mémoire côté backend : changer de page ou modifier un
filtre est ensuite instantané.

```yaml
catalogue.cache-duree-secondes=300
```

## Choix techniques

- **Backend** : Spring Boot 4.1.1, Java 25, `RestClient`, JPA/Hibernate,
  `WebConfig` pour le CORS, `SecurityConfig` (la page d'accueil est publique,
  l'authentification viendra avec les réservations).
- **Frontend** : Angular 21, un seul composant standalone avec signals,
  SSR activé par le projet.
- **Pagination** : découpage en mémoire côté backend après filtrage, ce qui
  garde le total et le nombre de pages exacts.
- Pas d'authentification : la réservation demande un nom et un prénom.


## Réservation d'un livre

Depuis la fiche d'un livre, le bouton **Réserver** ouvre `/reservation/<référence>` :
formulaire (nom, prénom), deux calendriers (début / fin) où les jours déjà pris sont
barrés, récapitulatif puis confirmation. Les deux bornes sont **incluses** et la fin
doit être après le début.

| Méthode | URL | Rôle |
|---|---|---|
| `GET`  | `/api/livres/reservations?reference=/works/OL82563W` | périodes déjà réservées (`dateDebut`, `dateFin`) |
| `POST` | `/api/livres/reservations` | corps `{ reference, prenom, nom, dateDebut, dateFin }` |

Réponses du `POST` : `201` enregistrée · `409` période déjà (en partie) réservée ·
`400` dates incohérentes (passé, fin avant début) ou champ manquant · `404` livre inconnu
d'Open Library · `502` Open Library injoignable.

Fonctionnement :

- Table `reservation` (créée par Hibernate) liée à `livre`. Un livre du catalogue absent
  de notre base y est **ajouté à la première réservation** (titre, auteur, année, couverture
  repris d'Open Library).
- Le **statut** et la **date de disponibilité** du livre restent ceux lus par le catalogue :
  `RESERVE` si une réservation couvre aujourd'hui, avec comme date le lendemain de la
  dernière réservation de la chaîne ; sinon `LIBRE`. Ils sont mis à jour à chaque
  réservation, au démarrage, puis chaque nuit à 00h05 (`StatutReservationJob`).
- Le livre est verrouillé pendant la vérification : deux réservations simultanées ne
  peuvent pas obtenir la même période.
