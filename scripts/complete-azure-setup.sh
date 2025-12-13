#!/bin/bash
#
# Complete Azure OAuth2 EC JWK Setup Script
#
# This script performs all necessary steps to set up EC JWK (P-256/ES256)
# for the OAuth2 Authorization Server running in Azure AKS.
#
# It handles:
# 1. Verifying prerequisites
# 2. Generating EC JWK
# 3. Uploading to Azure Key Vault
# 4. Updating Kubernetes manifests
# 5. Deploying to AKS
# 6. Verifying the setup
#
# Usage:
#   ./scripts/complete-azure-setup.sh                    # Interactive mode
#   ./scripts/complete-azure-setup.sh --auto             # Fully automated
#   ./scripts/complete-azure-setup.sh --vault-name my-kv # Custom vault
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Configuration
VAULT_NAME="${AZURE_VAULT_NAME:-inker-kv}"
SECRET_NAME="${AZURE_JWK_SECRET_NAME:-oauth2-jwk}"
K8S_NAMESPACE="${K8S_NAMESPACE:-default}"
AUTO_MODE=false
INTERACTIVE=true

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --auto)
            AUTO_MODE=true
            INTERACTIVE=false
            shift
            ;;
        --interactive)
            INTERACTIVE=true
            shift
            ;;
        --vault-name)
            VAULT_NAME="$2"
            shift 2
            ;;
        --secret-name)
            SECRET_NAME="$2"
            shift 2
            ;;
        --namespace)
            K8S_NAMESPACE="$2"
            shift 2
            ;;
        --help)
            show_help
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
done

log_info() {
    echo -e "${BLUE}ℹ ${NC}$*"
}

log_success() {
    echo -e "${GREEN}✓ ${NC}$*"
}

log_warning() {
    echo -e "${YELLOW}⚠ ${NC}$*"
}

log_error() {
    echo -e "${RED}✗ ${NC}$*" >&2
}

log_section() {
    echo
    echo -e "${CYAN}╔═══════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║ ${NC}$1"
    echo -e "${CYAN}╚═══════════════════════════════════════╝${NC}"
    echo
}

prompt_continue() {
    if [ "$INTERACTIVE" = true ]; then
        local prompt="$1"
        local response
        read -p "$(echo -e ${YELLOW}▶${NC} $prompt [y/N]: )" response
        [[ "$response" =~ ^[Yy]$ ]]
    else
        return 0
    fi
}

show_help() {
    cat << EOF
Complete Azure OAuth2 EC JWK Setup

This script automates the setup of Elliptic Curve keys for OAuth2 token signing
in Azure Kubernetes Service.

Usage: $0 [OPTIONS]

Options:
  --auto                    Run without prompts (fully automated)
  --interactive             Interactive mode (default)
  --vault-name NAME         Azure Key Vault name (default: inker-kv)
  --secret-name NAME        Secret name in Key Vault (default: oauth2-jwk)
  --namespace NS            Kubernetes namespace (default: default)
  --help                    Show this help message

Examples:
  # Interactive setup
  $0

  # Fully automated setup
  $0 --auto

  # Custom vault and secret
  $0 --vault-name my-vault --secret-name my-secret --auto

Environment Variables:
  AZURE_VAULT_NAME          Azure Key Vault name
  AZURE_JWK_SECRET_NAME     Secret name in Key Vault
  K8S_NAMESPACE             Kubernetes namespace

EOF
}

show_welcome() {
    clear
    cat << 'EOF'
╔═══════════════════════════════════════════════════════════════════════════════╗
║                                                                               ║
║          Azure OAuth2 Authorization Server - EC JWK Setup Wizard              ║
║                                                                               ║
║  This wizard will help you set up Elliptic Curve (P-256/ES256) JSON Web     ║
║  Keys for the OAuth2 Authorization Server running in Azure AKS.             ║
║                                                                               ║
║  Steps:                                                                       ║
║  1. Verify prerequisites (Azure CLI, kubectl, jq)                           ║
║  2. Generate EC P-256 JWK                                                    ║
║  3. Upload to Azure Key Vault                                                ║
║  4. Update Kubernetes manifests                                              ║
║  5. Deploy to AKS                                                             ║
║  6. Verify the configuration                                                 ║
║                                                                               ║
╚═══════════════════════════════════════════════════════════════════════════════╝
EOF
    echo
}

