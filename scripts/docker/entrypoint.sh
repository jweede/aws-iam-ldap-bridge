#!/usr/bin/env bash
set -eux

if [[ $# -ne 0 ]]; then
    exec "$@"
elif [[ "${1:-}" == "apacheds" ]]; then
    exec bash /opt/apacheds/bin/apacheds.sh console
else
    exec bash
fi
