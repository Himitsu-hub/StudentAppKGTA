#!/bin/bash
# Run ON the VPS as root (after HTTPS works):
#   bash /opt/studentapp/scripts/harden_firewall.sh
#
# - Allow SSH, HTTP, HTTPS
# - Deny public access to port 8000 (API only via Caddy on 443)
# Docker already binds API to 127.0.0.1:8000; this adds host firewall defense-in-depth.

set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root"
  exit 1
fi

if ! command -v ufw >/dev/null 2>&1; then
  echo "ufw not installed. Install: apt-get update && apt-get install -y ufw"
  exit 1
fi

echo "Configuring UFW..."
ufw allow OpenSSH || ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw deny 8000/tcp

# Enable if not already (non-interactive)
ufw --force enable
ufw status verbose

echo ""
echo "OK. Port 8000 denied from outside; 80/443/SSH open."
echo "API still works: https://YOUR_DOMAIN/  and  http://127.0.0.1:8000 on the server."
