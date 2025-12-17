# Azure EC JWK Manager for PowerShell
#
# This script handles EC JWK management for OAuth2 Authorization Server on Azure AKS
#
# Prerequisites:
#   - Azure CLI installed
#   - kubectl configured
#   - PowerShell 7.0+
#
# Usage:
#   .\scripts\azure-ec-jwk-manager.ps1 -Command generate
#   .\scripts\azure-ec-jwk-manager.ps1 -Command upload -FilePath "my-jwk.json"
#   .\scripts\azure-ec-jwk-manager.ps1 -Command deploy
#   .\scripts\azure-ec-jwk-manager.ps1 -Command verify
#

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('generate', 'upload', 'download', 'validate', 'update-k8s', 'deploy', 'verify', 'rotate', 'show-config', 'help')]
    [string]$Command,

    [Parameter(Mandatory=$false)]
    [string]$FilePath,

    [Parameter(Mandatory=$false)]
    [string]$VaultName = $env:AZURE_VAULT_NAME ?? "inker-kv",

    [Parameter(Mandatory=$false)]
    [string]$SecretName = $env:AZURE_JWK_SECRET_NAME ?? "oauth2-jwk",

    [Parameter(Mandatory=$false)]
    [string]$K8sNamespace = $env:K8S_NAMESPACE ?? "default"
)

$ErrorActionPreference = "Stop"
$VerbosePreference = "Continue"

$script:K8sSecretName = "oauth2-jwk-secret"
$script:ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$script:TempDir = Join-Path ([System.IO.Path]::GetTempPath()) "azure-jwk-$(Get-Random)"

# Create temp directory
New-Item -ItemType Directory -Path $script:TempDir -Force | Out-Null

function Cleanup {
    if (Test-Path $script:TempDir) {
        Remove-Item -Recurse -Force $script:TempDir | Out-Null
    }
}

trap {
    Write-Error "Error: $_"
    Cleanup
    exit 1
}

function Write-Success {
    Write-Host "✓ $args" -ForegroundColor Green
}

function Write-Warning {
    Write-Host "⚠ $args" -ForegroundColor Yellow
}

function Write-Error {
    Write-Host "✗ $args" -ForegroundColor Red
}

function Check-Prerequisites {
    Write-Host "Checking prerequisites..." -ForegroundColor Blue

    $tools = @('az', 'kubectl', 'jq')
    foreach ($tool in $tools) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
            Write-Error "$tool is not installed"
            exit 1
        }
    }

    # Check Azure CLI login
    try {
        $null = az account show 2>$null
    } catch {
        Write-Error "Not logged into Azure. Run: az login"
        exit 1
    }

    # Check kubectl context
    try {
        $null = kubectl config current-context 2>$null
    } catch {
        Write-Error "kubectl not configured. Set up your AKS context"
        exit 1
    }

    Write-Success "All prerequisites satisfied"
}

function Generate-EcJwk {
    Write-Host "Generating EC P-256 JWK (ES256)..." -ForegroundColor Blue

    Push-Location $script:ProjectRoot
    try {
        $output = & .\gradlew.bat -q -p server-logic :server-logic:runGenerator 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Failed to generate EC JWK"
            Write-Host $output
            return $null
        }

        $outputFile = Join-Path $script:TempDir "generated-jwk.json"
        $output | Out-File -FilePath $outputFile -Encoding UTF8

        Write-Success "EC JWK generated successfully"
        return $outputFile
    } finally {
        Pop-Location
    }
}

