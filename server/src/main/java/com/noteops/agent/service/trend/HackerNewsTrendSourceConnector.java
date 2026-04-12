package com.noteops.agent.service.trend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.trend.TrendSourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class HackerNewsTrendSourceConnector implements TrendSourceConnector {

    private static final String DEFAULT_BASE_URL = "https://hacker-news.firebaseio.com/v0";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    @Autowired
    public HackerNewsTrendSourceConnector(ObjectMapper objectMapper) {
        this(
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(),
            objectMapper,
            DEFAULT_BASE_URL
        );
    }

    HackerNewsTrendSourceConnector(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    @Override
    public TrendSourceType sourceType() {
        return TrendSourceType.HN;
    }

    @Override
    public String displayName() {
        return "Hacker News";
    }

    @Override
    public List<FetchedTrendCandidate> fetchCandidates(FetchCommand command) {
        try {
            JsonNode topStoriesNode = getJson("/topstories.json");
            if (!topStoriesNode.isArray()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "TREND_SOURCE_RESPONSE_INVALID", "Hacker News topstories response must be an array");
            }

            int fetchLimit = Math.max(1, command.fetchLimit());
            int candidateBudget = Math.min(topStoriesNode.size(), fetchLimit * 4);
            List<FetchedTrendCandidate> matching = new ArrayList<>();
            List<FetchedTrendCandidate> fallback = new ArrayList<>();
            List<String> keywordBias = command.keywordBias() == null ? List.of() : command.keywordBias();

            for (int index = 0; index < candidateBudget; index++) {
                JsonNode idNode = topStoriesNode.get(index);
                if (idNode == null || !idNode.canConvertToLong()) {
                    continue;
                }
                long itemId = idNode.asLong();
                JsonNode itemNode = getJson("/item/" + itemId + ".json");
                if (itemNode == null || itemNode.isNull()) {
                    continue;
                }
                if (!"story".equals(itemNode.path("type").asText(""))) {
                    continue;
                }

                String title = blankToNull(itemNode.path("title").asText(null));
                if (title == null) {
                    continue;
                }

                FetchedTrendCandidate candidate = new FetchedTrendCandidate(
                    TrendSourceType.HN,
                    Long.toString(itemId),
                    title,
                    buildUrl(itemNode, itemId),
                    Math.min(100.0d, itemNode.path("score").asDouble(0.0d)),
                    itemNode.hasNonNull("time") ? Instant.ofEpochSecond(itemNode.path("time").asLong()) : null,
                    buildExtraAttributes(itemNode, itemId)
                );

                if (matchesKeywordBias(title, keywordBias)) {
                    matching.add(candidate);
                } else {
                    fallback.add(candidate);
                }
            }

            List<FetchedTrendCandidate> ordered = new ArrayList<>();
            ordered.addAll(matching);
            if (ordered.size() < fetchLimit) {
                ordered.addAll(fallback);
            }
            return ordered.stream().limit(fetchLimit).toList();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "TREND_SOURCE_FETCH_FAILED",
                "Hacker News fetch failed: " + exception.getMessage()
            );
        }
    }

    private JsonNode getJson(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .GET()
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
            .header("User-Agent", "NoteOps-Agent/Trend")
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "TREND_SOURCE_FETCH_FAILED",
                "Hacker News fetch failed with status " + response.statusCode()
            );
        }
        return objectMapper.readTree(response.body());
    }

    private String buildUrl(JsonNode itemNode, long itemId) {
        String directUrl = blankToNull(itemNode.path("url").asText(null));
        if (directUrl != null) {
            return directUrl;
        }
        return "https://news.ycombinator.com/item?id=" + itemId;
    }

    private Map<String, Object> buildExtraAttributes(JsonNode itemNode, long itemId) {
        Map<String, Object> extraAttributes = new LinkedHashMap<>();
        extraAttributes.put("hn_item_id", itemId);
        putIfPresent(extraAttributes, "author", blankToNull(itemNode.path("by").asText(null)));
        if (itemNode.has("score")) {
            extraAttributes.put("score", itemNode.path("score").asInt());
        }
        if (itemNode.has("descendants")) {
            extraAttributes.put("comment_count", itemNode.path("descendants").asInt());
        }
        return Map.copyOf(extraAttributes);
    }

    private boolean matchesKeywordBias(String title, List<String> keywordBias) {
        if (keywordBias == null || keywordBias.isEmpty()) {
            return true;
        }
        String normalizedTitle = title.toLowerCase(Locale.ROOT);
        return keywordBias.stream()
            .filter(keyword -> keyword != null && !keyword.isBlank())
            .map(keyword -> keyword.toLowerCase(Locale.ROOT).trim())
            .anyMatch(normalizedTitle::contains);
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
