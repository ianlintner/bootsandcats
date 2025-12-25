package com.bootsandcats.oauth2.k8s;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.bootsandcats.oauth2.service.ClientStore;
import com.bootsandcats.oauth2.k8s.OAuth2ClientSpec;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

/**
 * Kubernetes-backed {@link ClientStore} that persists OAuth2 clients as custom resources.
 */
@Service
@Primary
@ConditionalOnProperty(prefix = "oauth2.clients", name = "store", havingValue = "kubernetes")
public class KubernetesRegisteredClientRepository implements ClientStore {

    private static final Logger log =
            LoggerFactory.getLogger(KubernetesRegisteredClientRepository.class);

    private final MixedOperation<OAuth2Client, OAuth2ClientList, Resource<OAuth2Client>> crdClient;
    private final KubernetesRegisteredClientMapper mapper = new KubernetesRegisteredClientMapper();
    private final String namespace;

    public KubernetesRegisteredClientRepository(
            KubernetesClient kubernetesClient,
            @Value("${oauth2.clients.kubernetes.namespace:}") String configuredNamespace) {
        this.namespace = resolveNamespace(configuredNamespace);
        this.crdClient = kubernetesClient.resources(OAuth2Client.class, OAuth2ClientList.class);
        log.info("Using Kubernetes client store in namespace={}", this.namespace);
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        OAuth2Client existing = findResourceByClientId(registeredClient.getClientId());
        OAuth2Client desired = mapper.toResource(registeredClient, namespace);

        if (existing != null) {
            ObjectMeta meta = desired.getMetadata();
            meta.setName(existing.getMetadata().getName());
            meta.setResourceVersion(existing.getMetadata().getResourceVersion());

            // Preserve flags/notes unless explicitly set by admin via CRD.
            OAuth2ClientSpec existingSpec = existing.getSpec();
            OAuth2ClientSpec desiredSpec = desired.getSpec();
            desiredSpec.setEnabled(
                    existingSpec.getEnabled() != null ? existingSpec.getEnabled() : Boolean.TRUE);
            desiredSpec.setSystem(
                    existingSpec.getSystem() != null ? existingSpec.getSystem() : Boolean.FALSE);
            desiredSpec.setNotes(existingSpec.getNotes());

            // Preserve secretRef when present; allow encodedSecret override from caller.
            if (existingSpec.getSecretRef() != null && desiredSpec.getSecretRef() == null) {
                desiredSpec.setSecretRef(existingSpec.getSecretRef());
            }
        } else {
            desired.getSpec().setEnabled(Boolean.TRUE);
            desired.getSpec().setSystem(Boolean.FALSE);
        }

        crdClient.inNamespace(namespace).resource(desired).createOrReplace();
    }

    @Override
    public RegisteredClient findById(String id) {
        OAuth2Client resource = findByLabel(mapper.selectorForRegisteredClientId(id));
        return toRegisteredClient(resource);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        OAuth2Client resource = findResourceByClientId(clientId);
        return toRegisteredClient(resource);
    }

    @Override
    public List<RegisteredClient> findAllClients() {
        return crdClient.inNamespace(namespace).list().getItems().stream()
                .map(this::toRegisteredClient)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public void deleteByClientId(String clientId) {
        OAuth2Client resource = findResourceByClientId(clientId);
        if (resource != null) {
            crdClient.inNamespace(namespace).withName(resource.getMetadata().getName()).delete();
        }
    }

    private OAuth2Client findResourceByClientId(String clientId) {
        return findByLabel(mapper.selectorForClientId(clientId));
    }

    private OAuth2Client findByLabel(Map<String, String> selector) {
        List<OAuth2Client> items =
                crdClient.inNamespace(namespace).withLabels(selector).list().getItems();
        if (CollectionUtils.isEmpty(items)) {
            return null;
        }
        if (items.size() > 1) {
            log.warn(
                    "Multiple OAuth2Client resources matched selector {}; using first result: {}",
                    selector,
                    items.stream().map(c -> c.getMetadata().getName()).toList());
        }
        return items.getFirst();
    }

    private RegisteredClient toRegisteredClient(OAuth2Client resource) {
        if (resource == null) {
            return null;
        }
        OAuth2ClientSpec spec = resource.getSpec();
        if (spec != null && Boolean.FALSE.equals(spec.getEnabled())) {
            return null;
        }
        if (spec != null && Boolean.TRUE.equals(spec.getSystem())) {
            // System flag is handled by admin code; for runtime lookups we still return it.
        }
        return mapper.toRegisteredClient(resource);
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
