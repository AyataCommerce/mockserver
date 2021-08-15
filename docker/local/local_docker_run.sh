#!/usr/bin/env bash
# shellcheck disable=SC2046

docker stop mockserver_local_snapshot || true
docker run \
  --rm \
  --env MOCKSERVER_OUTPUT_MEMORY_USAGE_CSV=true \
  --env MOCKSERVER_MEMORY_USAGE_DIRECTORY=/logs \
  --env MOCKSERVER_PROPERTY_FILE=/config/mockserver.properties \
  --env MOCKSERVER_BULK_INITIALIZATION_JSON_PATH=/api-mock \
  -v $(pwd):/logs \
  -v /Users/stanleysj/GIT/AYATA/dm-mock-api-svc/config/:/config \
  -v /Users/stanleysj/GIT/AYATA/dm-mock-api-svc/api-mock:/api-mock \
  --name mockserver_local_snapshot \
  -p 1080:1080 \
  ayatacommerce/mockserver:local-snapshot -logLevel INFO -serverPort 1080

