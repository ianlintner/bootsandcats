package com.bootsandcats.oauth2.k8s;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OAuth2Client extends CustomResource<OAuth2ClientSpec, OAuth2ClientStatus>
        implements Namespaced {}
