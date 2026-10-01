# ---- Etape 1 : build Angular (navigateur + serveur SSR) ----
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm install --no-audit --no-fund

COPY . .
RUN npm run build

# ---- Etape 2 : image d'execution ----
FROM node:22-alpine
WORKDIR /app

ENV NODE_ENV=production \
    PORT=4000 \
    BACKEND_URL=http://backend:8080

# Le serveur SSR importe express et @angular/* : dependances de production uniquement.
COPY package.json package-lock.json ./
RUN npm install --omit=dev --no-audit --no-fund && npm cache clean --force
COPY --from=build /app/dist/frontend ./dist/frontend

USER node
EXPOSE 4000
CMD ["node", "dist/frontend/server/server.mjs"]
