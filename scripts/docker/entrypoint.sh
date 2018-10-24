#!/usr/bin/env bash
set -eu

function log {
    (>&2 echo "$@")
}
log "templating config"
python3 /root/apacheds_configure.py - <<RENDERSPEC
iam_ldap.conf.j2     /etc/iam_ldap.conf
log4j.properties.j2  /opt/apacheds/instances/default/conf/log4j.properties
RENDERSPEC

# run
if [[ $# -ne 0 ]]; then
    log running "$@"
    exec "$@"
elif [[ "${1:-}" == "apacheds" ]]; then
    log running apacheds
    exec bash /opt/apacheds/bin/apacheds.sh console
else
    exec bash
fi
