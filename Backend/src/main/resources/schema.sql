-- Creation de la table des livres si elle n'existe pas encore.
-- Les scripts SQL sont executes avant que Hibernate ne cree le schema, on
-- declare donc la table nous-memes (CREATE TABLE IF NOT EXISTS rend
-- l'operation idempotente).
--
-- Cette table porte les donnees METIER de notre bibliotheque : statut
-- (LIBRE / RESERVE) et date de disponibilite. Open Library ne fournit
-- que les informations descriptives (titre, auteur, annee, couverture).

CREATE TABLE IF NOT EXISTS livre (
    id                  BIGSERIAL PRIMARY KEY,
    reference           VARCHAR(64)  NOT NULL UNIQUE,
    titre               VARCHAR(512) NOT NULL,
    auteur              VARCHAR(255) NOT NULL,
    annee_publication   INTEGER,
    cover_id            BIGINT,
    statut              VARCHAR(16)  NOT NULL DEFAULT 'LIBRE',
    date_disponibilite  DATE
);
