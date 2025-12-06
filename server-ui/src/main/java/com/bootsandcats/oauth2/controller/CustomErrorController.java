package com.bootsandcats.oauth2.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import com.bootsandcats.oauth2.dto.ErrorResponse;

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
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        int statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();

        if (status != null) {
            try {
                statusCode = Integer.parseInt(status.toString());
            } catch (NumberFormatException e) {
                statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
            }
        }

        HttpStatus httpStatus = HttpStatus.valueOf(statusCode);
        String message =
                switch (statusCode) {
                    case 400 -> "Bad request";
                    case 401 -> "Unauthorized";
                    case 403 -> "Access denied";
                    case 404 -> "Resource not found";
                    default -> "An error occurred";
                };

        ErrorResponse response =
                new ErrorResponse(statusCode, httpStatus.getReasonPhrase(), message, null);
        return new ResponseEntity<>(response, httpStatus);
    }
}
