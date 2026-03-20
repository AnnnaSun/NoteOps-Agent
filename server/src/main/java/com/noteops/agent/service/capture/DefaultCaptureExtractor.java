package com.noteops.agent.service.capture;

import com.noteops.agent.config.CaptureUrlProperties;

import com.noteops.agent.model.capture.CaptureFailureReason;
import com.noteops.agent.model.capture.CaptureInputType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DefaultCaptureExtractor implements CaptureExtractor {

    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STYLE_PATTERN = Pattern.compile("<style[^>]*>.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final HttpClient httpClient;
    private final CaptureUrlProperties properties;

    @Autowired
    public DefaultCaptureExtractor(CaptureUrlProperties properties) {
        this(
            HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(properties.connectTimeout())
                .build(),
            properties
        );
    }

    DefaultCaptureExtractor(HttpClient httpClient, CaptureUrlProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    @Override
    public ExtractionResult extract(ExtractionCommand command) {
        if (command.sourceType() == CaptureInputType.TEXT) {
            return extractText(command);
        }
        return extractUrl(command);
    }

    private ExtractionResult extractText(ExtractionCommand command) {
        String rawText = command.rawText();
        String cleanText = normalizeText(command.rawText());
        return new ExtractionResult(rawText, cleanText, nullIfBlank(command.sourceUrl()), "INLINE_TEXT", nullIfBlank(command.titleHint()));
    }

    private ExtractionResult extractUrl(ExtractionCommand command) {
        String sourceUrl = nullIfBlank(command.sourceUrl());
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(sourceUrl))
                .GET()
                .timeout(properties.readTimeout())
                .header("Accept", "text/html, text/plain;q=0.9")
                .header("User-Agent", properties.userAgent())
                .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CapturePipelineException(
                    CaptureFailureReason.EXTRACTION_FAILED,
                    "url extraction failed with status " + response.statusCode()
                );
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("text/html");
            if (!isTextualContent(contentType)) {
                throw new CapturePipelineException(
                    CaptureFailureReason.EXTRACTION_FAILED,
                    "url extraction requires a text response"
                );
            }

            byte[] responseBytes;
            try (InputStream body = response.body()) {
                responseBytes = readLimitedBytes(body, properties.maxResponseBytes());
            }
            Charset charset = detectCharset(contentType);
            String responseText = new String(responseBytes, charset);
            String pageTitle = extractHtmlTitle(responseText);
            String cleanText = normalizeUrlText(responseText, contentType);
            if (cleanText == null || cleanText.isBlank()) {
                throw new CapturePipelineException(
                    CaptureFailureReason.EXTRACTION_FAILED,
                    "url extraction produced an empty text snapshot"
                );
            }

            String rawText = pageTitle == null ? cleanText : pageTitle + "\n\n" + cleanText;
            return new ExtractionResult(rawText, cleanText, sourceUrl, "HTTP_URL_SNAPSHOT", pageTitle);
        } catch (CapturePipelineException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CapturePipelineException(
                CaptureFailureReason.EXTRACTION_FAILED,
                "url extraction failed: " + exception.getMessage(),
                exception
            );
        }
    }

    private byte[] readLimitedBytes(InputStream inputStream, int maxBytes) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8_192];
        int totalBytes = 0;
        int bytesRead;
        while ((bytesRead = inputStream.read(chunk)) != -1) {
            int allowed = Math.min(bytesRead, maxBytes - totalBytes);
            if (allowed > 0) {
                buffer.write(chunk, 0, allowed);
                totalBytes += allowed;
            }
            if (totalBytes >= maxBytes) {
                break;
            }
        }
        return buffer.toByteArray();
    }

    private Charset detectCharset(String contentType) {
        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        if (!matcher.find()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(matcher.group(1).trim().replace("\"", ""));
        } catch (Exception exception) {
            return StandardCharsets.UTF_8;
        }
    }

    private boolean isTextualContent(String contentType) {
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("text/")
            || normalized.contains("json")
            || normalized.contains("xml");
    }

    private String normalizeUrlText(String responseText, String contentType) {
        String normalizedType = contentType.toLowerCase(Locale.ROOT);
        if (normalizedType.startsWith("text/plain")) {
            return limitLength(normalizeText(responseText));
        }

        String withoutScripts = SCRIPT_PATTERN.matcher(responseText).replaceAll(" ");
        String withoutStyles = STYLE_PATTERN.matcher(withoutScripts).replaceAll(" ");
        String withoutTags = TAG_PATTERN.matcher(withoutStyles).replaceAll(" ");
        String cleanText = decodeBasicEntities(withoutTags);
        return limitLength(normalizeText(cleanText));
    }

    private String extractHtmlTitle(String responseText) {
        Matcher matcher = TITLE_PATTERN.matcher(responseText);
        if (!matcher.find()) {
            return null;
        }
        return nullIfBlank(normalizeText(decodeBasicEntities(matcher.group(1))));
    }

    private String decodeBasicEntities(String value) {
        return value
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'");
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return WHITESPACE_PATTERN.matcher(value.trim()).replaceAll(" ");
    }

    private String limitLength(String value) {
        if (value == null || value.length() <= properties.maxTextLength()) {
            return value;
        }
        return value.substring(0, properties.maxTextLength());
    }

    private String nullIfBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
