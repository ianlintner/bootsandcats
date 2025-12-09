"""
Data models for the OAuth2 client.

This module contains Pydantic models for API requests and responses.
After running client generation, this will be replaced with actual models.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any, Dict, List, Optional, Union

from pydantic import BaseModel, Field


class TokenResponse(BaseModel):
    """OAuth2 Token Response."""
    
    access_token: str
    token_type: str
    expires_in: Optional[int] = None
    refresh_token: Optional[str] = None
    scope: Optional[str] = None
    id_token: Optional[str] = None


class TokenRequest(BaseModel):
    """OAuth2 Token Request."""
    
    grant_type: str
    code: Optional[str] = None
    redirect_uri: Optional[str] = None
    client_id: Optional[str] = None
    client_secret: Optional[str] = None
    refresh_token: Optional[str] = None
    scope: Optional[str] = None
    code_verifier: Optional[str] = None


class OpenIDConfiguration(BaseModel):
    """OpenID Connect Discovery Document."""
    
    issuer: str
    authorization_endpoint: str
    token_endpoint: str
    userinfo_endpoint: Optional[str] = None
    jwks_uri: str
    registration_endpoint: Optional[str] = None
    scopes_supported: Optional[List[str]] = None
    response_types_supported: List[str]
    response_modes_supported: Optional[List[str]] = None
    grant_types_supported: Optional[List[str]] = None
    subject_types_supported: List[str]
    id_token_signing_alg_values_supported: List[str]
    token_endpoint_auth_methods_supported: Optional[List[str]] = None
    claims_supported: Optional[List[str]] = None
    code_challenge_methods_supported: Optional[List[str]] = None


class UserInfo(BaseModel):
    """User Info Response."""
    
    sub: str
    name: Optional[str] = None
    given_name: Optional[str] = None
    family_name: Optional[str] = None
    preferred_username: Optional[str] = None
    email: Optional[str] = None
    email_verified: Optional[bool] = None
    picture: Optional[str] = None
    locale: Optional[str] = None
    updated_at: Optional[int] = None


class OAuth2Error(BaseModel):
    """OAuth2 Error Response."""
    
    error: str
    error_description: Optional[str] = None
    error_uri: Optional[str] = None
    state: Optional[str] = None


class ClientRegistrationRequest(BaseModel):
    """Client Registration Request."""
    
    redirect_uris: List[str]
    client_name: Optional[str] = None
    client_uri: Optional[str] = None
    logo_uri: Optional[str] = None
    contacts: Optional[List[str]] = None
    tos_uri: Optional[str] = None
    policy_uri: Optional[str] = None
    jwks_uri: Optional[str] = None
    jwks: Optional[Dict[str, Any]] = None
    software_id: Optional[str] = None
    software_version: Optional[str] = None
    grant_types: Optional[List[str]] = None
    response_types: Optional[List[str]] = None
    token_endpoint_auth_method: Optional[str] = None
    scope: Optional[str] = None


class ClientRegistrationResponse(BaseModel):
    """Client Registration Response."""
    
    client_id: str
    client_secret: Optional[str] = None
    client_id_issued_at: Optional[int] = None
    client_secret_expires_at: Optional[int] = None
    redirect_uris: List[str]
    client_name: Optional[str] = None
    client_uri: Optional[str] = None
    logo_uri: Optional[str] = None
    contacts: Optional[List[str]] = None
    tos_uri: Optional[str] = None
    policy_uri: Optional[str] = None
    jwks_uri: Optional[str] = None
    jwks: Optional[Dict[str, Any]] = None
    software_id: Optional[str] = None
    software_version: Optional[str] = None
    grant_types: Optional[List[str]] = None
    response_types: Optional[List[str]] = None
    token_endpoint_auth_method: Optional[str] = None
    scope: Optional[str] = None


class JWK(BaseModel):
    """JSON Web Key."""
    
    kty: str
    use: Optional[str] = None
    key_ops: Optional[List[str]] = None
    alg: Optional[str] = None
    kid: Optional[str] = None
    x5u: Optional[str] = None
    x5c: Optional[List[str]] = None
    x5t: Optional[str] = None
    x5t_s256: Optional[str] = Field(None, alias="x5t#S256")
    # RSA specific
    n: Optional[str] = None
    e: Optional[str] = None
    # EC specific
    crv: Optional[str] = None
    x: Optional[str] = None
    y: Optional[str] = None


class JWKS(BaseModel):
    """JSON Web Key Set."""
    
    keys: List[JWK]


class IntrospectionRequest(BaseModel):
    """Introspection Request."""
    
    token: str
    token_type_hint: Optional[str] = None


class IntrospectionResponse(BaseModel):
    """Introspection Response."""
    
    active: bool
    scope: Optional[str] = None
    client_id: Optional[str] = None
    username: Optional[str] = None
    token_type: Optional[str] = None
    exp: Optional[int] = None
    iat: Optional[int] = None
    nbf: Optional[int] = None
    sub: Optional[str] = None
    aud: Optional[Union[str, List[str]]] = None
    iss: Optional[str] = None
    jti: Optional[str] = None


class RevocationRequest(BaseModel):
    """Revocation Request."""
    
    token: str
    token_type_hint: Optional[str] = None


__all__ = [
    "TokenResponse",
    "TokenRequest",
    "OpenIDConfiguration",
    "UserInfo",
    "OAuth2Error",
    "ClientRegistrationRequest",
    "ClientRegistrationResponse",
    "JWK",
    "JWKS",
    "IntrospectionRequest",
    "IntrospectionResponse",
    "RevocationRequest",
]
