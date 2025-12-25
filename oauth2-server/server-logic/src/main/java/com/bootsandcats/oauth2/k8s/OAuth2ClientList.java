package com.bootsandcats.oauth2.k8s;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResourceList;

public class OAuth2ClientList extends CustomResourceList<OAuth2Client> implements Namespaced {}
