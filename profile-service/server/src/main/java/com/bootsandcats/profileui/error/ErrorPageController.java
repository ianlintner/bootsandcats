package com.bootsandcats.profileui.error;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.exceptions.HttpStatusException;

/**
 * Serves a branded HTML error experience for browser users while preserving JSON responses for API
 * clients. Envoy OAuth2 filter handles authentication.
 */
@Controller
public class ErrorPageController {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /** Handle 404 Not Found globally. */
    @Error(status = HttpStatus.NOT_FOUND, global = true)
    @Produces(MediaType.TEXT_HTML)
    public HttpResponse<String> notFound(HttpRequest<?> request) {
        return renderResponse(HttpStatus.NOT_FOUND, request.getPath());
    }

    /** Handle 500 Internal Server Error globally. */
    @Error(status = HttpStatus.INTERNAL_SERVER_ERROR, global = true)
    @Produces(MediaType.TEXT_HTML)
    public HttpResponse<String> internalServerError(HttpRequest<?> request) {
        return renderResponse(HttpStatus.INTERNAL_SERVER_ERROR, request.getPath());
    }

    /** Handle 400 Bad Request globally. */
    @Error(status = HttpStatus.BAD_REQUEST, global = true)
    @Produces(MediaType.TEXT_HTML)
    public HttpResponse<String> badRequest(HttpRequest<?> request) {
        return renderResponse(HttpStatus.BAD_REQUEST, request.getPath());
    }

    /** Handle 401 Unauthorized globally. */
    @Error(status = HttpStatus.UNAUTHORIZED, global = true)
    @Produces(MediaType.TEXT_HTML)
    public HttpResponse<String> unauthorized(HttpRequest<?> request) {
        return renderResponse(HttpStatus.UNAUTHORIZED, request.getPath());
    }

    /** Handle 403 Forbidden globally. */
    @Error(status = HttpStatus.FORBIDDEN, global = true)
    @Produces(MediaType.TEXT_HTML)
    public HttpResponse<String> forbidden(HttpRequest<?> request) {
        return renderResponse(HttpStatus.FORBIDDEN, request.getPath());
    }

    /** Catch-all exception handler. */
    @Error(global = true)
    @Produces(MediaType.TEXT_HTML)
    public HttpResponse<String> onException(HttpRequest<?> request, Throwable throwable) {
        System.out.println("ErrorPageController.onException called for " + request.getPath());
        if (throwable != null) {
            throwable.printStackTrace();
        } else {
            System.out.println("Throwable is null");
        }
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (throwable instanceof HttpStatusException statusException) {
            status = statusException.getStatus();
        } else if (throwable instanceof AuthorizationException) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (throwable == null) {
            // If throwable is null, it's likely a 404 (no route found)
            status = HttpStatus.NOT_FOUND;
        }
        System.out.println("Returning status: " + status);
        return renderResponse(status, request.getPath());
    }

    private HttpResponse<String> renderResponse(HttpStatus status, String path) {
        String body = renderHtml(status, path);
        return HttpResponse.status(status).contentType(MediaType.TEXT_HTML_TYPE).body(body);
    }