function Validate-Jwk {
    param([string]$JwkFile)

    if (-not (Test-Path $JwkFile)) {
        Write-Error "JWK file not found: $JwkFile"
        return $false
    }

    try {
        $jwk = Get-Content $JwkFile -Raw | ConvertFrom-Json
    } catch {
        Write-Error "Invalid JSON in JWK file: $_"
        return $false
    }

    if (-not $jwk.keys -or $jwk.keys.Count -eq 0) {
        Write-Error "No keys found in JWK set"
        return $false
    }

    $key = $jwk.keys[0]

    if ($key.kty -ne "EC") {
        Write-Error "Expected key type EC, got $($key.kty)"
        return $false
    }

    if ($key.alg -ne "ES256") {
        Write-Error "Expected algorithm ES256, got $($key.alg)"
        return $false
    }

    if ($key.crv -ne "P-256") {
        Write-Error "Expected curve P-256, got $($key.crv)"
        return $false
    }

    # The authorization server must have a PRIVATE signing key. The public JWKS
    # (/oauth2/jwks) intentionally omits the private component ("d").
    if ([string]::IsNullOrWhiteSpace($key.d)) {
        Write-Error "JWK is missing private key material (EC field 'd')."
        Write-Error "Generate a full signing key (includes 'd'); do NOT copy from /oauth2/jwks."
        return $false
    }

    Write-Success "JWK validation passed (EC P-256 with ES256, private key present)"
    return $true
}

function Upload-ToKeyVault {
    param([string]$JwkFile)

    if (-not (Validate-Jwk $JwkFile)) {
        return $false
    }

    Write-Host "Uploading JWK to Azure Key Vault..." -ForegroundColor Blue
    Write-Host "  Vault: $VaultName"
    Write-Host "  Secret: $SecretName"

    # Check if vault exists
    try {
        $null = az keyvault show --name $VaultName 2>$null
    } catch {
        Write-Error "Key Vault not found: $VaultName"
        return $false
    }

    # Upload the secret
    $content = Get-Content $JwkFile -Raw
    $content | az keyvault secret set `
        --vault-name $VaultName `
        --name $SecretName `
        --file $JwkFile | Out-Null

    if ($LASTEXITCODE -eq 0) {
        Write-Success "JWK uploaded to Key Vault"

        # Display confirmation
        $version = az keyvault secret show `
            --vault-name $VaultName `
            --name $SecretName `
            --query properties.version -o tsv
        Write-Host "  Secret version: $version" -ForegroundColor Gray
        return $true
    } else {
        Write-Error "Failed to upload to Key Vault"
        return $false
    }
}

function Download-FromKeyVault {
    Write-Host "Downloading JWK from Key Vault..." -ForegroundColor Blue

    $outputFile = Join-Path $script:TempDir "downloaded-jwk.json"

    $content = az keyvault secret show `
        --vault-name $VaultName `
        --name $SecretName `
        --query value -o json

    if ($LASTEXITCODE -eq 0) {
        $content | Out-File -FilePath $outputFile -Encoding UTF8
        Write-Success "JWK downloaded from Key Vault"
        return $outputFile
    } else {
        Write-Error "Failed to download from Key Vault"
        return $null
    }
}

function Update-K8sSecret {
    param([string]$JwkFile)

    if (-not (Test-Path $JwkFile)) {
        Write-Error "JWK file not found: $JwkFile"
        return $false
    }

    Write-Host "Updating Kubernetes secret in namespace: $K8sNamespace" -ForegroundColor Blue

    # Create or update the secret
    kubectl create secret generic $script:K8sSecretName `
        --from-file="static-jwk=$JwkFile" `
        --namespace=$K8sNamespace `
        --dry-run=client -o yaml | kubectl apply -f - | Out-Null

    if ($LASTEXITCODE -eq 0) {
        Write-Success "Kubernetes secret updated"
        return $true
    } else {
        Write-Error "Failed to update Kubernetes secret"
        return $false
    }
}

