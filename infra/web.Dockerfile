# Production build of web/ behind nginx (compose service "web", profile "web"). No VITE_API_MOCK: no test files in dist.
FROM node:24 AS build
WORKDIR /src
ARG VITE_API_BASE_URL=/api/v2
ARG VITE_SITE_BASE_URL=http://localhost:4175
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL VITE_SITE_BASE_URL=$VITE_SITE_BASE_URL
COPY package.json package-lock.json ./
COPY web/package.json web/
RUN npm ci
COPY contracts contracts
COPY web web
RUN npm run build -w web

FROM nginx:1.27-alpine
COPY infra/nginx/web.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/web/dist /usr/share/nginx/html
