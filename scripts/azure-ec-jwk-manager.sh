#!/bin/bash
#
# Complete EC JWK Management Script for Azure Deployment
#
# This script handles:
# - Generating EC JWK (P-256/ES256)
# - Uploading to Azure Key Vault
# - Configuring Kubernetes secrets
# - Testing the configuration
#
# Prerequisites:
#   - Azure CLI installed and authenticated
#   - kubectl configured for your AKS cluster
#   - jq for JSON processing
#   - Gradle for key generation
#
# Usage:
#   ./scripts/azure-ec-jwk-manager.sh generate          # Generate new EC JWK
#   ./scripts/azure-ec-jwk-manager.sh upload <file>     # Upload to Key Vault
#   ./scripts/azure-ec-jwk-manager.sh deploy            # Deploy to AKS
#   ./scripts/azure-ec-jwk-manager.sh verify            # Verify configuration
#   ./scripts/azure-ec-jwk-manager.sh rotate <file>     # Rotate keys
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Configuration
VAULT_NAME="${AZURE_VAULT_NAME:-inker-kv}"
SECRET_NAME="${AZURE_JWK_SECRET_NAME:-oauth2-jwk}"
# NOTE:
# - The canonical path is to store the JWK in Key Vault and let the CSI driver
#   sync it into this Kubernetes Secret (see infrastructure/k8s/secrets/).
# - The update-k8s command below is retained for emergencies/offline testing,
#   but it bypasses Key Vault and can be overwritten by CSI sync.
K8S_SECRET_NAME="oauth2-jwk-secret"
K8S_NAMESPACE="${K8S_NAMESPACE:-default}"
TEMP_DIR=$(mktemp -d)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

