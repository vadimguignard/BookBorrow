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

Une seule commande lance les trois services (PostgreSQL, backend, frontend), dans cet ordre.

| Service  | URL                            |
|----------|--------------------------------|
| Frontend | http://localhost:4200          |
| Backend  | http://localhost:8080          |
| PostgreSQL | localhost:5432                |

Le serveur du frontend relaie `/api` vers le backend (variable `BACKEND_URL`, `http://backend:8080` dans compose) : le navigateur n'appelle que `http://localhost:4200`.

**Si vous aviez déjà lancé une ancienne version** (comptes par email), repartez d'une base propre : `docker compose down -v`.

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
- Authentification par username / mot de passe (voir plus bas).


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

## Authentification, comptes et profil

Pour réserver un livre il faut un compte. Le catalogue, les fiches et le calendrier restent publics.

### Pages (Frontend)

| Route          | Accès        | Contenu                                                                 |
|----------------|--------------|-------------------------------------------------------------------------|
| `/inscription` | visiteur     | username, mot de passe, confirmation, icône « œil », indicateur de force |
| `/connexion`   | visiteur     | username, mot de passe, icône « œil »                                    |
| `/profil`      | connecté     | informations du compte, bascule thème clair / sombre, livres réservés (statut + dates) |
| `/reservation/:reference` | connecté | Réservation au nom du compte                                    |

Un champ vide affiche un message sous le champ et **aucune requête n'est envoyée**. Un visiteur qui ouvre `/profil` est redirigé vers `/connexion`. Le thème choisi est mémorisé dans le navigateur.

### API (Backend)

| Méthode | Route                          | Accès    | Rôle                                                        |
|---------|--------------------------------|----------|-------------------------------------------------------------|
| POST    | `/api/auth/inscription`        | public   | `{ username, motDePasse, confirmationMotDePasse }` → 201 `{ token, utilisateur }` ; 400 règle non respectée ; 409 username déjà pris |
| POST    | `/api/auth/connexion`          | public   | `{ username, motDePasse }` → 200 `{ token, utilisateur }` ; 401 message générique |
| GET     | `/api/auth/moi`                | connecté | Profil de l'utilisateur                                     |
| GET     | `/api/reservations/mes`        | connecté | Ses réservations (livre, dates, état A_VENIR / EN_COURS / TERMINEE) |
| DELETE  | `/api/reservations/{id}`       | connecté | Annule l'une de ses réservations                            |
| POST    | `/api/livres/reservations`     | connecté | Réserve un livre : `{ reference, dateDebut, dateFin }`      |

### Règles et sécurité

- Username : 3 à 30 caractères (lettres, chiffres, `.`, `-`, `_`), unique sans tenir compte de la casse.
- Mot de passe : 8 à 64 caractères, au moins une majuscule et un chiffre ; doit être identique à sa confirmation.
- Mots de passe hachés avec **BCrypt**, jamais renvoyés par l'API.
- Connexion : « Nom d'utilisateur ou mot de passe incorrect. » dans les deux cas d'échec (username inconnu ou mauvais mot de passe).
- Jeton signé HMAC-SHA256 valable 24 h. **Avant toute mise en ligne**, définir `JWT_SECRET` (32 caractères minimum, aléatoire).
