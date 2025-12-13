# Google Cloud GKE Deployment

This guide covers deploying the OAuth2 Authorization Server to Google Kubernetes Engine (GKE) with Cloud SQL for PostgreSQL and Memorystore for Redis.

## Architecture

```mermaid
graph TB
    subgraph "Google Cloud Platform"
        subgraph "Cloud Load Balancing"
            GCLB[Global HTTP(S) Load Balancer]
            CloudArmor[Cloud Armor WAF]
        end
        
        subgraph "GKE Cluster"
            subgraph "System Workloads"
                IngressController[GKE Ingress Controller]
                ConfigConnector[Config Connector]
            end
            
            subgraph "oauth2-system namespace"
                Pod1[oauth2-server-1]
                Pod2[oauth2-server-2]
                Pod3[oauth2-server-3]
                CloudSQLProxy[Cloud SQL Proxy Sidecar]
            end
            
            subgraph "monitoring namespace"
                Prometheus[Prometheus]
                Grafana[Grafana]
            end
        end
        
        subgraph "Managed Services"
            CloudSQL[(Cloud SQL<br/>PostgreSQL)]
            Memorystore[(Memorystore<br/>Redis)]
            SecretManager[Secret Manager]
            KMS[Cloud KMS]
            CloudTrace[Cloud Trace]
            CloudMonitoring[Cloud Monitoring]
        end
        
        subgraph "Identity"
            WorkloadIdentity[Workload Identity]
            IAM[IAM]
        end
    end
    
    Internet[Internet] --> GCLB
    GCLB --> CloudArmor
    CloudArmor --> IngressController
    IngressController --> Pod1
    IngressController --> Pod2
    IngressController --> Pod3
    
    CloudSQLProxy --> CloudSQL
    Pod1 --> Memorystore
    Pod2 --> Memorystore
    Pod3 --> Memorystore
    
    Pod1 --> SecretManager
    Pod1 --> CloudTrace
    Pod1 --> CloudMonitoring
    
    WorkloadIdentity --> IAM
    IAM --> SecretManager
    IAM --> CloudSQL
```

## Prerequisites

- Google Cloud SDK (`gcloud`) installed and configured
- kubectl installed
- Helm 3.x installed
- GCP project with billing enabled

## Infrastructure Setup

### 1. Set Environment Variables

```bash
export PROJECT_ID="your-project-id"
export REGION="us-central1"
export ZONE="us-central1-a"
export CLUSTER_NAME="oauth2-gke-cluster"
export NETWORK_NAME="oauth2-network"

# Set default project
gcloud config set project $PROJECT_ID
```

### 2. Enable Required APIs

```bash
gcloud services enable \
  container.googleapis.com \
  sqladmin.googleapis.com \
  redis.googleapis.com \
  secretmanager.googleapis.com \
  cloudkms.googleapis.com \
  cloudbuild.googleapis.com \
  cloudtrace.googleapis.com \
  logging.googleapis.com \
  monitoring.googleapis.com
```

### 3. Create VPC Network

```bash
# Create VPC
gcloud compute networks create $NETWORK_NAME \
  --subnet-mode=custom

# Create subnet for GKE
gcloud compute networks subnets create oauth2-subnet \
  --network=$NETWORK_NAME \
  --region=$REGION \
  --range=10.0.0.0/20 \
  --secondary-range pods=10.4.0.0/14,services=10.8.0.0/20

# Create Cloud NAT for egress
gcloud compute routers create oauth2-router \
  --network=$NETWORK_NAME \
  --region=$REGION

gcloud compute routers nats create oauth2-nat \
  --router=oauth2-router \
  --region=$REGION \
  --auto-allocate-nat-external-ips \
  --nat-all-subnet-ip-ranges
```

### 4. Create GKE Cluster

```bash
gcloud container clusters create $CLUSTER_NAME \
  --region=$REGION \
  --num-nodes=1 \
  --machine-type=e2-standard-4 \
  --disk-size=100 \
  --disk-type=pd-ssd \
  --enable-autoscaling \
  --min-nodes=1 \
  --max-nodes=5 \
  --network=$NETWORK_NAME \
  --subnetwork=oauth2-subnet \
  --cluster-secondary-range-name=pods \
  --services-secondary-range-name=services \
  --enable-ip-alias \
  --enable-network-policy \
  --workload-pool=$PROJECT_ID.svc.id.goog \
  --enable-shielded-nodes \
  --enable-autorepair \
  --enable-autoupgrade \
  --release-channel=regular \
  --addons=HttpLoadBalancing,HorizontalPodAutoscaling,GcePersistentDiskCsiDriver

# Get credentials
gcloud container clusters get-credentials $CLUSTER_NAME --region=$REGION
```

