#!/bin/bash
echo "⚠️  KEEP THIS SECRET - only store in Azure Key Vault or secure storage" >&2
echo "⚠️  IMPORTANT: This JSON includes the PRIVATE KEY material!" >&2
echo "✓ EC JWK generated successfully" >&2
echo "" >&2

./gradlew -q -p server-logic :server-logic:runGenerator
# Run the EcJwkGenerator tool

fi
    exit 1
    echo "ERROR: gradlew not found in project root" >&2
if ! command -v ./gradlew &> /dev/null; then

cd "$PROJECT_ROOT"
# Build and run the generator using Gradle

echo "" >&2
echo "This key will be used for JWT token signing in Azure pods." >&2
echo "Generating EC P-256 JWK for ES256 token signing..." >&2

PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Get the script directory

set -euo pipefail

#
# Output: JSON Web Key Set with a single P-256 EC key (including private key material)
#
#   ./scripts/generate-ec-jwk.sh | jq .  (pretty print with jq)
#   ./scripts/generate-ec-jwk.sh
# Usage:
#
# The key uses the P-256 curve (secp256r1) and can be used with ES256 algorithm.
# This script generates an Elliptic Curve JSON Web Key suitable for JWT token signing.
#
# Generate EC JWK (P-256/ES256) for OAuth2 Authorization Server
#

