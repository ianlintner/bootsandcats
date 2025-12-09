#!/bin/bash
# Generate client SDKs from OpenAPI specs
# Usage: ./generate-clients.sh [typescript|python|go|rust|all]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLIENTS_DIR="$(dirname "$SCRIPT_DIR")"
SPECS_DIR="$CLIENTS_DIR/specs"
CONFIG_DIR="$CLIENTS_DIR/config"

# OpenAPI Generator version
OPENAPI_GENERATOR_VERSION="${OPENAPI_GENERATOR_VERSION:-7.10.0}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

log_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# Check if OpenAPI Generator is installed
check_openapi_generator() {
    if command -v openapi-generator-cli &> /dev/null; then
        log_info "Using openapi-generator-cli"
        GENERATOR_CMD="openapi-generator-cli"
    elif command -v openapi-generator &> /dev/null; then
        log_info "Using openapi-generator"
        GENERATOR_CMD="openapi-generator"
    elif docker info &> /dev/null; then
        log_info "Using Docker for OpenAPI Generator"
        GENERATOR_CMD="docker run --rm -v ${CLIENTS_DIR}:/local openapitools/openapi-generator-cli:v${OPENAPI_GENERATOR_VERSION}"
    else
        log_error "OpenAPI Generator not found. Please install it:"
        echo "  npm install -g @openapitools/openapi-generator-cli"
        echo "  OR"
        echo "  brew install openapi-generator"
        echo "  OR"
        echo "  docker pull openapitools/openapi-generator-cli:v${OPENAPI_GENERATOR_VERSION}"
        exit 1
    fi
}

# Check if spec files exist
check_specs() {
    if [ ! -f "$SPECS_DIR/oauth2-server.json" ]; then
        log_error "OAuth2 Server spec not found at $SPECS_DIR/oauth2-server.json"
        log_info "Run ./download-specs.sh first to download the specs"
        exit 1
    fi
}

generate_typescript() {
    log_step "Generating TypeScript client..."
    
    cd "$CLIENTS_DIR"
    
    $GENERATOR_CMD generate \
        -c config/typescript.yaml \
        -i specs/oauth2-server.json \
        -o typescript/src \
        --skip-validate-spec \
        --enable-post-process-file
    
    log_info "TypeScript client generated successfully"
    
    # Install dependencies and build if npm is available
    if command -v npm &> /dev/null; then
        log_info "Installing TypeScript dependencies..."
        cd typescript
        npm install
        npm run build || log_warn "Build failed, you may need to fix generated code"
        cd ..
    fi
}

generate_python() {
    log_step "Generating Python client..."
    
    cd "$CLIENTS_DIR"
    
    $GENERATOR_CMD generate \
        -c config/python.yaml \
        -i specs/oauth2-server.json \
        -o python/src/bootsandcats_oauth2_client/generated \
        --skip-validate-spec \
        --enable-post-process-file
    
    log_info "Python client generated successfully"
    
    # Install in development mode if pip is available
    if command -v pip &> /dev/null; then
        log_info "Installing Python package in development mode..."
        cd python
        pip install -e . || log_warn "Installation failed"
        cd ..
    fi
}

generate_go() {
    log_step "Generating Go client..."
    
    cd "$CLIENTS_DIR"
    
    $GENERATOR_CMD generate \
        -c config/go.yaml \
        -i specs/oauth2-server.json \
        -o go \
        --skip-validate-spec \
        --enable-post-process-file
    
    log_info "Go client generated successfully"
    
    # Run go mod tidy if go is available
    if command -v go &> /dev/null; then
        log_info "Running go mod tidy..."
        cd go
        go mod tidy
        go build ./... || log_warn "Build failed, you may need to fix generated code"
        cd ..
    fi
}

generate_rust() {
    log_step "Generating Rust client..."
    
    cd "$CLIENTS_DIR"
    
    $GENERATOR_CMD generate \
        -c config/rust.yaml \
        -i specs/oauth2-server.json \
        -o rust/src/generated \
        --skip-validate-spec \
        --enable-post-process-file
    
    log_info "Rust client generated successfully"
    
    # Build if cargo is available
    if command -v cargo &> /dev/null; then
        log_info "Building Rust client..."
        cd rust
        cargo build || log_warn "Build failed, you may need to fix generated code"
        cd ..
    fi
}

# Main execution
check_openapi_generator
check_specs

# Parse arguments
LANGUAGE="${1:-all}"

case "$LANGUAGE" in
    typescript|ts)
        generate_typescript
        ;;
    python|py)
        generate_python
        ;;
    go)
        generate_go
        ;;
    rust|rs)
        generate_rust
        ;;
    all)
        generate_typescript
        generate_python
        generate_go
        generate_rust
        ;;
    *)
        echo "Usage: $0 [typescript|python|go|rust|all]"
        exit 1
        ;;
esac

log_info "Client generation complete!"
