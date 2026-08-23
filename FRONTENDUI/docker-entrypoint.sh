#!/bin/sh

echo "Backend URL = ${BACKEND_URL}"

envsubst '${BACKEND_URL}' \
< /etc/nginx/templates/nginx.conf.template \
> /etc/nginx/conf.d/default.conf

exec nginx -g 'daemon off;'