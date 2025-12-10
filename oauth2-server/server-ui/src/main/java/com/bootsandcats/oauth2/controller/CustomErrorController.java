package com.bootsandcats.oauth2.controller;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import com.bootsandcats.oauth2.dto.ErrorResponse;

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
            method = {RequestMethod.GET, RequestMethod.HEAD},
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        ErrorView errorView = buildErrorView(request);
        ErrorResponse response =
                new ErrorResponse(
                        errorView.statusCode(),
                        errorView.reasonPhrase(),
                        errorView.message(),
                        null);
        return new ResponseEntity<>(response, errorView.httpStatus());
    }

    @RequestMapping(
            value = "/error",
            method = {RequestMethod.GET, RequestMethod.HEAD},
            produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView handleErrorHtml(HttpServletRequest request) {
        ErrorView errorView = buildErrorView(request);

        Map<String, Object> model =
                Map.of(
                        "statusCode",
                        errorView.statusCode(),
                        "statusMessage",
                        errorView.message(),
                        "reasonPhrase",
                        errorView.reasonPhrase(),
                        "path",
                        errorView.path(),
                        "timestamp",
                        OffsetDateTime.now());

        ModelAndView mav = new ModelAndView("error", model, errorView.httpStatus());
        mav.setStatus(errorView.httpStatus());
        return mav;
    }

    private ErrorView buildErrorView(HttpServletRequest request) {
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
                    default -> "An unexpected error occurred";
                };

        String path =
                request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String requestUri
                        ? requestUri
                        : request.getRequestURI();

        return new ErrorView(statusCode, httpStatus, httpStatus.getReasonPhrase(), message, path);
    }

    private record ErrorView(
            int statusCode,
            HttpStatus httpStatus,
            String reasonPhrase,
            String message,
            String path) {}
    }
}
