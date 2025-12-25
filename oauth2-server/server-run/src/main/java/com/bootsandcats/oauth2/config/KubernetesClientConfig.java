package com.bootsandcats.oauth2.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

@Configuration
@ConditionalOnExpression(
    "'${oauth2.clients.store:database}' == 'kubernetes'"
        + " || '${oauth2.deny.store:database}' == 'kubernetes'"
        + " || '${oauth2.audit.kubernetes-events.enabled:false}' == 'true'")
public class KubernetesClientConfig {

    @Bean
    @ConditionalOnMissingBean
    public KubernetesClient kubernetesClient(
            @Value("${oauth2.clients.kubernetes.master-url:}") String masterUrl) {
        Config config =
                StringUtils.hasText(masterUrl)
                        ? new ConfigBuilder().withMasterUrl(masterUrl).build()
                        : Config.autoConfigure(null);
        return new KubernetesClientBuilder().withConfig(config).build();
    }
}
