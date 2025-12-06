package com.bootsandcats.oauth2.dto;

import java.util.Map;

/** Simple error response DTO used by the error controller. */
public record ErrorResponse(int status, String error, String message, Map<String, Object> details) {}
