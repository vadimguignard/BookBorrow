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

-- ---------------------------------------------------------------------------
-- Migration des bases creees AVANT le passage a l'authentification par username.
-- Chaque instruction est sans effet sur une base neuve (tables absentes ou
-- colonnes deja conformes) : Hibernate cree ensuite les tables manquantes.
-- Les anciens comptes (email / prenom / nom) perdent ces colonnes : ils n'ont pas
-- de username et ne peuvent plus se connecter. Pour repartir de zero :
--   docker compose down -v
-- ---------------------------------------------------------------------------
ALTER TABLE IF EXISTS utilisateur DROP COLUMN IF EXISTS prenom;
ALTER TABLE IF EXISTS utilisateur DROP COLUMN IF EXISTS nom;
ALTER TABLE IF EXISTS utilisateur DROP COLUMN IF EXISTS email;
ALTER TABLE IF EXISTS utilisateur ADD COLUMN IF NOT EXISTS username VARCHAR(30);
ALTER TABLE IF EXISTS reservation ALTER COLUMN prenom DROP NOT NULL;
ALTER TABLE IF EXISTS reservation ALTER COLUMN nom DROP NOT NULL;
