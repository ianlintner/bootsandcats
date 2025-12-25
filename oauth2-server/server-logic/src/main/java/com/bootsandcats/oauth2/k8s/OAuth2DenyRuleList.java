package com.bootsandcats.oauth2.k8s;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResourceList;

public class OAuth2DenyRuleList extends CustomResourceList<OAuth2DenyRule> implements Namespaced {}
