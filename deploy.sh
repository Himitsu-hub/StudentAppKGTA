#!/bin/bash
set -euo pipefail

# Usage (HTTP only):
#   ADMIN_PASSWORD='strong' ./deploy.sh
#
# Usage (HTTPS with Caddy + Let's Encrypt):
#   ADMIN_PASSWORD='strong' DOMAIN='apistudentkgtu.ru' ./deploy.sh
#
# Optional:
#   SERVER=root@157.22.186.149 REMOTE_DIR=/opt/studentapp ./deploy.sh

S="${SERVER:-root@157.22.186.149}"
REMOTE_DIR="${REMOTE_DIR:-/opt/studentapp}"
D="$(cd "$(dirname "$0")/server" && pwd)"
DOMAIN="${DOMAIN:-}"

if [[ -z "${ADMIN_PASSWORD:-}" ]]; then
  echo "ERROR: set ADMIN_PASSWORD env var before deploy."
  echo "Example: ADMIN_PASSWORD='...' DOMAIN='apistudentkgtu.ru' ./deploy.sh"
  exit 1
fi

echo "1/6 Copying code to $S:$REMOTE_DIR ..."
ssh "$S" "mkdir -p $REMOTE_DIR/uploads $REMOTE_DIR/scripts $REMOTE_DIR/static"
scp "$D/main.py" "$D/database.py" "$D/parser.py" "$D/scraper.py" "$D/news_scraper.py" \
    "$D/schedule_validator.py" \
    "$D/requirements.txt" "$D/Dockerfile" "$D/docker-compose.yml" "$D/Caddyfile" \
    "$S:$REMOTE_DIR/"
# Favicon / static assets for admin tab icon
if [[ -d "$D/static" ]]; then
  scp -r "$D/static/." "$S:$REMOTE_DIR/static/"
fi
if [[ -f "$D/scripts/harden_firewall.sh" ]]; then
  scp "$D/scripts/harden_firewall.sh" "$S:$REMOTE_DIR/scripts/"
  ssh "$S" "chmod +x $REMOTE_DIR/scripts/harden_firewall.sh"
fi

if compgen -G "$D/uploads/"*.xlsx > /dev/null; then
  echo "2/6 Copying Excel schedules..."
  scp "$D/uploads/"*.xlsx "$S:$REMOTE_DIR/uploads/"
else
  echo "2/6 No local Excel files — skipping"
fi

if [[ -f "$D/teachers.json" ]]; then
  scp "$D/teachers.json" "$S:$REMOTE_DIR/"
fi

echo "3/6 Writing remote .env..."
if [[ -n "$DOMAIN" && "$DOMAIN" != "localhost" ]]; then
  ssh "$S" "printf 'ADMIN_PASSWORD=%s\nDATABASE_URL=sqlite:///./schedule.db\nDOMAIN=%s\n' \
    $(printf %q "$ADMIN_PASSWORD") $(printf %q "$DOMAIN") > $REMOTE_DIR/.env"
  echo "   DOMAIN=$DOMAIN (HTTPS via Caddy)"
else
  ssh "$S" "printf 'ADMIN_PASSWORD=%s\nDATABASE_URL=sqlite:///./schedule.db\nDOMAIN=localhost\n' \
    $(printf %q "$ADMIN_PASSWORD") > $REMOTE_DIR/.env"
  echo "   DOMAIN not set — Caddy uses localhost (no public cert)"
fi

echo "4/6 Restarting Docker (API + Caddy)..."
# Old docker-compose 1.29 breaks on new Docker with KeyError: ContainerConfig.
# Prefer Docker Compose V2 plugin; remove old containers first.
# Free host ports 80/443 if nginx/apache hold them (needed for Caddy HTTPS).
ssh "$S" bash -s <<REMOTE
set -euo pipefail
cd "$REMOTE_DIR"

if docker compose version >/dev/null 2>&1; then
  DC="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  echo "ERROR: neither 'docker compose' nor 'docker-compose' found"
  exit 1
fi
echo "Using: \$DC"

# Stop common web servers that grab :80 / :443
systemctl stop nginx 2>/dev/null || true
systemctl disable nginx 2>/dev/null || true
systemctl stop apache2 2>/dev/null || true
systemctl disable apache2 2>/dev/null || true
systemctl stop httpd 2>/dev/null || true
# Anything else on 80/443
if command -v fuser >/dev/null 2>&1; then
  fuser -k 80/tcp 2>/dev/null || true
  fuser -k 443/tcp 2>/dev/null || true
fi

\$DC down --remove-orphans || true
docker rm -f studentapp-schedule-api-1 studentapp_schedule-api_1 studentapp-caddy-1 2>/dev/null || true
docker rm -f \$(docker ps -aq --filter name=studentapp) 2>/dev/null || true

\$DC up -d --build --force-recreate
\$DC ps

# Harden firewall: SSH + 80/443 open, 8000 closed from internet
if command -v ufw >/dev/null 2>&1; then
  echo "Hardening UFW (deny public :8000)..."
  ufw allow OpenSSH 2>/dev/null || ufw allow 22/tcp 2>/dev/null || true
  ufw allow 80/tcp 2>/dev/null || true
  ufw allow 443/tcp 2>/dev/null || true
  ufw deny 8000/tcp 2>/dev/null || true
  ufw --force enable 2>/dev/null || true
  ufw status numbered 2>/dev/null || true
else
  echo "ufw not found — skip firewall (API still bound to 127.0.0.1:8000 in Docker)"
fi
REMOTE

echo "5/6 Health check (local API)..."
ssh "$S" "sleep 2; curl -sf http://127.0.0.1:8000/health && echo && curl -sf http://127.0.0.1:8000/api/courses | head -c 200 && echo"

if [[ -n "$DOMAIN" && "$DOMAIN" != "localhost" ]]; then
  echo "6/6 Health check (HTTPS)..."
  sleep 5
  if ssh "$S" "curl -sf https://$DOMAIN/health"; then
    echo ""
    echo "DONE. HTTPS OK + port 8000 denied from outside (if ufw present)."
    echo "  API:   https://$DOMAIN/"
    echo "  Admin: https://$DOMAIN/admin  (backup ZIP available there)"
    echo "  Android BASE_URL: \"https://$DOMAIN/\""
  else
    echo ""
    echo "WARN: https://$DOMAIN/health failed."
    echo "  - dig @8.8.8.8 +short $DOMAIN  → must be server IP"
    echo "  - ufw allow 80/tcp && ufw allow 443/tcp"
    echo "  - ssh $S 'cd $REMOTE_DIR && docker compose logs caddy --tail 80'"
    echo "  Local API: http://127.0.0.1:8000 on the server"
    exit 1
  fi
else
  echo "6/6 Skip public HTTPS (no DOMAIN)."
  echo "DONE (HTTP mode)."
fi

