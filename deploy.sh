#!/bin/bash
set -euo pipefail

# Usage (HTTP only):
#   ADMIN_PASSWORD='strong' ./deploy.sh
#
# Usage (HTTPS with Caddy + Let’s Encrypt):
#   ADMIN_PASSWORD='strong' DOMAIN='apistudentkgtu.ru' ./deploy.sh
#
# Code-only (no docker rebuild — use when PyPI is slow / site is down):
#   SKIP_BUILD=1 ADMIN_PASSWORD='strong' DOMAIN='apistudentkgtu.ru' ./deploy.sh
#
# Optional:
#   SERVER=root@157.22.186.149 REMOTE_DIR=/opt/studentapp ./deploy.sh

S="${SERVER:-root@157.22.186.149}"
REMOTE_DIR="${REMOTE_DIR:-/opt/studentapp}"
D="$(cd "$(dirname "$0")/server" && pwd)"
DOMAIN="${DOMAIN:-}"
SKIP_BUILD="${SKIP_BUILD:-0}"

if [[ -z "${ADMIN_PASSWORD:-}" ]]; then
  echo "ERROR: set ADMIN_PASSWORD env var before deploy."
  echo "Example: ADMIN_PASSWORD='...' DOMAIN='apistudentkgtu.ru' ./deploy.sh"
  echo "If site is down and rebuild fails: SKIP_BUILD=1 ADMIN_PASSWORD='...' DOMAIN='apistudentkgtu.ru' ./deploy.sh"
  exit 1
fi

echo "1/6 Copying code to $S:$REMOTE_DIR ..."
ssh "$S" "mkdir -p $REMOTE_DIR/uploads $REMOTE_DIR/scripts $REMOTE_DIR/static"
scp "$D/main.py" "$D/database.py" "$D/parser.py" "$D/scraper.py" "$D/news_scraper.py" \
    "$D/schedule_validator.py" "$D/faculties.py" "$D/teacher_match.py" "$D/teacher_index.py" \
    "$D/requirements.txt" "$D/Dockerfile" "$D/docker-compose.yml" "$D/Caddyfile" \
    "$S:$REMOTE_DIR/"
if [[ -d "$D/static" ]]; then
  scp -r "$D/static/." "$S:$REMOTE_DIR/static/"
fi
if [[ -f "$D/scripts/harden_firewall.sh" ]]; then
  scp "$D/scripts/harden_firewall.sh" "$S:$REMOTE_DIR/scripts/"
  ssh "$S" "chmod +x $REMOTE_DIR/scripts/harden_firewall.sh"
fi

if compgen -G "$D/uploads/"*.xlsx > /dev/null || compgen -G "$D/uploads/"*.xls > /dev/null; then
  echo "2/6 Copying Excel schedules..."
  shopt -s nullglob
  scp "$D/uploads/"*.xlsx "$D/uploads/"*.xls "$S:$REMOTE_DIR/uploads/" 2>/dev/null || \
    scp "$D/uploads/"*schedule* "$S:$REMOTE_DIR/uploads/"
  shopt -u nullglob
else
  echo "2/6 No local Excel files — skipping"
fi

if [[ -f "$D/teachers.json" ]]; then
  scp "$D/teachers.json" "$S:$REMOTE_DIR/"
fi
# Ship fresh news cache + meta from Mac so VPS is not stuck on old posts
# (VPS sometimes fails to scrape dksta.ru and kept a stale news_cache.json)
if [[ -f "$D/news_cache.json" ]]; then
  scp "$D/news_cache.json" "$S:$REMOTE_DIR/"
  echo "   news_cache.json copied (fresh scrape from deploy machine)"
fi
if [[ -f "$D/news_meta.json" ]]; then
  scp "$D/news_meta.json" "$S:$REMOTE_DIR/"
  echo "   news_meta.json copied"
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
ssh "$S" bash -s <<REMOTE
set -euo pipefail
cd "$REMOTE_DIR"
SKIP_BUILD="$SKIP_BUILD"

if docker compose version >/dev/null 2>&1; then
  DC="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  echo "ERROR: neither 'docker compose' nor 'docker-compose' found"
  exit 1
fi
echo "Using: \$DC  SKIP_BUILD=\$SKIP_BUILD"

systemctl stop nginx 2>/dev/null || true
systemctl disable nginx 2>/dev/null || true
systemctl stop apache2 2>/dev/null || true
systemctl disable apache2 2>/dev/null || true
systemctl stop httpd 2>/dev/null || true
if command -v fuser >/dev/null 2>&1; then
  fuser -k 80/tcp 2>/dev/null || true
  fuser -k 443/tcp 2>/dev/null || true
fi

# Prefer soft restart: code is volume-mounted (./:/app), no rebuild needed for main.py/static.
if [[ "\$SKIP_BUILD" == "1" ]]; then
  echo "SKIP_BUILD=1 → start existing images (no pip / no rebuild)"
  \$DC up -d --remove-orphans
  # If API was never built, fall back to build
  if ! \$DC ps --status running 2>/dev/null | grep -q schedule-api; then
    echo "API not running — trying rebuild (may fail if PyPI blocked)..."
    \$DC up -d --build --force-recreate || true
  fi
else
  # Try rebuild; on failure still try to start previous image so site comes back
  if ! \$DC up -d --build --force-recreate; then
    echo "WARN: docker build failed (often PyPI timeout). Starting last known images..."
    \$DC up -d --remove-orphans || true
  fi
fi

\$DC ps

if command -v ufw >/dev/null 2>&1; then
  echo "Hardening UFW (deny public :8000)..."
  ufw allow OpenSSH 2>/dev/null || ufw allow 22/tcp 2>/dev/null || true
  ufw allow 80/tcp 2>/dev/null || true
  ufw allow 443/tcp 2>/dev/null || true
  ufw deny 8000/tcp 2>/dev/null || true
  ufw --force enable 2>/dev/null || true
  ufw status numbered 2>/dev/null || true
fi
REMOTE

echo "5/6 Health check (local API)..."
if ssh "$S" "sleep 3; curl -sf http://127.0.0.1:8000/health"; then
  echo ""
  ssh "$S" "curl -sf http://127.0.0.1:8000/api/courses | head -c 200 && echo" || true
else
  echo ""
  echo "WARN: local API health failed. On server run:"
  echo "  ssh $S 'cd $REMOTE_DIR && docker compose ps && docker compose logs schedule-api --tail 50'"
fi

if [[ -n "$DOMAIN" && "$DOMAIN" != "localhost" ]]; then
  echo "6/6 Health check (HTTPS)..."
  sleep 3
  if curl -sf "https://$DOMAIN/health" >/dev/null 2>&1 || ssh "$S" "curl -sf https://$DOMAIN/health"; then
    echo ""
    echo "DONE. Site should be up."
    echo "  API:   https://$DOMAIN/"
    echo "  Admin: https://$DOMAIN/admin"
    echo "  Favicon: https://$DOMAIN/static/favicon.png"
  else
    echo ""
    echo "WARN: https://$DOMAIN/health failed — check DNS / Caddy logs."
    echo "  ssh $S 'cd $REMOTE_DIR && docker compose logs caddy --tail 40'"
    exit 1
  fi
else
  echo "6/6 Skip public HTTPS (no DOMAIN)."
  echo "DONE (HTTP mode)."
fi
