#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
# Requires working SSH to root@157.22.186.149
export ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(grep '^ADMIN_PASSWORD=' server/.env | cut -d= -f2-)}"
export DOMAIN="${DOMAIN:-apistudentkgtu.ru}"
export SKIP_BUILD="${SKIP_BUILD:-0}"
echo "Deploying faculties + MTF schedules + by-teacher API..."
./deploy.sh
echo
echo "Smoke:"
curl -sS "https://apistudentkgtu.ru/api/faculties" | head -c 400; echo
curl -sS "https://apistudentkgtu.ru/api/groups?faculty=mtf&course=1" | head -c 300; echo
curl -sS --get "https://apistudentkgtu.ru/api/schedule/by-teacher" --data-urlencode "q=Антошина" --data-urlencode "day=today" | head -c 400; echo
