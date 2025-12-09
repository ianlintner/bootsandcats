# System Instructions for Copilot in This Workspace

These are global instructions for Copilot when working in this environment with Azure, AKS (Kubernetes), GitHub, and related tooling.

---

## 1. General Architecture Context

- The primary environment is **Azure**.
- Applications are deployed to **AKS (Azure Kubernetes Service)**.
- Other Azure services are used (for example: Azure DNS, Application Gateway / Ingress, Key Vault, Storage, etc.).
- Copilot SHOULD assume:
  - There is a Kubernetes cluster backing the workloads.
  - Azure resources are managed through ARM/Bicep/Terraform and/or Azure CLI.
  - DNS and public exposure are typically via Azure DNS + Azure networking (e.g., Application Gateway, Load Balancers, Public IPs, etc.).

Where needed, ask clarifying questions instead of guessing infrastructure layout.

---

## 2. Tool / MCP Usage Priorities

When interacting with this environment, Copilot MUST prefer specialized MCP tools over generic shell commands whenever relevant.

### 2.1 Azure MCP

**Tool Name:** `Azure MCP`

**Use this when:**

- Inspecting or managing Azure resources:
  - Resource groups
  - AKS clusters
  - VNets, subnets, NSGs
  - Public IPs, Load Balancers, Application Gateway, etc.
  - Azure DNS zones and records
  - Azure Key Vault, Storage Accounts, etc.
- Getting information about:
  - Cluster credentials
  - Node pools
  - Managed identities, RBAC, or role assignments
- Reading or modifying Azure configuration where the MCP provides a safe API.

**Behavioral rules:**

1. Prefer `Azure MCP` over running `az ...` in a shell for **read operations** whenever possible.
2. For **write / state‑changing** operations via `Azure MCP` (creating / deleting / updating resources):
   - Explain what will be changed.
   - Confirm with the user if there is any risk or ambiguity before proceeding.

### 2.2 KubeView MCP (Kubernetes / AKS)

**Tool Name:** `kubeview-mcp`

**Use this when:**

- Inspecting AKS / Kubernetes cluster state, including:
  - Pods, Deployments, ReplicaSets, DaemonSets, StatefulSets
  - Services, Ingresses, ConfigMaps, Secrets (metadata only), Jobs, CronJobs
  - Namespaces and events
- Debugging workloads:
  - Pod status, logs, events
  - Describing resources
  - Finding failing pods, CrashLoopBackOff, ImagePullBackOff, etc.

**Behavioral rules:**

1. Prefer `kubeview-mcp` over raw `kubectl` commands in the terminal for inspection/debugging.
2. When proposing YAML changes (Deployments, Services, Ingress, etc.):
   - Show the minimal patch or full manifest clearly as code.
   - If an automation tool exists (e.g., GitOps, Helm, Kustomize), prefer editing source files rather than applying `kubectl` imperatively.

### 2.3 GitHub MCP

**Tool Name:** `Github MCP`

**Use this when:**

- Inspecting or managing GitHub resources:
  - Repositories, branches, pull requests, issues (read‑only unless issue tools are explicitly provided).
  - Repository, organization, and environment **secrets** (metadata and updates, not raw values).
  - Workflows, workflow runs, and statuses.
- Performing safe GitHub operations exposed via MCP instead of raw REST calls.

**Behavioral rules:**

1. Prefer `Github MCP` for:
   - Listing and updating GitHub secrets (values must never be shown).
   - Inspecting repo configuration (branches, protection rules, etc.).
   - Querying PRs / workflows instead of running `gh api` directly.
2. For **mutating operations** (e.g., updating secrets, triggering workflows):
   - Clearly state:
     - Which repo / env is affected.
     - What will be added/updated/deleted.
   - Confirm with the user if there is any risk.

### 2.4 VS Code Copilot Standard MCP and Tools

When running inside **Visual Studio Code** with Copilot:

- Use the **standard Copilot MCP and tools** provided by the editor for:
  - Reading and editing workspace files.
  - Running lightweight terminal commands (within the safety rules below).
  - Performing code navigation and search if no dedicated MCP is available.
- Prefer:
  - **VS Code file APIs** (via Copilot tools) over ad‑hoc shell file edits (like `sed`, `cat > file`, etc.).
  - **VS Code search / code navigation tools** for understanding the repo when `Azure MCP`, `Github MCP`, or `kubeview-mcp` are not directly relevant.

---

## 3. Memory / State Persistence MCP Tools

**Tool Name examples:**

- Any configured memory / storage MCP (for example: `memory-store-mcp` or similar).

**Use case:**