cleanup() {
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

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

check_prerequisites() {
    local missing=0

    for cmd in az kubectl jq; do
        if ! command -v "$cmd" &> /dev/null; then
            log_error "$cmd is not installed"
            missing=1
        fi
    done

    if [ $missing -eq 1 ]; then
        log_error "Please install missing prerequisites"
        exit 1
    fi

    # Check Azure CLI login
    if ! az account show &> /dev/null; then
        log_error "Not logged into Azure. Run: az login"
        exit 1
    fi

    # Check kubectl context
    if ! kubectl config current-context &> /dev/null; then
        log_error "kubectl not configured. Set up your AKS context"
        exit 1
    fi

    log_success "All prerequisites satisfied"
}

generate_ec_jwk() {
    log_info "Generating EC P-256 JWK (ES256)..."

    cd "$PROJECT_ROOT"

    if ! [ -f "gradlew" ]; then
        log_error "gradlew not found in project root"
        return 1
    fi

    local output_file="${1:-${TEMP_DIR}/generated-jwk.json}"

    if ./gradlew -q -p server-logic :server-logic:runGenerator > "$output_file" 2>/dev/null; then
        log_success "EC JWK generated successfully"
        echo "$output_file"
        return 0
    else
        log_error "Failed to generate EC JWK"
        return 1
    fi
}

validate_jwk() {
    local jwk_file="$1"

    if ! [ -f "$jwk_file" ]; then
        log_error "JWK file not found: $jwk_file"
        return 1
    fi

    # Validate JSON format
    if ! jq empty "$jwk_file" 2>/dev/null; then
        log_error "Invalid JSON in JWK file"
        return 1
    fi

    # Check required fields
    local keys_count=$(jq '.keys | length' "$jwk_file")
    if [ "$keys_count" -eq 0 ]; then
        log_error "No keys found in JWK set"
        return 1
    fi

    # Check key properties
    local key_type=$(jq -r '.keys[0].kty' "$jwk_file")
    local algorithm=$(jq -r '.keys[0].alg' "$jwk_file")
    local curve=$(jq -r '.keys[0].crv' "$jwk_file")
    local private_d=$(jq -r '.keys[0].d // empty' "$jwk_file")

    if [ "$key_type" != "EC" ]; then
        log_error "Expected key type EC, got $key_type"
        return 1
    fi

    if [ "$algorithm" != "ES256" ]; then
        log_error "Expected algorithm ES256, got $algorithm"
        return 1
    fi

    if [ "$curve" != "P-256" ]; then
        log_error "Expected curve P-256, got $curve"
        return 1
    fi

    # The authorization server must have a PRIVATE signing key. The public JWKS
    # (/oauth2/jwks) intentionally omits the private component ("d").
    if [ -z "$private_d" ] || [ "$private_d" = "null" ]; then
        log_error "JWK is missing private key material (EC field 'd')."
        log_error "Generate a full signing key (includes 'd'); do NOT copy from /oauth2/jwks."
        return 1
    fi

    log_success "JWK validation passed (EC P-256 with ES256, private key present)"
    return 0
}

upload_to_keyvault() {
    local jwk_file="$1"

    if ! validate_jwk "$jwk_file"; then
        return 1
    fi

    log_info "Uploading JWK to Azure Key Vault..."
    log_info "Vault: $VAULT_NAME"
    log_info "Secret: $SECRET_NAME"

    # Check if vault exists
    if ! az keyvault show --name "$VAULT_NAME" &> /dev/null; then
        log_error "Key Vault not found: $VAULT_NAME"
        return 1
    fi

    # Upload the secret
    if az keyvault secret set \
        --vault-name "$VAULT_NAME" \
        --name "$SECRET_NAME" \
        --file "$jwk_file" &> /dev/null; then
        log_success "JWK uploaded to Key Vault"

        # Display confirmation
        local version=$(az keyvault secret show \
            --vault-name "$VAULT_NAME" \
            --name "$SECRET_NAME" \
            --query properties.version -o tsv)
        log_info "Secret version: $version"
        return 0
    else
        log_error "Failed to upload to Key Vault"
        return 1
    fi
}

download_from_keyvault() {
    local output_file="${1:-${TEMP_DIR}/downloaded-jwk.json}"

    log_info "Downloading JWK from Key Vault..."

    if az keyvault secret show \
        --vault-name "$VAULT_NAME" \
        --name "$SECRET_NAME" \
        --query value -o json > "$output_file" 2>/dev/null; then
        log_success "JWK downloaded from Key Vault"
        echo "$output_file"
        return 0
    else
        log_error "Failed to download from Key Vault"
        return 1
    fi
}

update_k8s_secret() {
    local jwk_file="$1"

    if ! [ -f "$jwk_file" ]; then
        log_error "JWK file not found: $jwk_file"
        return 1
    fi

    log_warning "Updating Kubernetes secret directly (bypasses Key Vault): $K8S_SECRET_NAME"
    log_info "Namespace: $K8S_NAMESPACE"

    # Create or update the secret (key name matches oauth2-server Deployment env var ref)
    if kubectl create secret generic "$K8S_SECRET_NAME" \
        --from-file=static-jwk="$jwk_file" \
        --namespace="$K8S_NAMESPACE" \
        --dry-run=client -o yaml | kubectl apply -f - 2>/dev/null; then
        log_success "Kubernetes secret updated"
        return 0
    else
        log_error "Failed to update Kubernetes secret"
        return 1
    fi
}

deploy_to_aks() {
    log_info "Deploying to AKS..."

    # Apply the canonical kustomize entrypoint (includes secrets + deployments)
    log_info "Applying Kubernetes manifests via kustomize..."
    if kubectl apply -k "$PROJECT_ROOT/infrastructure/k8s" &> /dev/null; then
        log_success "Manifests applied"
    else
        log_error "Failed to apply manifests (kustomize)"
        return 1
    fi

    # Wait for rollout
    log_info "Waiting for deployment to roll out (this may take a minute)..."
    if kubectl rollout status deployment/oauth2-server \
        --namespace="$K8S_NAMESPACE" \
        --timeout=5m 2>/dev/null; then
        log_success "Deployment rolled out successfully"
        return 0
    else
        log_error "Deployment rollout failed or timed out"
        return 1
    fi
}

verify_configuration() {
    log_info "Verifying configuration..."
    local all_ok=true

    # Check Key Vault secret
    log_info "Checking Key Vault secret..."
    if az keyvault secret show \
        --vault-name "$VAULT_NAME" \
        --name "$SECRET_NAME" &> /dev/null; then
        log_success "Key Vault secret exists"
    else
        log_error "Key Vault secret not found"
        all_ok=false
    fi

    # Check Kubernetes secret
    log_info "Checking Kubernetes secret..."
    if kubectl get secret "$K8S_SECRET_NAME" \
        --namespace="$K8S_NAMESPACE" &> /dev/null; then
        log_success "Kubernetes secret exists"
    else
        log_error "Kubernetes secret not found"
        all_ok=false
    fi

    # Check deployment
    log_info "Checking deployment..."
    if kubectl get deployment oauth2-server \
        --namespace="$K8S_NAMESPACE" &> /dev/null; then
        log_success "Deployment exists"

        # Check pod status
        local ready_replicas=$(kubectl get deployment oauth2-server \
            --namespace="$K8S_NAMESPACE" \
            -o jsonpath='{.status.readyReplicas}')
        local desired_replicas=$(kubectl get deployment oauth2-server \
            --namespace="$K8S_NAMESPACE" \
            -o jsonpath='{.spec.replicas}')

        if [ "$ready_replicas" = "$desired_replicas" ]; then
            log_success "All pods are ready ($ready_replicas/$desired_replicas)"
        else
            log_warning "Not all pods ready ($ready_replicas/$desired_replicas)"
        fi
    else
        log_error "Deployment not found"
        all_ok=false
    fi

    # Check JWKS endpoint
    log_info "Checking JWKS endpoint..."
    if kubectl port-forward svc/oauth2-server 9000:9000 &> /dev/null &
        sleep 2 && \
        curl -s http://localhost:9000/oauth2/jwks | jq . &> /dev/null; then
        log_success "JWKS endpoint accessible"

        # Display key info
        local key_count=$(curl -s http://localhost:9000/oauth2/jwks | jq '.keys | length')
        local algorithms=$(curl -s http://localhost:9000/oauth2/jwks | jq -r '.keys[].alg' | sort -u | tr '\n' ',')
        log_info "Found $key_count key(s) with algorithms: $algorithms"
    else
        log_warning "JWKS endpoint not accessible from port-forward"
    fi

    if [ "$all_ok" = true ]; then
        log_success "All verifications passed"
        return 0
    else
        log_error "Some verifications failed"
        return 1
    fi
}

rotate_keys() {
    local new_jwk_file="$1"

    if ! validate_jwk "$new_jwk_file"; then
        return 1
    fi

    log_info "Rotating keys (keeping old key for backward compatibility)..."

    # Download current keys
    local current_jwk=$(download_from_keyvault "${TEMP_DIR}/current-jwk.json")
    if [ $? -ne 0 ]; then
        log_warning "Could not download current keys (may be first rotation)"
        # Just upload the new key
        upload_to_keyvault "$new_jwk_file"
        return $?
    fi

    # Merge keys
    log_info "Merging old and new keys..."
    jq -s '{keys: (.[0].keys + .[1].keys)}' \
        "$current_jwk" "$new_jwk_file" > "${TEMP_DIR}/merged-jwk.json"

    # Upload merged keys
    if upload_to_keyvault "${TEMP_DIR}/merged-jwk.json"; then
        log_success "Keys rotated successfully"
        log_info "Old key is retained for token validation"
        log_info "New key will be used for signing"
        return 0
    else
        log_error "Failed to rotate keys"
        return 1
    fi
}

show_help() {
    cat << EOF
Azure EC JWK Manager - Manage OAuth2 signing keys for AKS

Usage: $0 <command> [options]

Commands:
  generate              Generate a new EC P-256 JWK (ES256)
  upload <file>         Upload JWK to Azure Key Vault
  download              Download JWK from Azure Key Vault
  validate <file>       Validate JWK format and content
  update-k8s <file>     Update Kubernetes secret with JWK
  deploy                Deploy/update everything to AKS
  verify                Verify the complete configuration
  rotate <file>         Rotate keys (keep old, add new)
  show-config           Display current configuration
  help                  Show this help message

Environment Variables:
  AZURE_VAULT_NAME      Azure Key Vault name (default: inker-kv)
  AZURE_JWK_SECRET_NAME Secret name in Key Vault (default: oauth2-jwk)
  K8S_NAMESPACE         Kubernetes namespace (default: default)

Examples:
  # Complete setup
  $0 generate > my-jwk.json
  $0 validate my-jwk.json
  $0 upload my-jwk.json
  $0 deploy

  # Rotate keys
  $0 generate > new-jwk.json
  $0 rotate new-jwk.json
  $0 deploy

  # Verify everything works
  $0 verify

EOF
}

show_config() {
    log_info "Current configuration:"
    echo "  Azure Vault Name:     $VAULT_NAME"
    echo "  JWK Secret Name:      $SECRET_NAME"
    echo "  K8s Secret Name:      $K8S_SECRET_NAME"
    echo "  K8s Namespace:        $K8S_NAMESPACE"
    echo "  Project Root:         $PROJECT_ROOT"
}

main() {
    if [ $# -eq 0 ]; then
        show_help
        exit 0
    fi

    local command="$1"
    shift || true

    case "$command" in
        generate)
            check_prerequisites
            output_file=$(generate_ec_jwk)
            cat "$output_file"
            ;;
        upload)
            check_prerequisites
            if [ $# -eq 0 ]; then
                log_error "Please provide JWK file path"
                exit 1
            fi
            upload_to_keyvault "$1"
            ;;
        download)
            check_prerequisites
            download_from_keyvault
            ;;
        validate)
            if [ $# -eq 0 ]; then
                log_error "Please provide JWK file path"
                exit 1
            fi
            validate_jwk "$1"
            ;;
        update-k8s)
            check_prerequisites
            if [ $# -eq 0 ]; then
                log_error "Please provide JWK file path"
                exit 1
            fi
            update_k8s_secret "$1"
            ;;
        deploy)
            check_prerequisites
            deploy_to_aks
            ;;
        verify)
            check_prerequisites
            verify_configuration
            ;;
        rotate)
            check_prerequisites
            if [ $# -eq 0 ]; then
                log_error "Please provide new JWK file path"
                exit 1
            fi
            rotate_keys "$1"
            ;;
        show-config)
            show_config
            ;;
        help|-h|--help)
            show_help
            ;;
        *)
            log_error "Unknown command: $command"
            show_help
            exit 1
            ;;
    esac
}

main "$@"

