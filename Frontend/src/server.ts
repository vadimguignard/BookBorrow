import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { join } from 'node:path';

/** Adresse du backend Spring Boot (dans docker compose : http://backend:8080). */
const BACKEND_URL = process.env['BACKEND_URL'] ?? 'http://localhost:8080';

/** En-tetes propres a une connexion : ils ne doivent pas etre recopies d'un cote a l'autre. */
const EN_TETES_CONNEXION = new Set(['connection', 'keep-alive', 'transfer-encoding', 'upgrade', 'host']);

const browserDistFolder = join(import.meta.dirname, '../browser');

const app = express();
const angularApp = new AngularNodeAppEngine();

/**
 * Relais /api -> backend.
 *
 * Le navigateur n'appelle jamais le backend directement : il appelle ce serveur
 * (meme origine, donc pas de CORS), qui transmet la requete telle quelle, en-tete
 * Authorization compris, puis renvoie la reponse. Equivalent de proxy.conf.json
 * en developpement (ng serve).
 */
app.use('/api', async (req, res) => {
  try {
    const entetes = new Headers();
    for (const [nom, valeur] of Object.entries(req.headers)) {
      if (valeur === undefined || EN_TETES_CONNEXION.has(nom)) continue;
      entetes.set(nom, Array.isArray(valeur) ? valeur.join(', ') : valeur);
    }

    let corps: Buffer | undefined;
    if (req.method !== 'GET' && req.method !== 'HEAD') {
      const morceaux: Buffer[] = [];
      for await (const morceau of req) morceaux.push(morceau as Buffer);
      corps = Buffer.concat(morceaux);
    }

    const reponse = await fetch(`${BACKEND_URL}${req.originalUrl}`, {
      method: req.method,
      headers: entetes,
      body: corps ? new Uint8Array(corps) : undefined,
      redirect: 'manual',
    });

    res.status(reponse.status);
    reponse.headers.forEach((valeur, nom) => {
      // fetch a deja decompresse le corps : on ne recopie ni l'encodage ni la longueur.
      if (EN_TETES_CONNEXION.has(nom) || nom === 'content-encoding' || nom === 'content-length') return;
      res.setHeader(nom, valeur);
    });
    res.end(Buffer.from(await reponse.arrayBuffer()));
  } catch {
    res.status(502).json({ error: 'Le serveur est injoignable. Réessayez dans un instant.' });
  }
});

/**
 * Serve static files from /browser
 */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
  }),
);

/**
 * Handle all other requests by rendering the Angular application.
 */
app.use((req, res, next) => {
  angularApp
    .handle(req)
    .then((response) =>
      response ? writeResponseToNodeResponse(response, res) : next(),
    )
    .catch(next);
});

/**
 * Start the server if this module is the main entry point, or it is ran via PM2.
 * The server listens on the port defined by the `PORT` environment variable, or defaults to 4000.
 */
if (isMainModule(import.meta.url) || process.env['pm_id']) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }

    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}

/**
 * Request handler used by the Angular CLI (for dev-server and during build) or Firebase Cloud Functions.
 */
export const reqHandler = createNodeRequestHandler(app);
