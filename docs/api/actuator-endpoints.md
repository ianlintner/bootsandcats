# Actuator Endpoints

This document provides documentation for all Spring Boot Actuator endpoints exposed by the OAuth2 Authorization Server for health checks, metrics, and monitoring.

## Endpoints Overview

| Endpoint | Method | Description | Production Exposed |
|----------|--------|-------------|-------------------|
| `/actuator/health` | GET | Health status | ✅ Yes |
| `/actuator/health/liveness` | GET | Kubernetes liveness probe | ✅ Yes |
| `/actuator/health/readiness` | GET | Kubernetes readiness probe | ✅ Yes |
| `/actuator/info` | GET | Application info | ✅ Yes |
| `/actuator/prometheus` | GET | Prometheus metrics | ✅ Yes |
| `/actuator/metrics` | GET | Metrics overview | ❌ No (dev only) |
| `/actuator/env` | GET | Environment properties | ❌ No (dev only) |

---

## Health Endpoints

### `GET /actuator/health`

Returns the overall health status of the application.

#### Request

```http
GET /actuator/health HTTP/1.1
Host: auth.example.com
```

#### Response (Healthy)

```json
{
    "status": "UP",
    "groups": [
        "liveness",
        "readiness"
    ]
}
```

#### Response (Unhealthy)

```json
{
    "status": "DOWN",
    "groups": [
        "liveness",
        "readiness"
    ]
}
```

### Health Status Values

| Status | Description |
|--------|-------------|
| `UP` | Component is healthy |
| `DOWN` | Component is unhealthy |
| `OUT_OF_SERVICE` | Component is out of service |
| `UNKNOWN` | Health status is unknown |

---

### `GET /actuator/health/liveness`

Kubernetes liveness probe endpoint. Returns whether the application is alive.

#### Request

```http
GET /actuator/health/liveness HTTP/1.1
Host: auth.example.com
```

#### Response

```json
{
    "status": "UP"
}
```

#### Usage in Kubernetes

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 9000
  initialDelaySeconds: 60
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3
```

---

### `GET /actuator/health/readiness`

Kubernetes readiness probe endpoint. Returns whether the application is ready to receive traffic.

#### Request

```http
GET /actuator/health/readiness HTTP/1.1
Host: auth.example.com
```

#### Response

```json
{
    "status": "UP"
}
```

#### Usage in Kubernetes

```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 9000
  initialDelaySeconds: 30
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 3
```

---

### Health Indicators

When `management.endpoint.health.show-details=when-authorized`, the response includes detailed health information:

```json
{
    "status": "UP",
    "components": {
        "db": {
            "status": "UP",
            "details": {
                "database": "PostgreSQL",
                "validationQuery": "isValid()"
            }
        },
        "diskSpace": {
            "status": "UP",
            "details": {
                "total": 107374182400,
                "free": 85899345920,
                "threshold": 10485760,
                "path": "/app/.",
                "exists": true
            }
        },
        "ping": {
            "status": "UP"
        }
    }
}
```

---

## Info Endpoint

### `GET /actuator/info`

Returns application metadata and build information.

#### Request

```http
GET /actuator/info HTTP/1.1
Host: auth.example.com
```

#### Response

```json
{
    "app": {
        "name": "OAuth2 Authorization Server",
        "description": "Spring Boot OAuth2 Authorization Server with OIDC and PKCE support",
        "version": "1.0.0"
    },
    "build": {
        "artifact": "oauth2-server",
        "name": "oauth2-server",
        "version": "1.0.0-SNAPSHOT",
        "time": "2024-01-15T10:30:00.000Z"
    },
    "java": {
      "version": "21.0.3",
      "vendor": "Eclipse Adoptium"
    }
}
```

---

## Prometheus Metrics

### `GET /actuator/prometheus`

Returns metrics in Prometheus exposition format.

#### Request

```http
GET /actuator/prometheus HTTP/1.1
Host: auth.example.com
```

#### Response

```text
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="G1 Eden Space"} 5.0331648E7
jvm_memory_used_bytes{area="heap",id="G1 Old Gen"} 2.097152E7
jvm_memory_used_bytes{area="heap",id="G1 Survivor Space"} 1048576.0

# HELP http_server_requests_seconds Duration of HTTP server request handling
# TYPE http_server_requests_seconds summary
http_server_requests_seconds_count{method="POST",uri="/oauth2/token",status="200"} 150.0
http_server_requests_seconds_sum{method="POST",uri="/oauth2/token",status="200"} 12.5

# HELP oauth2_tokens_issued_total Number of tokens issued
# TYPE oauth2_tokens_issued_total counter
oauth2_tokens_issued_total 1250.0

# HELP oauth2_tokens_revoked_total Number of tokens revoked
# TYPE oauth2_tokens_revoked_total counter
oauth2_tokens_revoked_total 45.0

# HELP oauth2_authorization_requests_total Number of authorization requests
# TYPE oauth2_authorization_requests_total counter
oauth2_authorization_requests_total 890.0