- Persisting:
  - Important architectural decisions (ADR‑style notes).
  - Cluster or environment invariants: e.g., “All production namespaces start with `prod-`”.
  - Common troubleshooting patterns and their outcomes.
  - Endpoint URLs, resource naming conventions, and recurrent gotchas.

**Behavioral rules:**

1. When the user explicitly says something “should be remembered” for future sessions, use the memory MCP tool to store it if available.
2. When recalling prior context:
   - Query memory/store tools if they exist before asking the user to repeat architecture details.

---

## 4. Repo Search / Navigation Tools

If MCP tools are available for **repo search**, **code navigation**, or **semantic search**, Copilot SHOULD:

- Prefer MCP search tools (or the built‑in GitHub/VS Code search tools) to answer questions like:
  - “Where is X configured for AKS?”
  - “Where is the ingress for service Y defined?”
  - “How does authentication work for service Z?”
- Use:
  - **Lexical search** for exact string or file name questions.
  - **Semantic search** for conceptual questions (auth flow, retry logic, etc.).

Behavioral rules:

1. When a user asks about “where” or “how” something is implemented in the repo, run search instead of guessing.
2. When editing config / code, open the real file (via repo/MCP tools) and work against the actual content instead of relying solely on memory.

---

## 5. File Editing Strategy

Copilot MUST apply the following strategy when editing files (YAML, JSON, code, Helm charts, Terraform/Bicep, etc.):

1. **Fetch first, then patch.**
   - Always read the file (via repo/MCP tools) before proposing edits, unless the file just got created in the same conversation.
2. **Use minimal, focused edits.**
   - Show only the parts of the file that need to change, unless the user asks for the full file.
   - Mark additions and deletions clearly (or provide a clear patch/diff when possible).
3. **Preserve formatting and comments.**
   - Avoid reformatting the entire file.
   - Keep existing comments and structure intact unless specifically asked to refactor.
4. **Respect tooling ownership.**
   - If the resource is generated (e.g., Helm values, Terraform outputs, Kustomize), edit the **source of truth** file, not the generated output.

---

## 6. Terminal & Command Execution Rules

### 6.1 Avoid Large Commands Using EOF / Heredocs

Copilot MUST NOT propose or run large multi‑line commands using heredocs (EOF or similar) in the terminal such as:

```bash
cat <<EOF > big-config.yaml
...
EOF
```

or

```bash
kubectl apply -f - <<EOF
...
EOF
```

Instead:

1. **Use files.**
   - Create or edit a file (via file/MCP tools) like `deployment.yaml`, `ingress.yaml`, `values.yaml`, or `script.sh`.
   - Then, if appropriate, run a short command referencing that file, e.g.:
     - `kubectl apply -f deployment.yaml`
     - `az deployment group create --template-file main.bicep`
2. If a heredoc is **absolutely required** (rare):
   - Keep it as small as possible.
   - Confirm with the user before running it.

### 6.2 Shell Command Safety

When using the terminal:

1. **Be cautious with destructive commands.**
   - For `kubectl delete`, `az delete`, `rm -rf`, or similar, explain what will happen and confirm with the user first.
2. **Prefer dry‑run / describe / list first.**
   - Example: `kubectl apply --dry-run=server -f ...` before actual apply.
   - Example: `az deployment what-if` where supported.
3. **Avoid long, unreadable commands.**
   - Break them into:
     - Config files, scripts, or aliases.
     - Smaller, well‑explained steps.

---

## 7. Kubernetes & AKS Conventions

Copilot SHOULD follow these conventions unless user/infrastructure clearly differs:

- Use **namespaces** appropriately:
  - Keep system and app workloads separated (e.g., `kube-system`, `monitoring`, `prod-*`, `dev-*`).
- For manifests:
  - Use `apps/v1` for Deployments, `networking.k8s.io/v1` for Ingress.
  - Use clear labels and selectors consistently across resources.
- For AKS-specific features (like Azure CNI, AGIC, Managed Identity, etc.), defer to:
  - Existing patterns in the repo.
  - `Azure MCP` information about the cluster.

---

## 8. DNS and Networking

When questions involve DNS or ingress:

- Use **`Azure MCP`** to:
  - Inspect DNS zones and records.
  - View public IPs and load balancer / application gateway associations.
- Use **`kubeview-mcp`** to:
  - Inspect Ingress resources and Services.
  - Trace from Ingress → Service → Pod (Deployment/StatefulSet).

Copilot SHOULD:

- Explain how DNS name resolution maps to:
  - Azure DNS record → Public IP / load balancer → Ingress / Gateway → Kubernetes Service → Pod.
- Prefer diagrams/conceptual explanation when the user seems unsure about the flow.

---

## 9. When in Doubt

If it’s unclear:

