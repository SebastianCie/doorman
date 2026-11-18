#!/usr/bin/env bash
# Doorman nach OpenShift deployen (oder nach einem Cluster-Reset wiederherstellen).
#
#   1. oc login --token=... --server=...        (Token aus der OpenShift-Konsole: "Copy login command")
#   2. ./deploy/deploy.sh
#
# Liest deploy/.env (Vorlage: deploy/.env.example). Legt im aktuellen OpenShift-Projekt an bzw. aktualisiert:
# Secret "doorman", Deployment, Service, Route. Mehrfach ausführbar.
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/.." && pwd)"
ENV_FILE="$DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Fehlt: $ENV_FILE  →  cp deploy/.env.example deploy/.env und ausfüllen." >&2
  exit 1
fi
command -v oc >/dev/null || { echo "Das oc-CLI ist nicht installiert (brew install openshift-cli)." >&2; exit 1; }
oc whoami >/dev/null 2>&1 || { echo "Nicht bei OpenShift angemeldet. Zuerst: oc login --token=... --server=..." >&2; exit 1; }

set -a; source "$ENV_FILE"; set +a
: "${DATABASE_URL:?DATABASE_URL fehlt in deploy/.env}"
IMAGE="${IMAGE:-ghcr.io/sebastiancie/doorman:latest}"

# Session-Schlüssel einmalig erzeugen und in .env merken (bleibt über Cluster-Resets gleich)
if [[ -z "${SESSION_ENCRYPTION_KEY:-}" ]]; then
  SESSION_ENCRYPTION_KEY="$(openssl rand -base64 32 | tr -d '\n')"
  if grep -q '^SESSION_ENCRYPTION_KEY=' "$ENV_FILE"; then
    sed -i.bak "s|^SESSION_ENCRYPTION_KEY=.*|SESSION_ENCRYPTION_KEY=$SESSION_ENCRYPTION_KEY|" "$ENV_FILE" && rm -f "$ENV_FILE.bak"
  else
    echo "SESSION_ENCRYPTION_KEY=$SESSION_ENCRYPTION_KEY" >> "$ENV_FILE"
  fi
  echo "→ SESSION_ENCRYPTION_KEY erzeugt und in deploy/.env gespeichert."
fi

# Neon-URL (postgresql://user:pw@host/db?...) → JDBC-URL + Benutzer + Passwort.
# Der "-pooler"-Host wird durch die direkte Verbindung ersetzt: Flyway-Migrationen brauchen eine echte Sitzung.
PARSE_DB_URL="$(cat <<'PY'
import sys, urllib.parse as u
p = u.urlparse(sys.argv[1])
if p.scheme not in ("postgres", "postgresql"):
    sys.exit("DATABASE_URL muss mit postgresql:// beginnen")
host = (p.hostname or "").replace("-pooler.", ".")
port = ":%d" % p.port if p.port else ""
q = dict(u.parse_qsl(p.query))
jdbc = "jdbc:postgresql://" + host + port + p.path + "?" + u.urlencode({"sslmode": q.get("sslmode", "require")})
print(jdbc)
print(u.unquote(p.username or ""))
print(u.unquote(p.password or ""))
PY
)"
PARSED="$(python3 -c "$PARSE_DB_URL" "$DATABASE_URL")"
DB_URL="$(printf '%s\n' "$PARSED" | sed -n 1p)"
DB_USER="$(printf '%s\n' "$PARSED" | sed -n 2p)"
DB_PASSWORD="$(printf '%s\n' "$PARSED" | sed -n 3p)"

NS="$(oc project -q)"
echo "→ Projekt: $NS   Image: $IMAGE"

oc create secret generic doorman \
  --from-literal=DB_URL="$DB_URL" \
  --from-literal=DB_USER="$DB_USER" \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=SESSION_ENCRYPTION_KEY="$SESSION_ENCRYPTION_KEY" \
  --from-literal=INITIAL_ADMIN_PASSWORD="${INITIAL_ADMIN_PASSWORD:-}" \
  --dry-run=client -o yaml | oc apply -f -

sed "s|image: .*|image: $IMAGE|" "$ROOT/openshift/deployment.yaml" | oc apply -f -
oc apply -f "$ROOT/openshift/service.yaml" -f "$ROOT/openshift/route.yaml"

# Neues Image holen und neu starten, auch wenn sich am Deployment nichts geändert hat
oc rollout restart deployment/doorman >/dev/null
echo "→ Warte auf Start …"
if ! oc rollout status deployment/doorman --timeout=180s; then
  echo "Start fehlgeschlagen. Logs:" >&2
  oc logs deployment/doorman --tail=50 >&2 || true
  exit 1
fi

HOST="$(oc get route doorman -o jsonpath='{.spec.host}')"
echo
echo "✓ Doorman läuft: https://$HOST"
oc logs deployment/doorman 2>/dev/null | grep -i "admin" | grep -i "passwort" || true
