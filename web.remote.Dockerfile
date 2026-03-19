FROM node:18.20.8-alpine AS build

WORKDIR /build
ENV NODE_OPTIONS=--max-old-space-size=1536

COPY snowy-web/package*.json ./
COPY snowy-web/local_modules ./local_modules

RUN npm config set registry https://registry.npmmirror.com \
    && npm install

COPY snowy-web/ ./

RUN npm run build

FROM nginx:1.28.0-alpine

COPY snowy-web/etc/nginx/nginx.conf /etc/nginx/nginx.conf
COPY snowy-web/etc/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY snowy-web/etc/nginx/upstream.conf /etc/nginx/conf.d/upstream.conf
COPY snowy-web/etc/nginx/mime.types /etc/nginx/mime.types

COPY --from=build /build/dist/ /data/web/build/

EXPOSE 80
