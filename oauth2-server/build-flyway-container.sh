#!/bin/bash
#
# Build and optionally push the Flyway migration container
# Usage:
#   ./build-flyway-container.sh           # Build only
#   ./build-flyway-container.sh --push    # Build and push to ACR
#   ./build-flyway-container.sh --tag v1  # Build with custom tag

set -e

# Configuration
REGISTRY="${CONTAINER_REGISTRY:-gabby.azurecr.io}"
IMAGE_NAME="flyway-migrate"
DEFAULT_TAG="latest"

# Parse arguments
PUSH=false
TAG=$DEFAULT_TAG

while [[ $# -gt 0 ]]; do
  case $1 in
    --push)
      PUSH=true
      shift
      ;;
    --tag)
      TAG="$2"
      shift 2
      ;;
    --registry)
      REGISTRY="$2"
      shift 2
      ;;
    --help)
      echo "Usage: $0 [OPTIONS]"
      echo ""
      echo "Options:"
      echo "  --push              Push image to registry after building"
      echo "  --tag TAG           Use custom tag (default: latest)"
      echo "  --registry REGISTRY Use custom registry (default: gabby.azurecr.io)"
      echo "  --help              Show this help message"
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      echo "Use --help for usage information"
      exit 1
      ;;
  esac
done

FULL_IMAGE_NAME="${REGISTRY}/${IMAGE_NAME}:${TAG}"

echo "=========================================="
echo "Building Flyway Migration Container"
echo "=========================================="
echo "Image: ${FULL_IMAGE_NAME}"
echo "Push: ${PUSH}"
echo ""

# Check if we're in the correct directory
if [ ! -f "Dockerfile.flyway" ]; then
  echo "Error: Dockerfile.flyway not found"
  echo "Please run this script from the oauth2-server directory"
  exit 1
fi

# Check if migration directory exists
if [ ! -d "server-dao/src/main/resources/db/migration" ]; then
  echo "Error: Migration directory not found"
  echo "Expected: server-dao/src/main/resources/db/migration"
  exit 1
fi

# Count migration files
MIGRATION_COUNT=$(find server-dao/src/main/resources/db/migration -name "V*.sql" | wc -l)
echo "Found ${MIGRATION_COUNT} migration files"
echo ""

# Build the container
echo "Building container..."
docker build -f Dockerfile.flyway -t "${FULL_IMAGE_NAME}" -t "${REGISTRY}/${IMAGE_NAME}:${TAG}" .

if [ $? -eq 0 ]; then
  echo ""
  echo "✅ Container built successfully!"
  echo "   ${FULL_IMAGE_NAME}"
else
  echo ""
  echo "❌ Container build failed!"
  exit 1
fi

# Test the container locally
echo ""
echo "Testing container..."
if docker run --rm "${FULL_IMAGE_NAME}" --version; then
  echo "✅ Container test passed!"
else
  echo "❌ Container test failed!"
  exit 1
fi

# Push if requested
if [ "$PUSH" = true ]; then
  echo ""
  echo "=========================================="
  echo "Pushing to Registry"
  echo "=========================================="
  
  # Check if logged in to registry
  if ! docker info | grep -q "${REGISTRY}"; then
    echo "Attempting to login to ${REGISTRY}..."
    if command -v az &> /dev/null; then
      # Use Azure CLI for ACR login
      ACR_NAME=$(echo "${REGISTRY}" | cut -d. -f1)
      az acr login --name "${ACR_NAME}"
    else
      echo "Error: Not logged in to ${REGISTRY}"
      echo "Please run: az acr login --name <registry-name>"
      exit 1
    fi
  fi
  
  echo "Pushing ${FULL_IMAGE_NAME}..."
  docker push "${FULL_IMAGE_NAME}"
  
  if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Container pushed successfully!"
  else
    echo ""
    echo "❌ Container push failed!"
    exit 1
  fi
fi

echo ""
echo "=========================================="
echo "Summary"
echo "=========================================="
echo "Image: ${FULL_IMAGE_NAME}"
echo "Migration files: ${MIGRATION_COUNT}"
echo "Status: ✅ Ready to deploy"
echo ""
echo "Next steps:"
echo "  • Test locally: docker-compose -f ../infrastructure/docker-compose.yml up flyway-migrate"
echo "  • Deploy to K8s: kubectl apply -f ../infrastructure/k8s/apps/oauth2-server/flyway-migration-job.yaml"
if [ "$PUSH" = false ]; then
  echo "  • Push to registry: $0 --push"
fi
echo ""
