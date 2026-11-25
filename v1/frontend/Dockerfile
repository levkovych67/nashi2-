FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL
ARG VITE_CDN_URL
ARG VITE_MAPTILER_API_KEY
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_CDN_URL=$VITE_CDN_URL \
    VITE_MAPTILER_API_KEY=$VITE_MAPTILER_API_KEY
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
