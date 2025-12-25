package com.bootsandcats.oauth2.security;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.bootsandcats.oauth2.k8s.OAuth2DenyRule;
import com.bootsandcats.oauth2.k8s.OAuth2DenyRuleList;
import com.bootsandcats.oauth2.k8s.OAuth2DenyRuleSpec;
import com.bootsandcats.oauth2.model.DenyMatchField;
import com.bootsandcats.oauth2.model.DenyMatchType;
import com.bootsandcats.oauth2.model.DenyRuleEntity;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

/** Kubernetes-backed deny rule store using the OAuth2DenyRule custom resource. */
@Service
@ConditionalOnProperty(prefix = "oauth2.deny", name = "store", havingValue = "kubernetes")
public class KubernetesDenyRuleStore implements DenyRuleStore {

    private static final Logger log = LoggerFactory.getLogger(KubernetesDenyRuleStore.class);

    private static final String ID_ANNOTATION = "oauth.bootsandcats.com/id";
    private static final String MATCH_FIELD_LABEL = "oauth.bootsandcats.com/deny-match-field";
    private static final String PROVIDER_LABEL = "oauth.bootsandcats.com/deny-provider";

    private final MixedOperation<OAuth2DenyRule, OAuth2DenyRuleList, Resource<OAuth2DenyRule>> crd;
    private final String namespace;

    public KubernetesDenyRuleStore(
            KubernetesClient kubernetesClient,
            @Value("${oauth2.deny.kubernetes.namespace:}") String configuredNamespace) {
        this.namespace = resolveNamespace(configuredNamespace);
        this.crd = kubernetesClient.resources(OAuth2DenyRule.class, OAuth2DenyRuleList.class);
        log.info("Using Kubernetes deny rule store in namespace={}", this.namespace);
    }

    @Override
    public List<DenyRuleEntity> findAll() {
        return crd.inNamespace(namespace).list().getItems().stream().map(this::toEntity).toList();
    }

    @Override
    public Optional<DenyRuleEntity> findById(long id) {
        OAuth2DenyRule resource = crd.inNamespace(namespace).withName(nameForId(id)).get();
        return Optional.ofNullable(resource).map(this::toEntity);
    }

    @Override
    public DenyRuleEntity save(DenyRuleEntity entity) {
        Long id = entity.getId();
        if (id == null) {
            id = nextId();
            entity.setId(id);
        }

        OAuth2DenyRule desired = toResource(entity);

        OAuth2DenyRule existing = crd.inNamespace(namespace).withName(desired.getMetadata().getName()).get();
        if (existing != null && existing.getMetadata() != null) {
            desired.getMetadata().setResourceVersion(existing.getMetadata().getResourceVersion());
        }

        crd.inNamespace(namespace).resource(desired).createOrReplace();
        return entity;
    }

    @Override
    public void delete(DenyRuleEntity entity) {
        if (entity.getId() == null) {
            return;
        }
        crd.inNamespace(namespace).withName(nameForId(entity.getId())).delete();
    }

    @Override
    public List<DenyRuleEntity> findActiveRulesForProvider(String provider, DenyMatchField matchField) {
        String providerNorm = StringUtils.hasText(provider) ? provider.trim() : "";

        String labelValue = matchField.name().toLowerCase(Locale.ROOT);
        List<OAuth2DenyRule> items =
                crd.inNamespace(namespace).withLabel(MATCH_FIELD_LABEL, labelValue).list().getItems();

        return items.stream()
                .map(this::toEntity)
                .filter(DenyRuleEntity::isEnabled)
                .filter(
                        r -> {
                            String p = r.getProvider();
                            if (!StringUtils.hasText(p)) {
                                return true;
                            }
                            if ("*".equals(p)) {
                                return true;
                            }
                            return p.equalsIgnoreCase(providerNorm);
                        })
                .toList();
    }

