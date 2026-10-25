# syntax=docker/dockerfile:1.7

FROM node:24.22.1-alpine3.24 AS build
WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci

COPY angular.json ngsw-config.json tsconfig*.json ./
COPY public ./public
COPY src ./src
RUN npm run build

FROM nginx:1.30.6-alpine3.24

RUN apk upgrade --no-cache

ARG INVAULT_BUILD_REVISION=unversioned
LABEL org.opencontainers.image.revision="$INVAULT_BUILD_REVISION"

COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/frontend/browser/ /usr/share/nginx/html/

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget --quiet --spider http://localhost:8080/healthz || exit 1
