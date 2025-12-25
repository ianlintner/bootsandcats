package com.bootsandcats.oauth2.k8s;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.bootsandcats.oauth2.model.SecurityAuditEvent;
import com.bootsandcats.oauth2.model.AuditEventResult;
import com.bootsandcats.oauth2.model.AuditEventType;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Best-effort publisher that emits {@link SecurityAuditEvent} records as Kubernetes Events.
 */
@Component
@ConditionalOnProperty(
        prefix = "oauth2.audit.kubernetes-events",
        name = "enabled",
        havingValue = "true")
public class KubernetesAuditEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(KubernetesAuditEventEmitter.class);

    private final KubernetesClient kubernetesClient;
    private final String namespace;
    private final String podName;

    public KubernetesAuditEventEmitter(
            KubernetesClient kubernetesClient,
            @Value("${oauth2.audit.kubernetes-events.namespace:}") String configuredNamespace,
            @Value("${oauth2.audit.kubernetes-events.pod-name:}") String configuredPodName) {
        this.kubernetesClient = kubernetesClient;
        this.namespace = resolveNamespace(configuredNamespace);
        this.podName = resolvePodName(configuredPodName);

        log.info(
                "Kubernetes audit events enabled (namespace={}, podName={})",
                this.namespace,
                this.podName != null ? this.podName : "(none)");
    }

    public void publish(SecurityAuditEvent auditEvent) {
        if (auditEvent == null) {
            return;
        }

        try {
            Event k8sEvent = toKubernetesEvent(auditEvent);
            // Fabric8 supports typed operations for core/v1 events.
            kubernetesClient.v1().events().inNamespace(namespace).resource(k8sEvent).create();
        } catch (Exception e) {
            // Best-effort: do not break auth flows
            log.debug(
                    "Failed to emit Kubernetes event for audit event {} ({})",
                    auditEvent.getEventId(),
                    auditEvent.getEventType(),
                    e);
        }
    }

    private Event toKubernetesEvent(SecurityAuditEvent e) {
        String reason = toReason(e.getEventType());
        String type =
            e.getResult() != null
                    && (e.getResult() == AuditEventResult.FAILURE
                        || e.getResult() == AuditEventResult.DENIED)
                        ? "Warning"
                        : "Normal";

        String message = buildMessage(e);
        String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

        ObjectReference involvedObject = null;
        if (StringUtils.hasText(podName)) {
            involvedObject =
                    new ObjectReferenceBuilder()
                            .withApiVersion("v1")
                            .withKind("Pod")
                            .withName(podName)
                            .withNamespace(namespace)
                            .build();
        }

        // Use generateName to avoid collisions; K8s will append a unique suffix.
        EventBuilder b =
                new EventBuilder()
                        .withNewMetadata()
                        .withGenerateName("oauth2-audit-")
                        .withNamespace(namespace)
                        .endMetadata()
                        .withType(type)
                        .withReason(reason)
                        .withMessage(message)
                        .withLastTimestamp(now);

        if (involvedObject != null) {
            b = b.withInvolvedObject(involvedObject);
        }

        return b.build();
    }

    private static String buildMessage(SecurityAuditEvent e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getEventType() != null ? e.getEventType().name() : "AUDIT_EVENT");
        if (e.getResult() != null) {
            sb.append(" result=").append(e.getResult().name());
        }
        if (StringUtils.hasText(e.getPrincipal())) {
            sb.append(" principal=").append(e.getPrincipal());
        }
        if (StringUtils.hasText(e.getClientId())) {
            sb.append(" clientId=").append(e.getClientId());
        }
        if (StringUtils.hasText(e.getIpAddress())) {
            sb.append(" ip=").append(e.getIpAddress());
        }
        if (StringUtils.hasText(e.getErrorMessage())) {
            sb.append(" error=").append(truncate(e.getErrorMessage(), 200));
        }
        return truncate(sb.toString(), 1024);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, Math.max(0, maxLen - 3)) + "...";
    }

    private static String toReason(AuditEventType eventType) {
        if (eventType == null) {
            return "AuditEvent";
        }
        String eventTypeName = eventType.name();
        // Convert e.g. LOGIN_SUCCESS -> LoginSuccess
        String[] parts = eventTypeName.trim().toLowerCase(Locale.ROOT).split("[_\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1));
            }
        }
        return sb.length() > 0 ? sb.toString() : "AuditEvent";
    }

    private static String resolveNamespace(String configuredNamespace) {
        if (StringUtils.hasText(configuredNamespace)) {
            return configuredNamespace.trim();
        }
        String fromEnv = System.getenv("POD_NAMESPACE");
        if (StringUtils.hasText(fromEnv)) {
            return fromEnv.trim();
        }
        return "default";
    }

    private static String resolvePodName(String configuredPodName) {
        if (StringUtils.hasText(configuredPodName)) {
            return configuredPodName.trim();
        }
        String fromEnv = System.getenv("POD_NAME");
        if (StringUtils.hasText(fromEnv)) {
            return fromEnv.trim();
        }
        return null;
    }
}
