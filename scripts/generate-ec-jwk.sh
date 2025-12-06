#!/bin/bash
#
# Generate EC JWK (P-256/ES256) for OAuth2 Authorization Server
#
# This script generates an Elliptic Curve JSON Web Key suitable for JWT token signing.
# The key uses the P-256 curve (secp256r1) and can be used with ES256 algorithm.
#
# Usage:
#   ./scripts/generate-ec-jwk.sh
#   ./scripts/generate-ec-jwk.sh | jq .  (pretty print with jq)
#
# Output: JSON Web Key Set with a single P-256 EC key (including private key material)
#

set -euo pipefail

# Get the script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "Generating EC P-256 JWK for ES256 token signing..." >&2
echo "This key will be used for JWT token signing in Azure pods." >&2
echo "" >&2

# Build and run the generator using Gradle
cd "$PROJECT_ROOT"

if [ ! -f "./gradlew" ]; then
    echo "ERROR: gradlew not found in project root" >&2
    exit 1
fi

# Run the EcJwkGenerator tool
./gradlew -q :server-logic:runGenerator

echo "" >&2
echo "✓ EC JWK generated successfully" >&2
echo "⚠️  IMPORTANT: This JSON includes the PRIVATE KEY material!" >&2
echo "⚠️  KEEP THIS SECRET - only store in Azure Key Vault or secure storage" >&2
