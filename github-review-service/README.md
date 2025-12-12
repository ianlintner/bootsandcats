# GitHub Review Service

FastAPI microservice that queues GitHub profile assessments and summarizes public repositories with OpenAI via LangChain.

## Running locally

```bash
python -m venv .venv
source .venv/bin/activate
pip install -e .
export OPENAI_API_KEY=... # required
export REDIS_URL=redis://localhost:6379/0
uvicorn app.main:app --reload --port 8000
```

Start a worker in another terminal:

```bash
python worker.py
```

## Configuration

Environment variables (can be prefixed with `GH_REVIEW_`):

- `OPENAI_API_KEY` (required) – OpenAI access key.
- `GITHUB_TOKEN` (optional) – GitHub token for higher rate limits.
- `REDIS_URL` – Redis connection string.
- `OPENAI_MODEL` – Chat model, defaults to `gpt-4o-mini`.

### Azure Key Vault / Secrets Store

- SecretProviderClass: `github-review-secrets-provider`
- Expected Key Vault objects: `openai-api-key`, `github-token`, `github-review-client-secret`, `redis-uri`.
  Ensure these are populated in `inker-kv` and synced into `github-review-secrets` before deploying.

## API

- `POST /api/assessments` `{ github_username?, email? }` → `{ job_id, status }`
- `GET /api/assessments/{job_id}` → job status and result
- `GET /api/status` healthcheck

JWT-derived headers (`x-jwt-sub`, `x-jwt-username`, `x-jwt-email`) are expected when deployed behind Istio/Lua auth filters. Set `GH_REVIEW_ALLOW_ANONYMOUS=true` for local dev.