### 5. Create Cloud SQL for PostgreSQL

```bash
export SQL_INSTANCE_NAME="oauth2-postgres"
export DB_PASSWORD=$(openssl rand -base64 24)

# Create Cloud SQL instance
gcloud sql instances create $SQL_INSTANCE_NAME \
  --database-version=POSTGRES_15 \
  --tier=db-custom-2-8192 \
  --region=$REGION \
  --availability-type=REGIONAL \
  --storage-type=SSD \
  --storage-size=100 \
  --backup-start-time=02:00 \
  --enable-bin-log \
  --maintenance-window-day=SUN \
  --maintenance-window-hour=03 \
  --database-flags=log_checkpoints=on,log_connections=on,log_disconnections=on \
  --root-password="$DB_PASSWORD"

# Create database
gcloud sql databases create oauth2db --instance=$SQL_INSTANCE_NAME

# Create user
gcloud sql users create oauth2_user \
  --instance=$SQL_INSTANCE_NAME \
  --password="$DB_PASSWORD"

# Get connection name
export SQL_CONNECTION_NAME=$(gcloud sql instances describe $SQL_INSTANCE_NAME --format='value(connectionName)')
echo "Cloud SQL Connection Name: $SQL_CONNECTION_NAME"
```

### 6. Create Memorystore for Redis

```bash
export REDIS_INSTANCE_NAME="oauth2-redis"

# Create Redis instance
gcloud redis instances create $REDIS_INSTANCE_NAME \
  --size=5 \
  --region=$REGION \
  --tier=STANDARD_HA \
  --redis-version=redis_7_0 \
  --network=$NETWORK_NAME \
  --connect-mode=PRIVATE_SERVICE_ACCESS \
  --transit-encryption-mode=SERVER_AUTHENTICATION

# Get Redis host
export REDIS_HOST=$(gcloud redis instances describe $REDIS_INSTANCE_NAME --region=$REGION --format='value(host)')
export REDIS_PORT=$(gcloud redis instances describe $REDIS_INSTANCE_NAME --region=$REGION --format='value(port)')
echo "Redis Host: $REDIS_HOST:$REDIS_PORT"
```

### 7. Set Up Secret Manager

```bash
# Create secrets
echo -n "$DB_PASSWORD" | gcloud secrets create database-password \
  --replication-policy="automatic" \
  --data-file=-

echo -n "$(openssl rand -base64 32)" | gcloud secrets create oauth2-demo-client-secret \
  --replication-policy="automatic" \
  --data-file=-

echo -n "$(openssl rand -base64 32)" | gcloud secrets create oauth2-m2m-client-secret \
  --replication-policy="automatic" \
  --data-file=-

echo -n "jdbc:postgresql:///oauth2db?cloudSqlInstance=$SQL_CONNECTION_NAME&socketFactory=com.google.cloud.sql.postgres.SocketFactory" | gcloud secrets create database-url \
  --replication-policy="automatic" \
  --data-file=-
```

### 8. Configure Workload Identity

```bash
# Create Kubernetes service account
kubectl create namespace oauth2-system

# Create GCP service account
gcloud iam service-accounts create oauth2-server-sa \
  --display-name="OAuth2 Server Service Account"

# Grant permissions
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:oauth2-server-sa@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/cloudsql.client"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:oauth2-server-sa@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:oauth2-server-sa@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/cloudtrace.agent"

gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:oauth2-server-sa@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/logging.logWriter"

# Allow Kubernetes SA to impersonate GCP SA
gcloud iam service-accounts add-iam-policy-binding \
  oauth2-server-sa@$PROJECT_ID.iam.gserviceaccount.com \
  --member="serviceAccount:$PROJECT_ID.svc.id.goog[oauth2-system/oauth2-server]" \
  --role="roles/iam.workloadIdentityUser"
```

## Kubernetes Configuration

### Kubernetes Service Account

```yaml
# serviceaccount.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: oauth2-server
  namespace: oauth2-system
  annotations:
    iam.gke.io/gcp-service-account: oauth2-server-sa@PROJECT_ID.iam.gserviceaccount.com
```

### External Secrets with Secret Manager

```yaml
# external-secrets.yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: oauth2-server-secrets
  namespace: oauth2-system
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: gcp-secret-store
    kind: ClusterSecretStore
  target:
    name: oauth2-server-secrets
    creationPolicy: Owner
  data:
    - secretKey: DATABASE_PASSWORD
      remoteRef:
        key: database-password
    - secretKey: OAUTH2_DEMO_CLIENT_SECRET
      remoteRef:
        key: oauth2-demo-client-secret
    - secretKey: OAUTH2_M2M_CLIENT_SECRET
      remoteRef:
        key: oauth2-m2m-client-secret
```

