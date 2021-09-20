#!/bin/sh

set -x

# Create a new image version with latest code changes.
docker build . --tag pleo-antaeus

# Build the code.
docker run \
  --publish 7000:7000 \
  --rm \
  --interactive \
  --tty \
  --volume pleo-antaeus-build-cache:/root/.gradle \
  --env PAYMENT_MAX_RETRIES=3 \
  --env BILLING_SCHEDULER_CRON='0 0/1 * 1/1 * ? *' \
  pleo-antaeus
