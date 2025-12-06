package com.bootsandcats.oauth2.controller;

import java.util.HashMap;
import java.util.Map;

// import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Custom Error Controller for handling application errors.
 *
 * <p>Provides consistent error responses without exposing sensitive information.
 */
@Controller
@Hidden
public class CustomErrorController { // implements ErrorController {

    /**
     * Handle error requests.
     *
     * @param request HTTP request
     * @return Error response
     */
    @RequestMapping(
            value = "/error",
            method = {RequestMethod.GET, RequestMethod.HEAD})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        Map<String, Object> errorResponse = new HashMap<>();
        int statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();

        if (status != null) {
            try {
                statusCode = Integer.parseInt(status.toString());
            } catch (NumberFormatException e) {
                statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
            }
        }

        HttpStatus httpStatus = HttpStatus.valueOf(statusCode);
        errorResponse.put("status", statusCode);
        errorResponse.put("error", httpStatus.getReasonPhrase());

        // Don't expose internal error details
        switch (statusCode) {
            case 400:
                errorResponse.put("message", "Bad request");
                break;
            case 401:
                errorResponse.put("message", "Unauthorized");
                break;
            case 403:
                errorResponse.put("message", "Access denied");
                break;
            case 404:
                errorResponse.put("message", "Resource not found");
                break;
            default:
                errorResponse.put("message", "An error occurred");
        }

        return new ResponseEntity<>(errorResponse, httpStatus);
    }
}