function Deploy-ToAks {
    Write-Host "Deploying to AKS..." -ForegroundColor Blue

    # Update SecretProviderClass
    Write-Host "Applying SecretProviderClass..." -ForegroundColor Gray
    kubectl apply -f "$script:ProjectRoot\k8s\secret-provider-class.yaml" | Out-Null

    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to apply SecretProviderClass"
        return $false
    }
    Write-Success "SecretProviderClass applied"

    # Update Deployment
    Write-Host "Applying Deployment..." -ForegroundColor Gray
    kubectl apply -f "$script:ProjectRoot\k8s\deployment.yaml" | Out-Null

    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to apply Deployment"
        return $false
    }
    Write-Success "Deployment applied"

    # Wait for rollout
    Write-Host "Waiting for deployment to roll out (this may take a minute)..." -ForegroundColor Gray
    kubectl rollout status deployment/oauth2-server `
        --namespace=$K8sNamespace `
        --timeout=5m | Out-Null

    if ($LASTEXITCODE -eq 0) {
        Write-Success "Deployment rolled out successfully"
        return $true
    } else {
        Write-Error "Deployment rollout failed or timed out"
        return $false
    }
}

function Verify-Configuration {
    Write-Host "Verifying configuration..." -ForegroundColor Blue
    $allOk = $true

    # Check Key Vault secret
    Write-Host "Checking Key Vault secret..." -ForegroundColor Gray
    az keyvault secret show `
        --vault-name $VaultName `
        --name $SecretName | Out-Null

    if ($LASTEXITCODE -eq 0) {
        Write-Success "Key Vault secret exists"
    } else {
        Write-Error "Key Vault secret not found"
        $allOk = $false
    }

    # Check Kubernetes secret
    Write-Host "Checking Kubernetes secret..." -ForegroundColor Gray
    kubectl get secret $script:K8sSecretName `
        --namespace=$K8sNamespace | Out-Null

    if ($LASTEXITCODE -eq 0) {
        Write-Success "Kubernetes secret exists"
    } else {
        Write-Error "Kubernetes secret not found"
        $allOk = $false
    }

    # Check deployment
    Write-Host "Checking deployment..." -ForegroundColor Gray
    kubectl get deployment oauth2-server `
        --namespace=$K8sNamespace | Out-Null

    if ($LASTEXITCODE -eq 0) {
        Write-Success "Deployment exists"

        # Check pod status
        $ready = kubectl get deployment oauth2-server `
            --namespace=$K8sNamespace `
            -o jsonpath='{.status.readyReplicas}'
        $desired = kubectl get deployment oauth2-server `
            --namespace=$K8sNamespace `
            -o jsonpath='{.spec.replicas}'

        if ($ready -eq $desired) {
            Write-Success "All pods are ready ($ready/$desired)"
        } else {
            Write-Warning "Not all pods ready ($ready/$desired)"
        }
    } else {
        Write-Error "Deployment not found"
        $allOk = $false
    }

    if ($allOk) {
        Write-Success "All verifications passed"
        return $true
    } else {
        Write-Error "Some verifications failed"
        return $false
    }
}

function Rotate-Keys {
    param([string]$NewJwkFile)

    if (-not (Validate-Jwk $NewJwkFile)) {
        return $false
    }

    Write-Host "Rotating keys (keeping old key for backward compatibility)..." -ForegroundColor Blue

    # Download current keys
    $currentJwk = Download-FromKeyVault
    if (-not $currentJwk) {
        Write-Warning "Could not download current keys (may be first rotation)"
        return Upload-ToKeyVault $NewJwkFile
    }

    # Merge keys
    Write-Host "Merging old and new keys..." -ForegroundColor Gray
    $current = Get-Content $currentJwk -Raw | ConvertFrom-Json
    $new = Get-Content $NewJwkFile -Raw | ConvertFrom-Json

    $merged = @{
        keys = @($current.keys) + @($new.keys)
    }

    $mergedFile = Join-Path $script:TempDir "merged-jwk.json"
    $merged | ConvertTo-Json -Depth 10 | Out-File -FilePath $mergedFile -Encoding UTF8

    # Upload merged keys
    if (Upload-ToKeyVault $mergedFile) {
        Write-Success "Keys rotated successfully"
        Write-Host "  Old key is retained for token validation" -ForegroundColor Gray
        Write-Host "  New key will be used for signing" -ForegroundColor Gray
        return $true
    } else {
        Write-Error "Failed to rotate keys"
        return $false
    }
}

