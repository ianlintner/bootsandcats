# Maintenance and Hygiene

This project uses Gradle (via the wrapper) and ships a pre-commit helper to keep the repo consistent. Run these commands from the repo root unless noted.

## Fast daily checks

- Format & lint gate: `./gradlew spotlessCheck spotbugsMain`
- Unit tests only: `./gradlew test`
- Quick app smoke on UI module: `./gradlew :server-ui:fastTests`

## Full verification (CI-equivalent)

- All checks and integration tests: `./gradlew check`
- Full build with artifacts: `./gradlew build`
- Pre-commit bundle (same tasks as the Git hook): `./scripts/pre-commit-checks.sh`

## Hooks and tooling

- Enable the bundled Git hook: `git config core.hooksPath githooks`
- The hook runs `spotlessCheck`, `spotbugsMain`, and `build` via Gradle.
- Use `./gradlew spotlessApply` to auto-fix formatting before committing.

## Troubleshooting helpers

- JWT/Envoy diagnostics: `./scripts/diagnose-jwt-validation.sh`
- Smoke OAuth clients: `./scripts/oauth2-smoke-test.sh`
- Azure setup automation: `./scripts/complete-azure-setup.sh --auto` (see `scripts/README.md`).
