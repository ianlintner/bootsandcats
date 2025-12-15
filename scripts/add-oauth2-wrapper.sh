#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <app-name> [client-id] [cookie-prefix]" >&2
  echo "Env overrides: ISSUER, AUTHORIZATION_ENDPOINT, TOKEN_ENDPOINT_HOST, TOKEN_ENDPOINT_PORT, TOKEN_ENDPOINT_URI, JWKS_HOST, JWKS_PORT, JWKS_URI, STAT_PREFIX" >&2
  exit 1
fi

APP="$1"
CLIENT_ID="${2:-$1}"
COOKIE_PREFIX="${3:-${APP//-/_}}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATES_DIR="${ROOT}/infrastructure/k8s/templates/oauth2"
ISTIO_DIR="${ROOT}/infrastructure/k8s/istio"
APP_DIR="${ROOT}/infrastructure/k8s/apps/${APP}"

ISSUER="${ISSUER:-https://oauth2.cat-herding.net}"
AUTHORIZATION_ENDPOINT="${AUTHORIZATION_ENDPOINT:-${ISSUER}/oauth2/authorize}"
TOKEN_ENDPOINT_HOST="${TOKEN_ENDPOINT_HOST:-oauth2-server.default.svc.cluster.local}"
TOKEN_ENDPOINT_PORT="${TOKEN_ENDPOINT_PORT:-9000}"
TOKEN_ENDPOINT_URI="${TOKEN_ENDPOINT_URI:-http://${TOKEN_ENDPOINT_HOST}:${TOKEN_ENDPOINT_PORT}/oauth2/token}"
JWKS_HOST="${JWKS_HOST:-oauth2-server.default.svc.cluster.local}"
JWKS_PORT="${JWKS_PORT:-9000}"
JWKS_URI="${JWKS_URI:-http://${JWKS_HOST}:${JWKS_PORT}/oauth2/jwks}"
STAT_PREFIX_DEFAULT="${APP//-/_}_oauth"
STAT_PREFIX="${STAT_PREFIX:-${STAT_PREFIX_DEFAULT}}"

export APP CLIENT_ID COOKIE_PREFIX ISSUER AUTHORIZATION_ENDPOINT TOKEN_ENDPOINT_HOST TOKEN_ENDPOINT_PORT TOKEN_ENDPOINT_URI JWKS_HOST JWKS_PORT JWKS_URI STAT_PREFIX

ensure_app_dir() {
  if [[ ! -d "${APP_DIR}" ]]; then
    echo "Creating app directory ${APP_DIR}"
    mkdir -p "${APP_DIR}"
    cat > "${APP_DIR}/kustomization.yaml" <<'EOF'
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources: []
EOF
  fi
}

ENV_SUBST_VARS='${APP} ${CLIENT_ID} ${COOKIE_PREFIX} ${ISSUER} ${AUTHORIZATION_ENDPOINT} ${TOKEN_ENDPOINT_HOST} ${TOKEN_ENDPOINT_PORT} ${TOKEN_ENDPOINT_URI} ${JWKS_HOST} ${JWKS_PORT} ${JWKS_URI} ${STAT_PREFIX}'

render_template() {
  local template="$1"
  local target="$2"
  mkdir -p "$(dirname "${target}")"
  envsubst "${ENV_SUBST_VARS}" < "${template}" > "${target}"
  echo "Rendered ${target}"
}

append_if_missing() {
  local file="$1"
  local content="$2"
  if ! grep -Fq "${content}" "${file}"; then
    echo "${content}" >> "${file}"
    echo "Updated ${file} (+ ${content})"
  fi
}

ensure_patch_in_app_kustomization() {
  local app_kustom="${APP_DIR}/kustomization.yaml"
  if [[ ! -f "${app_kustom}" ]]; then
    echo "App kustomization not found, creating."
    ensure_app_dir
  fi

  if ! grep -q '^patchesStrategicMerge:' "${app_kustom}"; then
    printf '\npatchesStrategicMerge:\n' >> "${app_kustom}"
  fi
  append_if_missing "${app_kustom}" "  - ${APP}-oauth2-patch.yaml"
}

ensure_istio_resources() {
  local istio_kustom="${ISTIO_DIR}/kustomization.yaml"
  append_if_missing "${istio_kustom}" "  - envoyfilter-${APP}-oauth2-exchange.yaml"
  append_if_missing "${istio_kustom}" "  - envoyfilter-${APP}-jwt-to-headers.yaml"
}

main() {
  ensure_app_dir

  render_template "${TEMPLATES_DIR}/envoyfilter-oauth2-exchange.yaml.tmpl" \
    "${ISTIO_DIR}/envoyfilter-${APP}-oauth2-exchange.yaml"

  render_template "${TEMPLATES_DIR}/envoyfilter-jwt-to-headers.yaml.tmpl" \
    "${ISTIO_DIR}/envoyfilter-${APP}-jwt-to-headers.yaml"

  render_template "${TEMPLATES_DIR}/deployment-oauth2-patch.yaml.tmpl" \
    "${APP_DIR}/${APP}-oauth2-patch.yaml"

  ensure_patch_in_app_kustomization
  ensure_istio_resources

  cat <<EOF
Done. Next steps:
- Create CSI secret keys: ${APP}-client-secret and ${APP}-oauth-hmac-secret
- Register redirect: https://<public-host>/_oauth2/callback
- kustomize build infrastructure/k8s to verify manifests
EOF
}

main "$@"