function Show-Help {
    Write-Host @"
Azure EC JWK Manager - Manage OAuth2 signing keys for AKS

Usage: $PSCommandPath -Command <command> [-FilePath <path>] [-VaultName <name>] [-K8sNamespace <namespace>]

Commands:
  generate              Generate a new EC P-256 JWK (ES256)
  upload                Upload JWK to Azure Key Vault
  download              Download JWK from Azure Key Vault
  validate              Validate JWK format and content
  update-k8s            Update Kubernetes secret with JWK
  deploy                Deploy/update everything to AKS
  verify                Verify the complete configuration
  rotate                Rotate keys (keep old, add new)
  show-config           Display current configuration
  help                  Show this help message

Parameters:
  -Command              Command to execute (required)
  -FilePath             Path to JWK file (for upload/validate/rotate)
  -VaultName            Azure Key Vault name (default: inker-kv)
  -SecretName           Secret name in Key Vault (default: oauth2-jwk)
  -K8sNamespace         Kubernetes namespace (default: default)

Examples:
  # Complete setup
  .\$($MyInvocation.MyCommand.Name) -Command generate | Out-File my-jwk.json
  .\$($MyInvocation.MyCommand.Name) -Command validate -FilePath my-jwk.json
  .\$($MyInvocation.MyCommand.Name) -Command upload -FilePath my-jwk.json
  .\$($MyInvocation.MyCommand.Name) -Command deploy

  # Rotate keys
  .\$($MyInvocation.MyCommand.Name) -Command generate | Out-File new-jwk.json
  .\$($MyInvocation.MyCommand.Name) -Command rotate -FilePath new-jwk.json
  .\$($MyInvocation.MyCommand.Name) -Command deploy

  # Verify everything works
  .\$($MyInvocation.MyCommand.Name) -Command verify

"@
}

function Show-Config {
    Write-Host "Current configuration:" -ForegroundColor Blue
    Write-Host "  Azure Vault Name:     $VaultName"
    Write-Host "  JWK Secret Name:      $SecretName"
    Write-Host "  K8s Secret Name:      $($script:K8sSecretName)"
    Write-Host "  K8s Namespace:        $K8sNamespace"
    Write-Host "  Project Root:         $($script:ProjectRoot)"
}

# Main execution
try {
    switch ($Command) {
        "generate" {
            Check-Prerequisites
            $file = Generate-EcJwk
            if ($file) {
                Get-Content $file -Raw
            }
        }
        "upload" {
            Check-Prerequisites
            if (-not $FilePath) {
                Write-Error "Please provide FilePath parameter"
                exit 1
            }
            Upload-ToKeyVault $FilePath
        }
        "download" {
            Check-Prerequisites
            $file = Download-FromKeyVault
            if ($file) {
                Get-Content $file -Raw
            }
        }
        "validate" {
            if (-not $FilePath) {
                Write-Error "Please provide FilePath parameter"
                exit 1
            }
            Validate-Jwk $FilePath
        }
        "update-k8s" {
            Check-Prerequisites
            if (-not $FilePath) {
                Write-Error "Please provide FilePath parameter"
                exit 1
            }
            Update-K8sSecret $FilePath
        }
        "deploy" {
            Check-Prerequisites
            Deploy-ToAks
        }
        "verify" {
            Check-Prerequisites
            Verify-Configuration
        }
        "rotate" {
            Check-Prerequisites
            if (-not $FilePath) {
                Write-Error "Please provide FilePath parameter"
                exit 1
            }
            Rotate-Keys $FilePath
        }
        "show-config" {
            Show-Config
        }
        "help" {
            Show-Help
        }
        default {
            Write-Error "Unknown command: $Command"
            Show-Help
            exit 1
        }
    }
} finally {
    Cleanup
}