# HELP oauth2_authorization_failed_total Number of failed authorization attempts
# TYPE oauth2_authorization_failed_total counter
oauth2_authorization_failed_total 12.0
```

---

## Key Metrics

### OAuth2 Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `oauth2_tokens_issued_total` | Counter | Total tokens issued |
| `oauth2_tokens_revoked_total` | Counter | Total tokens revoked |
| `oauth2_authorization_requests_total` | Counter | Total authorization requests |
| `oauth2_authorization_failed_total` | Counter | Total failed authorizations |

### HTTP Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `http_server_requests_seconds` | Summary | HTTP request duration |
| `http_server_requests_seconds_count` | Counter | Total HTTP request count |
| `http_server_requests_seconds_max` | Gauge | Max request duration |

### JVM Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `jvm_memory_used_bytes` | Gauge | JVM memory usage |
| `jvm_memory_max_bytes` | Gauge | Max JVM memory |
| `jvm_gc_pause_seconds` | Summary | GC pause duration |
| `jvm_threads_live_threads` | Gauge | Live thread count |
| `jvm_threads_daemon_threads` | Gauge | Daemon thread count |

### Database Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `hikaricp_connections_active` | Gauge | Active DB connections |
| `hikaricp_connections_pending` | Gauge | Pending connection requests |
| `hikaricp_connections_timeout_total` | Counter | Connection timeouts |

### System Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `system_cpu_usage` | Gauge | System CPU usage |
| `process_cpu_usage` | Gauge | Process CPU usage |
| `system_load_average_1m` | Gauge | 1-minute load average |
| `disk_free_bytes` | Gauge | Free disk space |

---

## Prometheus Configuration

### Scrape Configuration

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'oauth2-server'
    metrics_path: '/actuator/prometheus'
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: keep
        regex: oauth2-server
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
      - source_labels: [__address__, __meta_kubernetes_pod_annotation_prometheus_io_port]
        action: replace
        regex: ([^:]+)(?::\d+)?;(\d+)
        replacement: $1:$2
        target_label: __address__
```

### ServiceMonitor for Prometheus Operator

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: oauth2-server
  namespace: oauth2-system
  labels:
    release: prometheus
spec:
  selector:
    matchLabels:
      app: oauth2-server
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 15s
      scrapeTimeout: 10s
  namespaceSelector:
    matchNames:
      - oauth2-system
```

---

## Alerting Rules

### Prometheus Alerting Rules

```yaml
groups:
  - name: oauth2-server
    rules:
      # High Error Rate
      - alert: OAuth2HighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) /
          sum(rate(http_server_requests_seconds_count[5m])) > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "OAuth2 server error rate > 5%"
          description: "Error rate is {{ $value | printf \"%.2f\" }}%"
      
      # High Latency
      - alert: OAuth2HighLatency
        expr: |
          histogram_quantile(0.95, 
            sum(rate(http_server_requests_seconds_bucket{uri="/oauth2/token"}[5m])) by (le)
          ) > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "OAuth2 token endpoint P95 latency > 2s"
          description: "P95 latency is {{ $value | printf \"%.2f\" }}s"
      
      # Instance Down
      - alert: OAuth2ServerDown
        expr: up{job="oauth2-server"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "OAuth2 server instance is down"
          description: "Instance {{ $labels.instance }} is down"
      
      # High Memory Usage
      - alert: OAuth2HighMemoryUsage
        expr: |
          jvm_memory_used_bytes{area="heap"} / 
          jvm_memory_max_bytes{area="heap"} > 0.85
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "OAuth2 server high heap memory usage"
          description: "Memory usage is {{ $value | printf \"%.0f\" }}%"
      
      # Database Connection Pool Exhausted
      - alert: OAuth2DBConnectionPoolExhausted
        expr: hikaricp_connections_pending > 5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "OAuth2 database connection pool under pressure"
          description: "{{ $value }} pending connection requests"
      
      # Authorization Failures Spike
      - alert: OAuth2AuthorizationFailuresSpike
        expr: |
          rate(oauth2_authorization_failed_total[5m]) > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Spike in OAuth2 authorization failures"
          description: "{{ $value | printf \"%.2f\" }} failures per second"
```

---

## Grafana Dashboard Queries

### Request Rate

```promql
sum(rate(http_server_requests_seconds_count{job="oauth2-server"}[5m])) by (uri)
```

### Error Rate

```promql
sum(rate(http_server_requests_seconds_count{job="oauth2-server",status=~"5.."}[5m])) /
sum(rate(http_server_requests_seconds_count{job="oauth2-server"}[5m])) * 100
```

### P95 Latency

```promql
histogram_quantile(0.95, 
  sum(rate(http_server_requests_seconds_bucket{job="oauth2-server"}[5m])) by (le, uri)
)
```

### Token Issuance Rate

```promql
rate(oauth2_tokens_issued_total[5m])
```

### JVM Memory Usage

```promql
jvm_memory_used_bytes{job="oauth2-server",area="heap"} /
jvm_memory_max_bytes{job="oauth2-server",area="heap"} * 100
```

### Active Database Connections

```promql
hikaricp_connections_active{job="oauth2-server"}
```

---

## Best Practices

### Security

1. **Limit Exposure**: Only expose `health`, `info`, and `prometheus` in production
2. **Network Segmentation**: Only allow Prometheus to scrape metrics endpoint
3. **Authentication**: Consider adding authentication for detailed health info

### Performance

1. **Scrape Interval**: Use 15-30s scrape intervals to balance freshness and load
2. **Metric Cardinality**: Avoid high-cardinality labels to prevent metric explosion
3. **Aggregation**: Use recording rules for frequently-used queries

### Configuration

```yaml
# application-prod.properties
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.show-details=never
management.prometheus.metrics.export.enabled=true
management.metrics.tags.application=oauth2-authorization-server
```

---

## Next Steps

- [Observability Overview](../observability/overview.md) - Complete observability setup
- [Metrics Guide](../observability/metrics.md) - Detailed metrics documentation
- [SLOs](../operations/slos.md) - Service Level Objectives
