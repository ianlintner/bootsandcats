#!/bin/bash
# Download OpenAPI specs from running services
# Usage: ./download-specs.sh [oauth2|profile|all]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLIENTS_DIR="$(dirname "$SCRIPT_DIR")"
SPECS_DIR="$CLIENTS_DIR/specs"

# Default configuration
OAUTH2_SERVER_URL="${OAUTH2_SERVER_URL:-http://localhost:8080}"
PROFILE_SERVICE_URL="${PROFILE_SERVICE_URL:-http://localhost:8081}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Create specs directory if it doesn't exist
mkdir -p "$SPECS_DIR"

download_oauth2_spec() {
    log_info "Downloading OAuth2 Server OpenAPI spec..."
    
    if curl -s -f -o "$SPECS_DIR/oauth2-server.json" "$OAUTH2_SERVER_URL/v3/api-docs"; then
        log_info "OAuth2 Server spec saved to $SPECS_DIR/oauth2-server.json"
        return 0
    else
        log_error "Failed to download OAuth2 Server spec from $OAUTH2_SERVER_URL/v3/api-docs"
        log_warn "Make sure the OAuth2 server is running"
        return 1
    fi
}

download_profile_spec() {
    log_info "Downloading Profile Service OpenAPI spec..."
    
    # Try different endpoints
    if curl -s -f -o "$SPECS_DIR/profile-service.json" "$PROFILE_SERVICE_URL/swagger/api-docs"; then
        log_info "Profile Service spec saved to $SPECS_DIR/profile-service.json"
        return 0
    elif curl -s -f -o "$SPECS_DIR/profile-service.json" "$PROFILE_SERVICE_URL/v3/api-docs"; then
        log_info "Profile Service spec saved to $SPECS_DIR/profile-service.json"
        return 0
    else
        log_error "Failed to download Profile Service spec from $PROFILE_SERVICE_URL"
        log_warn "Make sure the Profile service is running"
        return 1
    fi
}

# Parse arguments
SERVICE="${1:-all}"

case "$SERVICE" in
    oauth2)
        download_oauth2_spec
        ;;
    profile)
        download_profile_spec
        ;;
    all)
        OAUTH2_SUCCESS=0
        PROFILE_SUCCESS=0
        
        download_oauth2_spec || OAUTH2_SUCCESS=1
        download_profile_spec || PROFILE_SUCCESS=1
        
        if [ $OAUTH2_SUCCESS -eq 1 ] && [ $PROFILE_SUCCESS -eq 1 ]; then
            log_error "Failed to download any specs"
            exit 1
        fi
        ;;
    *)
        echo "Usage: $0 [oauth2|profile|all]"
        exit 1
        ;;
esac

log_info "Spec download complete!"
ls -la "$SPECS_DIR"
