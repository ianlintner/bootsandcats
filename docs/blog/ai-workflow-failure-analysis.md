# AI-Powered Workflow Failure Analysis

*Published: December 10, 2025*

## Introduction

We've integrated the [AI Workflow Failure Summary Action](https://github.com/ianlintner/ai_summary_action) into our CI/CD pipelines to automatically analyze and diagnose workflow failures using AI. This post answers common questions about the action and how we're using it in the bootsandcats OAuth2 Authorization Server project.

## What is the AI Summary Action?

The AI Workflow Failure Summary Action is a GitHub Action that uses Large Language Models (LLMs) to analyze failed workflow runs and provide:

- 🤖 **Intelligent root cause analysis** - AI-powered examination of failure logs
- 📊 **Structured summaries** - Clear breakdown of errors, causes, and fix recommendations
- 🎯 **Automatic issue creation** - Creates GitHub issues with detailed analysis
- 🧠 **Memory & caching** - Learns from past failures for context-aware analysis
- 🔌 **Multiple LLM providers** - Supports OpenAI, Azure OpenAI, GitHub Models, and Anthropic

## Frequently Asked Questions

### How does it work?

When a workflow fails, a new job called `analyze-failure` runs (using `if: failure()`). This job:

1. Extracts logs from all failed jobs in the workflow
2. Sends the logs to an LLM for analysis
3. Generates a structured summary with root cause, error details, and recommendations
4. Creates a GitHub issue with the analysis (if configured)

### What LLM providers are supported?

The action supports:

| Provider | Model Examples |
|----------|----------------|
| **OpenAI** | `gpt-4o-mini`, `gpt-4o`, `gpt-4-turbo` |
| **Azure OpenAI** | Any deployed model |
| **GitHub Models** | `gpt-4o`, available models in marketplace |
| **Anthropic** | `claude-3-5-sonnet-20241022`, `claude-3-opus` |

### Is my data secure?

Important security considerations:

- **Workflow logs are sent to the LLM provider** - Ensure your workflows use GitHub's [secret masking](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
- **Only failed job logs are analyzed** - Not your entire codebase
- **Log truncation** - Only the last 500 lines (configurable) per job are sent
- **API keys stored as secrets** - Never hardcode credentials

### How do we use it in bootsandcats?

We've added the AI analysis to all our workflows:

```yaml
analyze-failure:
  name: Analyze Workflow Failure
  runs-on: ubuntu-latest
  if: failure()
  needs: [<all-other-jobs>]
  steps:
    - name: Checkout
      uses: actions/checkout@v4

    - name: Analyze Workflow Failure
      uses: ianlintner/ai_summary_action@v0.0.4
      with:
        github-token: ${{ secrets.GITHUB_TOKEN }}
        llm-provider: 'openai'
        openai-api-key: ${{ secrets.OPENAI_API_KEY }}
        create-issue: 'true'
        issue-label: 'ai-analysis'
        enable-memory: 'true'
        cache-strategy: 'actions-cache'
        memory-scope: 'branch'
        max-historical-runs: '10'
```

### What does the output look like?

The action generates structured summaries like:

```markdown
## Summary
The workflow failed due to a missing environment variable in the test job.

## Root Cause
The test suite expects `DATABASE_URL` to be defined but it was not set 
in the workflow environment.

## Error Details
- **Location**: test/integration/db.test.js:15
- **Error**: `Error: DATABASE_URL is not defined`

## Recommended Actions
1. Add `DATABASE_URL` to your workflow environment variables
2. Or add it to repository secrets and reference as `${{ secrets.DATABASE_URL }}`
3. Ensure the test database is accessible from GitHub Actions runners

## Additional Context
The error occurred in all test jobs, suggesting this is a configuration 
issue rather than a code problem.
```

### What are the key features we're using?

| Feature | Configuration | Purpose |
|---------|---------------|---------|
| **Memory** | `enable-memory: 'true'` | Learns from past failures |
| **Auto Issues** | `create-issue: 'true'` | Creates trackable GitHub issues |
| **Branch Scope** | `memory-scope: 'branch'` | Context per feature branch |
| **Historical Analysis** | `max-historical-runs: '10'` | References recent failures |

### How do I customize the AI prompts?

You can provide custom prompts via files:

```yaml
- name: Analyze Workflow Failure
  uses: ianlintner/ai_summary_action@v0.0.4
  with:
    github-token: ${{ secrets.GITHUB_TOKEN }}
    openai-api-key: ${{ secrets.OPENAI_API_KEY }}
    custom-system-prompt: '.github/prompts/system-prompt.md'
    custom-user-prompt: '.github/prompts/user-prompt.md'
```

This lets you tailor the analysis to your specific tech stack and common failure patterns.

### What permissions does it need?

The action requires these permissions in your workflow:

```yaml
permissions:
  contents: read    # To checkout code
  actions: read     # To read workflow logs
  issues: write     # To create issues (if enabled)
```

### What are the costs?

Costs depend on your LLM provider:

- **OpenAI**: Pay-per-token pricing (gpt-4o-mini is very affordable)
- **Azure OpenAI**: Based on your Azure deployment
- **GitHub Models**: Included with GitHub Copilot subscription
- **Anthropic**: Pay-per-token pricing

For most projects, expect pennies per analysis with `gpt-4o-mini`.

## Which Workflows Have AI Analysis?

| Workflow | File | Analysis Scope |
|----------|------|----------------|
| **CI** | `ci.yml` | Build, tests, static analysis, integration tests, smoke tests, deployment |
| **Security Scan** | `security.yml` | OWASP dependency check failures |
| **Smoke Test** | `smoke-test.yml` | Scheduled smoke test failures |
| **Generate Clients** | `generate-clients.yml` | SDK generation failures |

## Getting Started

To add this to your own projects:

1. **Add the OpenAI API key as a secret**:
   - Go to Repository Settings → Secrets and variables → Actions
   - Create `OPENAI_API_KEY` with your API key

2. **Add the failure analysis job** to your workflow (see example above)

3. **Update permissions** at the workflow level to include `actions: read` and `issues: write`

## Resources

- [AI Summary Action Repository](https://github.com/ianlintner/ai_summary_action)
- [Action Documentation](https://ianlintner.github.io/ai_summary_action/)
- [GitHub Marketplace](https://github.com/marketplace/actions/ai-workflow-failure-summary)
- [Custom Prompts Guide](https://ianlintner.github.io/ai_summary_action/usage/custom-prompts/)

## Conclusion

AI-powered failure analysis significantly reduces the time spent debugging CI/CD issues. Instead of manually sifting through logs, you get intelligent summaries that point you directly to the problem and suggest solutions. Combined with memory features, the system becomes smarter over time, recognizing recurring issues and providing increasingly relevant recommendations.

---

*Have questions about the AI Summary Action? [Open an issue](https://github.com/ianlintner/ai_summary_action/issues) or check the [documentation](https://ianlintner.github.io/ai_summary_action/).*
