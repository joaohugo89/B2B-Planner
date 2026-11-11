# syntax=docker/dockerfile:1

# ---- build ----------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Vite grava a base da API no bundle, então ela entra no build, não no runtime.
# Domínios separados: --build-arg VITE_API_BASE_URL=https://api.exemplo.com/api
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ---- runtime --------------------------------------------------------------
FROM nginx:1.27-alpine

# Como template: a imagem oficial troca ${BACKEND_URL} pelo valor do ambiente na partida.
# Só variáveis definidas são trocadas, então $host, $scheme etc. do nginx ficam intactos.
ENV BACKEND_URL=http://backend:8081
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80
