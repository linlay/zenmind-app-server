zenmind-app-server Program Bundle
==================================

This bundle runs zenmind-app-server as a single native process.

Quick Start
-----------
1. cp .env.example .env
2. Edit .env — set AUTH_ADMIN_PASSWORD_BCRYPT and AUTH_APP_MASTER_PASSWORD_BCRYPT
3. ./start.sh --daemon
4. Open http://127.0.0.1:11950/admin/
5. ./stop.sh

Directory Layout
----------------
app-server              Go binary (backend + frontend gateway)
start.sh                Start script (foreground or --daemon)
stop.sh                 Stop script
.env.example            Environment variable template
schema.sql              Database schema (auto-applied on first start)
frontend/dist/          Pre-built admin frontend assets
data/                   SQLite database (created at runtime)
.runtime/               PID and log files (created at runtime)

Key Environment Variables
-------------------------
SERVER_PORT             Listen port (default: 11950)
AUTH_DB_PATH            SQLite database path (default: ./data/auth.db)
AUTH_ISSUER             Public issuer URL for OAuth2/OIDC metadata
AUTH_ADMIN_PASSWORD_BCRYPT  Admin login bcrypt hash (required)
AUTH_APP_MASTER_PASSWORD_BCRYPT  App master password bcrypt hash (required)
FRONTEND_DIST_DIR       Frontend static files directory (default: ./frontend/dist)
