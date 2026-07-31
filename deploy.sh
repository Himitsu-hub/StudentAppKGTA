#!/bin/bash
set -euo pipefail

# Usage:
#   ADMIN_PASSWORD='your-strong-password' ./deploy.sh
# Optional:
#   SERVER=root@157.22.186.149 REMOTE_DIR=/opt/studentapp ./deploy.sh

S="${SERVER:-root@157.22.186.149}"
REMOTE_DIR="${REMOTE_DIR:-/opt/studentapp}"
D="$(cd "$(dirname "$0")/server" && pwd)"

if [[ -z "${ADMIN_PASSWORD:-}" ]]; then
  echo "ERROR: set ADMIN_PASSWORD env var before deploy."
  echo "Example: ADMIN_PASSWORD='...' ./deploy.sh"
  exit 1
fi

echo "1/5 Copying code to $S:$REMOTE_DIR ..."
ssh "$S" "mkdir -p $REMOTE_DIR/uploads"
scp "$D/main.py" "$D/database.py" "$D/parser.py" "$D/scraper.py" "$D/news_scraper.py" \
    "$D/requirements.txt" "$D/Dockerfile" "$D/docker-compose.yml" "$S:$REMOTE_DIR/"

if compgen -G "$D/uploads/"*.xlsx > /dev/null; then
  echo "2/5 Copying Excel schedules..."
  scp "$D/uploads/"*.xlsx "$S:$REMOTE_DIR/uploads/"
else
  echo "2/5 No local Excel files — skipping"
fi

if [[ -f "$D/teachers.json" ]]; then
  scp "$D/teachers.json" "$S:$REMOTE_DIR/"
fi

echo "3/5 Writing remote .env..."
ssh "$S" "printf 'ADMIN_PASSWORD=%s\nDATABASE_URL=sqlite:///./schedule.db\n' $(printf %q "$ADMIN_PASSWORD") > $REMOTE_DIR/.env"

echo "4/5 Restarting Docker..."
ssh "$S" "cd $REMOTE_DIR && docker-compose up -d --build"

echo "5/5 Health check..."
ssh "$S" "curl -sf http://localhost:8000/health && echo && curl -sf http://localhost:8000/api/courses"

echo ""
echo "DONE. Admin UI: http://SERVER:8000/admin (password not in URL)"
