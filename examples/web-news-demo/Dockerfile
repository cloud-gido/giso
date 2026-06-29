ARG NODE_IMAGE=node:20-alpine
ARG NGINX_IMAGE=nginx:alpine

FROM ${NODE_IMAGE} AS build
WORKDIR /src
COPY sdk/web ./sdk/web
COPY examples/web-news-demo ./examples/web-news-demo
WORKDIR /src/examples/web-news-demo
RUN npm install && npm run build

FROM ${NGINX_IMAGE}
COPY examples/web-news-demo/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/examples/web-news-demo/dist /usr/share/nginx/html
EXPOSE 5180
