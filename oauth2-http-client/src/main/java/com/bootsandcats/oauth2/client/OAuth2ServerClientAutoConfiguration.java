package com.bootsandcats.oauth2.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

/**
 * Auto-configuration that registers a reusable WebClient and typed HTTP client for the
 * Authorization Server.
 */
@Configuration
@EnableConfigurationProperties(OAuth2ServerClientProperties.class)
public class OAuth2ServerClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "oauth2ServerWebClient")
    public WebClient oauth2ServerWebClient(OAuth2ServerClientProperties properties) {
        HttpClient httpClient =
                HttpClient.create()
                        .option(
                                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                (int) properties.getConnectTimeout().toMillis())
                        .responseTimeout(properties.getReadTimeout());

        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuth2ServerHttpClient oauth2ServerHttpClient(
            WebClient oauth2ServerWebClient, OAuth2ServerClientProperties properties) {
        return new OAuth2ServerHttpClient(oauth2ServerWebClient, properties);
    }
}