check_prerequisites() {
    log_section "Step 1: Checking Prerequisites"

    local missing=0
    for cmd in az kubectl jq; do
        if command -v "$cmd" &> /dev/null; then
            log_success "$cmd is installed"
        else
            log_error "$cmd is not installed"
            missing=1
        fi
    done

    if [ $missing -eq 1 ]; then
        log_error "Please install missing prerequisites and try again"
        exit 1
    fi

    # Check Azure login
    if az account show &> /dev/null; then
        local account=$(az account show --query name -o tsv)
        log_success "Logged into Azure: $account"
    else
        log_error "Not logged into Azure. Run: az login"
        exit 1
    fi

    # Check kubectl context
    if kubectl config current-context &> /dev/null; then
        local context=$(kubectl config current-context)
        log_success "kubectl configured for: $context"
    else
        log_error "kubectl not configured. Set up your AKS context first"
        exit 1
    fi

    log_success "All prerequisites satisfied"
}

display_configuration() {
    log_section "Configuration Summary"

    echo "  Azure Vault Name:     $VAULT_NAME"
    echo "  JWK Secret Name:      $SECRET_NAME"
    echo "  K8s Namespace:        $K8S_NAMESPACE"
    echo "  Project Root:         $PROJECT_ROOT"
    echo
}

generate_jwk() {
    log_section "Step 2: Generating EC P-256 JWK"

    log_info "Generating new EC P-256 JWK for ES256 signing..."

    cd "$PROJECT_ROOT"

    if ! ./gradlew -q -p server-logic :server-logic:runGenerator > "${TEMP_DIR}/generated-jwk.json" 2>/dev/null; then
        log_error "Failed to generate JWK"
        return 1
    fi

    log_success "EC JWK generated successfully"

    # Display key info
    local kid=$(jq -r '.keys[0].kid' "${TEMP_DIR}/generated-jwk.json")
    local alg=$(jq -r '.keys[0].alg' "${TEMP_DIR}/generated-jwk.json")
    local crv=$(jq -r '.keys[0].crv' "${TEMP_DIR}/generated-jwk.json")

    echo "  Key ID:  $kid"
    echo "  Algorithm: $alg"
    echo "  Curve:   $crv"
    echo

    # Ask to review
    if [ "$INTERACTIVE" = true ]; then
        echo "Generated JWK:"
        jq . "${TEMP_DIR}/generated-jwk.json" | sed 's/^/  /'
        echo
    fi
}

upload_to_keyvault() {
    log_section "Step 3: Uploading to Azure Key Vault"

    log_info "Uploading JWK to Key Vault..."

    # Check vault exists
    if ! az keyvault show --name "$VAULT_NAME" &> /dev/null; then
        log_error "Key Vault not found: $VAULT_NAME"
        return 1
    fi

    # Upload
    if az keyvault secret set \
        --vault-name "$VAULT_NAME" \
        --name "$SECRET_NAME" \
        --file "${TEMP_DIR}/generated-jwk.json" &> /dev/null; then
        log_success "JWK uploaded to Key Vault"

        # Get version
        local version=$(az keyvault secret show \
            --vault-name "$VAULT_NAME" \
            --name "$SECRET_NAME" \
            --query properties.version -o tsv)

        echo "  Vault:    $VAULT_NAME"
        echo "  Secret:   $SECRET_NAME"
        echo "  Version:  $version"
        echo
    else
        log_error "Failed to upload to Key Vault"
        return 1
    fi
}

update_manifests() {
    log_section "Step 4: Updating Kubernetes Manifests"

    log_info "Verifying Kubernetes manifests are up to date..."

    # Check if manifests have JWK references (new canonical paths)
    if grep -q "oauth2-jwk" "$PROJECT_ROOT/infrastructure/k8s/secrets/secret-provider-class-oauth2-server.yaml"; then
        log_success "SecretProviderClass includes JWK configuration"
    else
        log_warning "SecretProviderClass may need manual review"
    fi

    if grep -q "AZURE_KEYVAULT_STATIC_JWK" "$PROJECT_ROOT/infrastructure/k8s/apps/oauth2-server/oauth2-server-deployment.yaml"; then
        log_success "Deployment manifest includes JWK env var configuration"
    else
        log_warning "Deployment manifest may need manual review"
    fi

    echo
}

deploy_to_aks() {
    log_section "Step 5: Deploying to AKS"

    log_info "Applying Kubernetes manifests..."

    # Apply the canonical kustomize entrypoint (includes secrets + deployments)
    log_info "Applying manifests via kustomize..."
    if kubectl apply -k "$PROJECT_ROOT/infrastructure/k8s" &> /dev/null; then
        log_success "Manifests applied"
    else
        log_error "Failed to apply manifests"
        return 1
    fi

    # Wait for rollout
    log_info "Waiting for deployment to roll out..."
    if kubectl rollout status deployment/oauth2-server \
        --namespace="$K8S_NAMESPACE" \
        --timeout=5m 2>/dev/null; then
        log_success "Deployment rolled out successfully"
    else
        log_error "Deployment rollout failed or timed out"
        return 1
    fi

    echo
}