    private String renderHtml(HttpStatus status, String path) {
        String statusMessage =
                switch (status) {
                    case NOT_FOUND -> "The page you were looking for doesn’t exist.";
                    case UNAUTHORIZED, FORBIDDEN -> "You may need to sign in or request access.";
                    case BAD_REQUEST -> "The request could not be processed.";
                    default -> "Something went wrong on our side.";
                };

        String timestamp = FORMATTER.format(OffsetDateTime.now());

        return """
                <!DOCTYPE html>
                <html lang=\"en\">
                <head>
                    <meta charset=\"UTF-8\" />
                    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
                    <title>Something went wrong | Boots & Cats</title>
                    <style>
                        :root {
                            color-scheme: light dark;
                            --bg: #0b1224;
                            --card: #0f172a;
                            --text: #e5e7eb;
                            --muted: #94a3b8;
                            --accent: #06b6d4;
                            --accent-2: #8b5cf6;
                            --danger: #f97316;
                            --shadow: 0 18px 48px rgba(0,0,0,0.35);
                        }
                        * { box-sizing: border-box; }
                        body {
                            margin: 0;
                            min-height: 100vh;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            font-family: \"Inter\", system-ui, -apple-system, \"Segoe UI\", sans-serif;
                            background: radial-gradient(circle at 15%% 20%%, rgba(8,47,73,0.28), transparent 30%%),
                                        radial-gradient(circle at 85%% 10%%, rgba(139,92,246,0.18), transparent 26%%),
                                        var(--bg);
                            color: var(--text);
                            padding: 24px;
                        }
                        .card {
                            width: min(880px, 100%%);
                            background: linear-gradient(135deg, rgba(15,23,42,0.92), rgba(12,19,35,0.94));
                            border: 1px solid rgba(255,255,255,0.06);
                            border-radius: 18px;
                            padding: 30px;
                            box-shadow: var(--shadow);
                            position: relative;
                            overflow: hidden;
                        }
                        .card::after {
                            content: \"\";
                            position: absolute;
                            inset: 0;
                            background: radial-gradient(circle at 10%% 10%%, rgba(6,182,212,0.08), transparent 38%%),
                                        radial-gradient(circle at 90%% 10%%, rgba(139,92,246,0.1), transparent 32%%);
                            pointer-events: none;
                        }
                        .eyebrow {
                            display: inline-flex;
                            align-items: center;
                            gap: 8px;
                            padding: 6px 12px;
                            border-radius: 999px;
                            background: rgba(6,182,212,0.16);
                            color: #67e8f9;
                            font-weight: 700;
                            letter-spacing: 0.02em;
                            font-size: 12px;
                        }
                        h1 {
                            margin: 14px 0 6px;
                            font-size: clamp(28px, 4vw, 36px);
                            letter-spacing: -0.02em;
                        }
                        .status {
                            display: inline-flex;
                            align-items: center;
                            gap: 8px;
                            padding: 6px 10px;
                            border-radius: 10px;
                            background: rgba(249,115,22,0.12);
                            color: var(--danger);
                            font-weight: 700;
                            letter-spacing: 0.02em;
                        }
                        .muted { color: var(--muted); font-size: 15px; margin: 0 0 12px; }
                        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px; margin-top: 18px; }
                        .panel {
                            padding: 12px 14px;
                            border-radius: 12px;
                            background: rgba(255,255,255,0.04);
                            border: 1px solid rgba(255,255,255,0.06);
                        }
                        .panel strong { display: block; margin-bottom: 6px; color: #c7d2fe; font-size: 13px; letter-spacing: 0.01em; }
                        .actions { margin-top: 24px; display: flex; flex-wrap: wrap; gap: 12px; }
                        .btn {
                            display: inline-flex;
                            align-items: center;
                            gap: 10px;
                            padding: 12px 16px;
                            border-radius: 12px;
                            text-decoration: none;
                            font-weight: 700;
                            transition: transform 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
                        }
                        .btn.primary {
                            background: linear-gradient(120deg, var(--accent), var(--accent-2));
                            color: #0b1021;
                            box-shadow: 0 10px 30px rgba(6,182,212,0.35);
                        }
                        .btn.secondary {
                            border: 1px solid rgba(255,255,255,0.15);
                            color: var(--text);
                            background: rgba(255,255,255,0.05);
                        }
                        .btn:hover { transform: translateY(-1px); box-shadow: 0 14px 32px rgba(0,0,0,0.22); }
                    </style>
                </head>
                <body>
                    <div class=\"card\">
                        <div class=\"eyebrow\">Boots & Cats • Profile Service</div>
                        <div class=\"status\">%s • %s</div>
                        <h1>Something went wrong</h1>
                        <p class=\"muted\">%s</p>
                        <div class=\"grid\">
                            <div class=\"panel\">
                                <strong>Path</strong>
                                <div>%s</div>
                            </div>
                            <div class=\"panel\">
                                <strong>Timestamp</strong>
                                <div>%s</div>
                            </div>
                        </div>
                        <div class=\"actions\">
                            <a class=\"btn primary\" href=\"/\">Go home</a>
                            <a class=\"btn secondary\" href=\"/swagger-ui\">API docs</a>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(
                        status.getCode(),
                        status.getReason(),
                        statusMessage,
                        escapeFormatSpecifiers(path),
                        timestamp);
    }

    /**
     * Escapes percent signs in strings to prevent IllegalFormatFlagsException when used with
     * String.formatted(). Replaces % with %% so they are treated as literal percent signs.
     */
    private String escapeFormatSpecifiers(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("%", "%%");
    }
}