- Ask the user whether to:
  - Use `Azure MCP` or run `az` locally.
  - Use `kubeview-mcp` or `kubectl`.
  - Use `Github MCP` or `gh` CLI.
  - Modify GitOps/Helm/Terraform source vs. applying manual `kubectl` commands.
- Do not assume production changes; ask for the target environment (`dev`, `stage`, `prod`) when it matters.

---

## 10. Delegation to Subagents

If this workspace is configured with **specialized subagents** (for example, separate agents for observability, security, data, or specific services), Copilot MUST:

1. **Prefer delegating** to the most relevant subagent when:
   - The question is scoped to that subagent’s domain (e.g., “debug logs for service X”, “security posture for Y”).
   - A subagent can operate more safely or efficiently than generic commands.
2. **Clearly indicate delegation** in responses, including:
   - Which subagent is being used.
   - What task it is performing.
3. **Avoid redundant work**:
   - Do not repeat the same expensive analysis in the main agent if a subagent already did it.
4. When multiple subagents could apply, ask the user to clarify which area or environment they care about (e.g., “observability vs security vs infra”).

---

## 11. GitHub Secrets Management (`Github MCP` + `gh` CLI)

GitHub repository and environment secrets MUST be managed **safely** using **`Github MCP`** where available, or the **`gh` CLI** as a secondary mechanism.

### 11.1 Preferred tools

- **Primary**: `Github MCP` (for reading metadata and creating/updating secrets).
- **Secondary**: `gh` CLI (`gh secret set`, `gh secret list`, `gh secret delete`, etc.) for repositories or environments that are not wired into MCP or where CLI is explicitly requested.

### 11.2 Behavioral rules for GitHub secrets

1. **Never print secret values** in responses or logs.
2. **Prefer indirection from Azure Key Vault**:
   - Where possible, secrets in GitHub should be references to Azure Key Vault or short‑lived tokens derived from KV, rather than long‑lived static secrets.
3. For **updating secrets via `Github MCP`**:
   - Use the MCP tools to create/update repository or environment secrets, not raw REST calls, when available.
   - Describe:
     - Which repo / environment is being modified.
     - The secret name.
     - That the **value will not be echoed**.
4. For **updating secrets via `gh` CLI**:
   - Propose short commands like:
     ```bash
     gh secret set SECRET_NAME --repo owner/repo
     ```
   - Do NOT inline secret values in the command shown to the user (they should paste interactively or from a secure source).
5. When asked to “show a secret”:
   - Explain that secret values cannot be retrieved from GitHub once set.
   - Instead, point back to the source of truth (Azure Key Vault via `Azure MCP`).

---

## 12. Secret Management and Azure Key Vault

### 12.1 Key Principle: Azure Key Vault is the Source of Truth

In this environment, **all sensitive secrets are stored in Azure Key Vault** and accessed in Kubernetes via the **Azure Key Vault Secret Provider** (CSI driver or similar). Copilot MUST:

1. Treat Azure Key Vault as the **authoritative source of secrets**.
2. Prefer **Key Vault + secret provider** usage over native Kubernetes `Secret` objects that store values directly in etcd.

### 12.2 Behavioral rules for secrets in AKS

1. **Do NOT create plain Kubernetes `Secret` resources** with actual secret values, unless the user explicitly instructs otherwise and understands the risk.
   - If a `Secret` object is absolutely required, recommend:
     - Using it only for non‑sensitive or derived values, OR
     - Referencing data coming from Key Vault mounts / projections.
2. When defining application configuration in Kubernetes:
   - Prefer patterns like:
     - CSI Secret Store driver mounts from Azure Key Vault.
     - Environment variables sourced from files or volumes backed by the Key Vault provider.
3. When asked to “create a secret for X in AKS”:
   - Propose:
     - Creating or updating the secret in Azure Key Vault (via `Azure MCP`).
     - Ensuring the AKS workload references that secret via the Key Vault provider (e.g., SecretProviderClass, volume mounts, or environment variable projections).
4. When asked about “where is secret Y defined?”:
   - First, look in **Azure Key Vault** via `Azure MCP`.
   - Then show how it is wired into:
     - Helm values.
     - Kubernetes manifests (e.g., SecretProviderClass).
     - Application configuration.

### 12.3 Coordination between Key Vault and GitHub

When workflows require GitHub to access a secret that ultimately lives in Azure Key Vault:

1. Prefer:
   - Providing GitHub with a **short‑lived credential** (e.g., federated identity, workload identity, OpenID Connect) that allows it to access Key Vault directly.
2. If a plain GitHub secret must be set:
   - Pull the value from Azure Key Vault (via `Azure MCP` or manually by the user).
   - Use `Github MCP` / `gh` CLI to set the secret.
   - Do not store or repeat the value in Copilot responses.

---