### GKE Deployment with Cloud SQL Proxy

```yaml
# deployment-gcp.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: oauth2-server
  namespace: oauth2-system
  labels:
    app: oauth2-server
spec:
  replicas: 3
  selector:
    matchLabels:
      app: oauth2-server
  template:
    metadata:
      labels:
        app: oauth2-server
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/path: "/actuator/prometheus"
        prometheus.io/port: "9000"
    spec:
      serviceAccountName: oauth2-server
      securityContext:
        runAsNonRoot: true
        runAsUser: 1001
        fsGroup: 1001
      containers:
        # OAuth2 Server Container
        - name: oauth2-server
          image: ghcr.io/ianlintner/bootsandcats/oauth2-server:latest
          ports:
            - name: http
              containerPort: 9000
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: DATABASE_URL
              value: "jdbc:postgresql://127.0.0.1:5432/oauth2db"
            - name: DATABASE_USERNAME
              value: "oauth2_user"
            - name: OAUTH2_ISSUER_URL
              value: "https://auth.example.com"
            - name: REDIS_HOST
              value: "${REDIS_HOST}"
            - name: REDIS_PORT
              value: "${REDIS_PORT}"
            # Enable Google Cloud Trace
            - name: GOOGLE_CLOUD_PROJECT
              value: "${PROJECT_ID}"
          envFrom:
            - secretRef:
                name: oauth2-server-secrets
          resources:
            requests:
              cpu: "500m"
              memory: "1Gi"
            limits:
              cpu: "2000m"
              memory: "4Gi"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 9000
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 9000
            initialDelaySeconds: 30
            periodSeconds: 5
          volumeMounts:
            - name: tmp
              mountPath: /tmp
        
        # Cloud SQL Proxy Sidecar
        - name: cloud-sql-proxy
          image: gcr.io/cloud-sql-connectors/cloud-sql-proxy:2.8.0
          args:
            - "--structured-logs"
            - "--port=5432"
            - "${SQL_CONNECTION_NAME}"
          securityContext:
            runAsNonRoot: true
          resources:
            requests:
              cpu: "100m"
              memory: "256Mi"
            limits:
              cpu: "500m"
              memory: "512Mi"
      
      volumes:
        - name: tmp
          emptyDir: {}
      
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                labelSelector:
                  matchExpressions:
                    - key: app
                      operator: In
                      values:
                        - oauth2-server
                topologyKey: kubernetes.io/hostname
      
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: topology.kubernetes.io/zone
          whenUnsatisfiable: DoNotSchedule
          labelSelector:
            matchLabels:
              app: oauth2-server
```

### GKE Ingress with Cloud Load Balancing

```yaml
# ingress-gcp.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: oauth2-server
  namespace: oauth2-system
  annotations:
    kubernetes.io/ingress.class: "gce"
    kubernetes.io/ingress.global-static-ip-name: "oauth2-server-ip"
    networking.gke.io/managed-certificates: "oauth2-server-cert"
    networking.gke.io/v1beta1.FrontendConfig: "oauth2-frontend-config"
spec:
  rules:
    - host: auth.example.com
      http:
        paths:
          - path: /*
            pathType: ImplementationSpecific
            backend:
              service:
                name: oauth2-server
                port:
                  number: 80
---
apiVersion: networking.gke.io/v1beta1
kind: ManagedCertificate
metadata:
  name: oauth2-server-cert
  namespace: oauth2-system
spec:
  domains:
    - auth.example.com
---
apiVersion: networking.gke.io/v1beta1
kind: FrontendConfig
metadata:
  name: oauth2-frontend-config
  namespace: oauth2-system
spec:
  redirectToHttps:
    enabled: true
    responseCodeName: MOVED_PERMANENTLY_DEFAULT
```

### Cloud Armor Security Policy

```bash
# Create Cloud Armor security policy
gcloud compute security-policies create oauth2-security-policy \
  --description="Security policy for OAuth2 server"

# Add rate limiting rule
gcloud compute security-policies rules create 1000 \
  --security-policy=oauth2-security-policy \
  --action=throttle \
  --rate-limit-threshold-count=100 \
  --rate-limit-threshold-interval-sec=60 \
  --conform-action=allow \
  --exceed-action=deny-429 \
  --enforce-on-key=IP \
  --description="Rate limit by IP"

# Add WAF rules
gcloud compute security-policies rules create 2000 \
  --security-policy=oauth2-security-policy \
  --expression="evaluatePreconfiguredExpr('xss-v33-stable')" \
  --action=deny-403 \
  --description="Block XSS attacks"

gcloud compute security-policies rules create 2001 \
  --security-policy=oauth2-security-policy \
  --expression="evaluatePreconfiguredExpr('sqli-v33-stable')" \
  --action=deny-403 \
  --description="Block SQL injection"

# Apply to backend service
gcloud compute backend-services update oauth2-server-backend \
  --security-policy=oauth2-security-policy \
  --global
```