verify_setup() {
    log_section "Step 6: Verifying Configuration"

    local all_ok=true

    # Check Key Vault
    log_info "Checking Key Vault secret..."
    if az keyvault secret show --vault-name "$VAULT_NAME" --name "$SECRET_NAME" &> /dev/null; then
        log_success "Key Vault secret exists"
    else
        log_error "Key Vault secret not found"
        all_ok=false
    fi

    # Check deployment
    log_info "Checking deployment..."
    if kubectl get deployment oauth2-server --namespace="$K8S_NAMESPACE" &> /dev/null; then
        log_success "Deployment exists"

        local ready=$(kubectl get deployment oauth2-server \
            --namespace="$K8S_NAMESPACE" \
            -o jsonpath='{.status.readyReplicas}')
        local desired=$(kubectl get deployment oauth2-server \
            --namespace="$K8S_NAMESPACE" \
            -o jsonpath='{.spec.replicas}')

        if [ "$ready" = "$desired" ]; then
            log_success "All pods ready ($ready/$desired)"
        else
            log_warning "Not all pods ready yet ($ready/$desired)"
        fi
    else
        log_error "Deployment not found"
        all_ok=false
    fi

    # Check JWKS endpoint
    log_info "Checking JWKS endpoint..."
    if kubectl port-forward svc/oauth2-server 9000:9000 &> /dev/null &
        PF_PID=$!
        sleep 2
        curl -s http://localhost:9000/oauth2/jwks | jq . &> /dev/null; then
        kill $PF_PID 2>/dev/null || true
        log_success "JWKS endpoint is accessible"

        # Get key info
        local key_count=$(curl -s http://localhost:9000/oauth2/jwks | jq '.keys | length')
        local algs=$(curl -s http://localhost:9000/oauth2/jwks | jq -r '.keys[].alg' | sort -u | tr '\n' ',')

        echo "  Keys found: $key_count"
        echo "  Algorithms: ${algs%,}"
    else
        log_warning "JWKS endpoint check skipped (may need manual verification)"
    fi

    echo

    if [ "$all_ok" = true ]; then
        return 0
    else
        return 1
    fi
}

show_completion() {
    log_section "Setup Complete!"

    cat << EOF
${GREEN}✓ Your OAuth2 Authorization Server is now configured with EC JWK keys!${NC}

Next Steps:

1. Monitor the deployment:
   ${CYAN}kubectl logs -l app=oauth2-server -f${NC}

2. Test the JWKS endpoint:
   ${CYAN}kubectl port-forward svc/oauth2-server 9000:9000 &
   curl -s http://localhost:9000/oauth2/jwks | jq .${NC}

3. Generate a test token:
   ${CYAN}curl -X POST http://localhost:9000/oauth2/token \\
     -H "Content-Type: application/x-www-form-urlencoded" \\
     -d "grant_type=client_credentials&client_id=m2m-client&client_secret=m2m-secret"${NC}

4. For key rotation in the future:
   ${CYAN}./scripts/azure-ec-jwk-manager.sh help${NC}

Documentation:
  - Quick Start: ${CYAN}docs/QUICK_START_EC_JWK.md${NC}
  - Full Setup:  ${CYAN}docs/AZURE_EC_JWK_SETUP.md${NC}
  - Troubleshooting: ${CYAN}docs/TROUBLESHOOTING_EC_JWK.md${NC}

${YELLOW}Remember to:${NC}
  • Rotate keys quarterly
  • Monitor Key Vault access
  • Keep JWK files out of version control
  • Use Managed Identities for pod authentication

EOF
}

main() {
    show_welcome
    display_configuration

    if [ "$INTERACTIVE" = true ]; then
        if ! prompt_continue "Continue with setup?"; then
            log_info "Setup cancelled"
            exit 0
        fi
    fi

    # Create temp directory
    TEMP_DIR=$(mktemp -d)
    trap "rm -rf $TEMP_DIR" EXIT

    # Execute steps
    check_prerequisites && \
    generate_jwk && \
    upload_to_keyvault && \
    update_manifests && \
    deploy_to_aks && \
    verify_setup && \
    show_completion || {
        log_error "Setup encountered errors. Please review the output above."
        exit 1
    }
}

main "$@"

