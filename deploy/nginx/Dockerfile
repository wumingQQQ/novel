FROM nginx:1.29-alpine

RUN rm -rf /usr/share/nginx/html/*

COPY artifacts/web/ /usr/share/nginx/html/
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 80
