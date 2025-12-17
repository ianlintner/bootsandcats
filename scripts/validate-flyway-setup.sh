#!/bin/bash
#
# Validate Flyway Migration Container Setup
# This script checks that all required files are in place

set -e

echo "=========================================="
echo "Flyway Migration Setup Validation"
echo "=========================================="
echo ""

# Track validation results
ERRORS=0
WARNINGS=0

# Function to check file exists
check_file() {
  if [ -f "$1" ]; then
    echo "✅ $1"
  else
    echo "❌ MISSING: $1"
    ((ERRORS++))
  fi
}

# Function to check directory exists
check_dir() {
  if [ -d "$1" ]; then
    echo "✅ $1"
  else
    echo "❌ MISSING: $1"
    ((ERRORS++))
  fi
}

# Check if we're in the right directory
if [ ! -f "settings.gradle.kts" ]; then
  echo "❌ Error: Run this script from the project root directory"
  exit 1
fi

echo "Checking Flyway container files..."
check_file "oauth2-server/Dockerfile.flyway"
check_file "oauth2-server/.dockerignore.flyway"
check_file "oauth2-server/build-flyway-container.sh"
check_file "oauth2-server/FLYWAY_CONTAINER.md"
check_file "oauth2-server/FLYWAY_QUICKSTART.md"
check_file "oauth2-server/FLYWAY_MIGRATION_IMPLEMENTATION.md"
echo ""

echo "Checking migration scripts..."
check_dir "oauth2-server/server-dao/src/main/resources/db/migration"
MIGRATION_COUNT=$(find oauth2-server/server-dao/src/main/resources/db/migration -name "V*.sql" 2>/dev/null | wc -l)
if [ "$MIGRATION_COUNT" -gt 0 ]; then
  echo "✅ Found ${MIGRATION_COUNT} migration files"
else
  echo "❌ No migration files found"
  ((ERRORS++))
fi
echo ""

echo "Checking Docker Compose configuration..."
check_file "infrastructure/docker-compose.yml"
if grep -q "flyway-migrate:" infrastructure/docker-compose.yml; then
  echo "✅ flyway-migrate service configured in docker-compose.yml"
else
  echo "❌ flyway-migrate service not found in docker-compose.yml"
  ((ERRORS++))
fi
echo ""

echo "Checking Kubernetes configuration..."
check_file "infrastructure/k8s/apps/oauth2-server/flyway-migration-job.yaml"
check_file "infrastructure/k8s/apps/oauth2-server/kustomization.yaml"
if grep -q "flyway-migration-job.yaml" infrastructure/k8s/apps/oauth2-server/kustomization.yaml; then
  echo "✅ flyway-migration-job.yaml referenced in kustomization.yaml"
else
  echo "⚠️  WARNING: flyway-migration-job.yaml not in kustomization.yaml"
  ((WARNINGS++))
fi
echo ""

echo "Checking Spring Boot configuration..."
check_file "oauth2-server/server-ui/src/main/resources/application-prod-no-flyway.properties"
echo ""

echo "Checking scripts..."
check_file "scripts/test-flyway-migrations.sh"
if [ -x "oauth2-server/build-flyway-container.sh" ]; then
  echo "✅ build-flyway-container.sh is executable"
else
  echo "⚠️  WARNING: build-flyway-container.sh is not executable"
  echo "   Run: chmod +x oauth2-server/build-flyway-container.sh"
  ((WARNINGS++))
fi
if [ -x "scripts/test-flyway-migrations.sh" ]; then
  echo "✅ test-flyway-migrations.sh is executable"
else
  echo "⚠️  WARNING: test-flyway-migrations.sh is not executable"
  echo "   Run: chmod +x scripts/test-flyway-migrations.sh"
  ((WARNINGS++))
fi
echo ""

echo "Checking Docker availability..."
if command -v docker &> /dev/null; then
  echo "✅ Docker is installed"
  if docker info &> /dev/null; then
    echo "✅ Docker daemon is running"
  else
    echo "⚠️  WARNING: Docker daemon is not running"
    ((WARNINGS++))
  fi
else
  echo "❌ Docker is not installed"
  ((ERRORS++))
fi
echo ""

# Summary
echo "=========================================="
echo "Validation Summary"
echo "=========================================="
if [ $ERRORS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
  echo "✅ All checks passed!"
  echo ""
  echo "Next steps:"
  echo "  1. Build the container: cd oauth2-server && ./build-flyway-container.sh"
  echo "  2. Test locally: cd ../infrastructure && docker-compose up flyway-migrate"
  echo "  3. Push to registry: cd ../oauth2-server && ./build-flyway-container.sh --push"
  echo ""
  exit 0
elif [ $ERRORS -eq 0 ]; then
  echo "⚠️  Setup complete with ${WARNINGS} warning(s)"
  echo ""
  echo "You can proceed but should address the warnings."
  exit 0
else
  echo "❌ Setup incomplete: ${ERRORS} error(s), ${WARNINGS} warning(s)"
  echo ""
  echo "Please fix the errors above before proceeding."
  exit 1
fi
