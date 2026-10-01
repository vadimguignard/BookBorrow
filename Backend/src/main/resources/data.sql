-- Catalogue initial de la bibliotheque.
--
-- Les references sont les cles Open Library des livres : elles servent de clef
-- de jointure entre notre base et le catalogue Open Library.
--
-- IMPORTANT : ces lignes ne portent QUE des donnees metier de NOTRE application.
-- Le statut est LIBRE pour tous les livres, aucune reservation n'existe encore
-- (la logique de reservation sera implementee ulterieurement). Le statut et la
-- date de disponibilite ne sont jamais fournis par Open Library.
--
-- ON CONFLICT DO NOTHING : le catalogue initial n'est insere qu'une seule fois,
-- meme si le conteneur est redemarre avec le meme volume PostgreSQL.

INSERT INTO livre (reference, titre, auteur, annee_publication, cover_id, statut, date_disponibilite) VALUES
    ('/works/OL82563W',  'Harry Potter and the Philosopher''s Stone', 'J. K. Rowling',  1997, 15155833, 'LIBRE', NULL),
    ('/works/OL27482W',  'The Hobbit',                              'J.R.R. Tolkien',  1937, 14627509, 'LIBRE', NULL),
    ('/works/OL893414W', 'Dune',                                   'Frank Herbert',   1965, 11481354, 'LIBRE', NULL),
    ('/works/OL1168083W','Nineteen Eighty-Four',                   'George Orwell',   1949, 9267242,  'LIBRE', NULL),
    ('/works/OL66554W',  'Pride and Prejudice',                    'Jane Austen',     1813, 14348537, 'LIBRE', NULL)
ON CONFLICT (reference) DO NOTHING;
