package com.bootsandcats.oauth2.k8s;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Group("oauth.bootsandcats.com")
@Version("v1alpha1")
@Kind("OAuth2DenyRule")
@Plural("oauth2denyrules")
public class OAuth2DenyRule extends CustomResource<OAuth2DenyRuleSpec, OAuth2DenyRuleStatus>
        implements Namespaced {}