    private OAuth2DenyRule toResource(DenyRuleEntity entity) {
        OAuth2DenyRule resource = new OAuth2DenyRule();

        ObjectMeta meta = new ObjectMeta();
        meta.setName(nameForId(entity.getId()));
        meta.setNamespace(namespace);

        Map<String, String> annotations = new HashMap<>();
        annotations.put(ID_ANNOTATION, String.valueOf(entity.getId()));
        meta.setAnnotations(annotations);

        Map<String, String> labels = new HashMap<>();
        if (entity.getMatchField() != null) {
            labels.put(MATCH_FIELD_LABEL, entity.getMatchField().name().toLowerCase(Locale.ROOT));
        }
        String provider = entity.getProvider();
        labels.put(PROVIDER_LABEL, sanitizeLabelValue(StringUtils.hasText(provider) ? provider : "global"));
        meta.setLabels(labels);

        resource.setMetadata(meta);

        OAuth2DenyRuleSpec spec = new OAuth2DenyRuleSpec();
        spec.setEnabled(entity.isEnabled());
        spec.setProvider(StringUtils.hasText(entity.getProvider()) ? entity.getProvider() : null);
        spec.setMatchField(entity.getMatchField() != null ? entity.getMatchField().name() : null);
        spec.setMatchType(entity.getMatchType() != null ? entity.getMatchType().name() : null);
        spec.setPattern(entity.getPattern());
        spec.setNormalizedValue(entity.getNormalizedValue());
        spec.setReason(entity.getReason());
        spec.setCreatedBy(entity.getCreatedBy());
        spec.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        spec.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        resource.setSpec(spec);

        return resource;
    }

    private DenyRuleEntity toEntity(OAuth2DenyRule resource) {
        DenyRuleEntity entity = new DenyRuleEntity();

        ObjectMeta meta = resource.getMetadata();
        OAuth2DenyRuleSpec spec = resource.getSpec();

        Long id = tryParseId(meta);
        entity.setId(id);

        if (spec != null) {
            entity.setEnabled(Boolean.TRUE.equals(spec.getEnabled()));
            entity.setProvider(StringUtils.hasText(spec.getProvider()) ? spec.getProvider() : null);

            if (StringUtils.hasText(spec.getMatchField())) {
                try {
                    entity.setMatchField(DenyMatchField.valueOf(spec.getMatchField().trim()));
                } catch (IllegalArgumentException ignored) {
                    // ignore invalid values
                }
            }

            if (StringUtils.hasText(spec.getMatchType())) {
                try {
                    entity.setMatchType(DenyMatchType.valueOf(spec.getMatchType().trim()));
                } catch (IllegalArgumentException ignored) {
                    // ignore invalid values
                }
            }

            entity.setPattern(spec.getPattern());
            entity.setNormalizedValue(spec.getNormalizedValue());
            entity.setReason(spec.getReason());
            entity.setCreatedBy(spec.getCreatedBy());

            entity.setCreatedAt(parseInstant(spec.getCreatedAt()));
            entity.setUpdatedAt(parseInstant(spec.getUpdatedAt()));
        }

        return entity;
    }

    private Long nextId() {
        return crd.inNamespace(namespace).list().getItems().stream()
                .map(r -> tryParseId(r.getMetadata()))
                .filter(v -> v != null && v > 0)
                .max(Comparator.naturalOrder())
                .orElse(0L)
                + 1;
    }

    private static Long tryParseId(ObjectMeta meta) {
        if (meta == null) {
            return null;
        }

        Map<String, String> annotations = meta.getAnnotations();
        if (annotations != null && annotations.containsKey(ID_ANNOTATION)) {
            try {
                return Long.parseLong(annotations.get(ID_ANNOTATION));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        String name = meta.getName();
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String prefix = "deny-rule-";
        if (name.startsWith(prefix)) {
            try {
                return Long.parseLong(name.substring(prefix.length()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String nameForId(long id) {
        return "deny-rule-" + id;
    }

    private static String sanitizeLabelValue(String value) {
        String v = value.trim();
        if (v.length() > 63) {
            v = v.substring(0, 63);
        }
        // Label values can contain [A-Za-z0-9_.-]
        v = v.replaceAll("[^A-Za-z0-9_.-]", "-");
        if (v.isEmpty()) {
            return "unknown";
        }
        return v;
    }

    private static String resolveNamespace(String configuredNamespace) {
        if (configuredNamespace != null && !configuredNamespace.isBlank()) {
            return configuredNamespace;
        }
        String fromEnv = System.getenv("POD_NAMESPACE");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return "default";
    }
}