## Observability with Cloud Operations

### Cloud Monitoring Dashboard

```json
{
  "displayName": "OAuth2 Authorization Server",
  "gridLayout": {
    "widgets": [
      {
        "title": "Request Rate",
        "xyChart": {
          "dataSets": [{
            "timeSeriesQuery": {
              "timeSeriesFilter": {
                "filter": "metric.type=\"custom.googleapis.com/http_server_requests_seconds_count\""
              }
            }
          }]
        }
      },
      {
        "title": "Error Rate",
        "xyChart": {
          "dataSets": [{
            "timeSeriesQuery": {
              "timeSeriesFilter": {
                "filter": "metric.type=\"custom.googleapis.com/http_server_requests_seconds_count\" AND metric.labels.status=~\"5..\""
              }
            }
          }]
        }
      },
      {
        "title": "Latency P95",
        "xyChart": {
          "dataSets": [{
            "timeSeriesQuery": {
              "timeSeriesFilter": {
                "filter": "metric.type=\"custom.googleapis.com/http_server_requests_seconds\""
              }
            }
          }]
        }
      },
      {
        "title": "Tokens Issued",
        "xyChart": {
          "dataSets": [{
            "timeSeriesQuery": {
              "timeSeriesFilter": {
                "filter": "metric.type=\"custom.googleapis.com/oauth2_tokens_issued_total\""
              }
            }
          }]
        }
      }
    ]
  }
}
```

### Cloud Monitoring Alerts

```bash
# Create notification channel
gcloud alpha monitoring channels create \
  --display-name="OAuth2 On-Call" \
  --type=email \
  --channel-labels=email_address=oncall@example.com

# Create alert policies
gcloud alpha monitoring policies create \
  --display-name="OAuth2 High Error Rate" \
  --condition-display-name="Error rate > 5%" \
  --condition-filter='metric.type="custom.googleapis.com/http_server_requests_seconds_count" AND metric.labels.status=~"5.."' \
  --condition-threshold-value=5 \
  --condition-threshold-comparison=COMPARISON_GT \
  --condition-threshold-duration=300s \
  --notification-channels="projects/$PROJECT_ID/notificationChannels/<channel-id>"
```

## Deployment Script

```bash
#!/bin/bash
# deploy-gcp.sh

set -e

NAMESPACE="oauth2-system"

echo "Creating namespace..."
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

echo "Creating service account..."
envsubst < serviceaccount.yaml | kubectl apply -f -

echo "Applying ConfigMaps..."
kubectl apply -f configmap.yaml

echo "Applying External Secrets..."
kubectl apply -f external-secrets.yaml

echo "Waiting for secrets to sync..."
sleep 30

echo "Applying Deployment..."
envsubst < deployment-gcp.yaml | kubectl apply -f -

echo "Applying Service..."
kubectl apply -f service.yaml

echo "Reserving static IP..."
gcloud compute addresses create oauth2-server-ip --global

echo "Applying Ingress..."
kubectl apply -f ingress-gcp.yaml

echo "Applying HPA..."
kubectl apply -f hpa.yaml

echo "Applying PDB..."
kubectl apply -f infrastructure/k8s/apps/configs/pdb.yaml

echo "Waiting for deployment..."
kubectl rollout status deployment/oauth2-server -n $NAMESPACE

echo "Deployment complete!"
kubectl get pods -n $NAMESPACE

echo ""
echo "Static IP: $(gcloud compute addresses describe oauth2-server-ip --global --format='value(address)')"
echo "Update your DNS to point auth.example.com to this IP"
```

## Cost Optimization

| Resource | Configuration | Monthly Cost (Est.) |
|----------|---------------|---------------------|
| GKE Cluster (3 nodes e2-standard-4) | Regional | ~$300 |
| Cloud SQL PostgreSQL (db-custom-2-8192, HA) | Regional | ~$250 |
| Memorystore Redis (5GB, Standard HA) | Standard | ~$150 |
| Cloud Load Balancing | Global | ~$25 |
| Cloud Armor | Standard | ~$5 |
| Secret Manager | Standard | ~$1 |
| Cloud Logging & Monitoring | Standard | ~$50 |
| **Total** | | **~$780/month** |

## Next Steps

- [Azure Deployment](azure.md) - Alternative deployment on Azure
- [AWS Deployment](aws.md) - Alternative deployment on AWS
- [Observability](../observability/overview.md) - Configure monitoring
- [Operations](../operations/slos.md) - SLOs and runbooks
