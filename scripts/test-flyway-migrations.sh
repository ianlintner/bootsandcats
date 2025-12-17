#!/bin/bash
#
# Test Flyway migrations in CI/CD pipeline
# This script validates that migrations can be applied successfully

set -e

echo "=========================================="
echo "Testing Flyway Migrations"
echo "=========================================="

# Check required environment variables
required_vars=("DATABASE_URL" "DATABASE_USERNAME" "DATABASE_PASSWORD")
for var in "${required_vars[@]}"; do
  if [ -z "${!var}" ]; then
    echo "Warning: $var not set, using defaults"
  fi
done

# Set defaults for testing
FLYWAY_URL="${DATABASE_URL:-jdbc:postgresql://localhost:5432/oauth2db}"
FLYWAY_USER="${DATABASE_USERNAME:-postgres}"
FLYWAY_PASSWORD="${DATABASE_PASSWORD:-postgres}"
IMAGE_TAG="${FLYWAY_IMAGE_TAG:-flyway-migrate:latest}"

echo "Database URL: ${FLYWAY_URL}"
echo "Image: ${IMAGE_TAG}"
echo ""

# Run info to check current state
echo "Checking current migration state..."
docker run --rm \
  -e FLYWAY_URL="${FLYWAY_URL}" \
  -e FLYWAY_USER="${FLYWAY_USER}" \
  -e FLYWAY_PASSWORD="${FLYWAY_PASSWORD}" \
  "${IMAGE_TAG}" info

echo ""

# Run migrate
echo "Applying migrations..."
docker run --rm \
  -e FLYWAY_URL="${FLYWAY_URL}" \
  -e FLYWAY_USER="${FLYWAY_USER}" \
  -e FLYWAY_PASSWORD="${FLYWAY_PASSWORD}" \
  "${IMAGE_TAG}" migrate

echo ""

# Verify migrations were applied
echo "Verifying migrations..."
docker run --rm \
  -e FLYWAY_URL="${FLYWAY_URL}" \
  -e FLYWAY_USER="${FLYWAY_USER}" \
  -e FLYWAY_PASSWORD="${FLYWAY_PASSWORD}" \
  "${IMAGE_TAG}" validate

echo ""
echo "✅ All migrations applied successfully!"
