package com.noteops.agent.service.trend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.trend.TrendSourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GitHubTrendSourceConnector implements TrendSourceConnector {

    private static final String DEFAULT_BASE_URL = "https://api.github.com";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    @Autowired
    public GitHubTrendSourceConnector(ObjectMapper objectMapper) {
        this(
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(),
            objectMapper,
            DEFAULT_BASE_URL
        );
    }

    GitHubTrendSourceConnector(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    @Override
    public TrendSourceType sourceType() {
        return TrendSourceType.GITHUB;
    }

    @Override
    public String displayName() {
        return "GitHub";
    }

    @Override
    public List<FetchedTrendCandidate> fetchCandidates(FetchCommand command) {
        try {
            String query = buildQuery(command.keywordBias());
            String path = "/search/repositories?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&sort=updated&order=desc&per_page=" + Math.max(1, command.fetchLimit());
            JsonNode responseNode = getJson(path);
            JsonNode itemsNode = responseNode.path("items");
            if (!itemsNode.isArray()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "TREND_SOURCE_RESPONSE_INVALID", "GitHub repositories response must contain items array");
            }

            List<FetchedTrendCandidate> candidates = new ArrayList<>();
            for (JsonNode itemNode : itemsNode) {
                String repositoryId = itemNode.path("id").asText(null);
                String repositoryName = itemNode.path("full_name").asText(null);
                String url = itemNode.path("html_url").asText(null);
                if (repositoryId == null || repositoryName == null || url == null) {
                    continue;
                }
                candidates.add(new FetchedTrendCandidate(
                    TrendSourceType.GITHUB,
                    repositoryId,
                    repositoryName,
                    url,
                    Math.min(100.0d, Math.log10(itemNode.path("stargazers_count").asDouble(0.0d) + 1.0d) * 25.0d),
                    itemNode.hasNonNull("pushed_at") ? Instant.parse(itemNode.path("pushed_at").asText()) : null,
                    buildExtraAttributes(itemNode)
                ));
            }
            return candidates;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "TREND_SOURCE_FETCH_FAILED",
                "GitHub fetch failed: " + exception.getMessage()
            );
        }
    }

    private JsonNode getJson(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .GET()
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "NoteOps-Agent/Trend")
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "TREND_SOURCE_FETCH_FAILED",
                "GitHub fetch failed with status " + response.statusCode()
            );
        }
        return objectMapper.readTree(response.body());
    }

    private String buildQuery(List<String> keywordBias) {
        List<String> effectiveKeywords = keywordBias == null || keywordBias.isEmpty()
            ? List.of("agent", "llm", "memory", "retrieval", "tooling", "coding")
            : keywordBias.stream().filter(keyword -> keyword != null && !keyword.isBlank()).map(String::trim).toList();

        if (effectiveKeywords.isEmpty()) {
            return "agent OR llm";
        }
        return String.join(" OR ", effectiveKeywords) + " archived:false";
    }

    private Map<String, Object> buildExtraAttributes(JsonNode itemNode) {
        Map<String, Object> extraAttributes = new LinkedHashMap<>();
        putIfPresent(extraAttributes, "full_name", blankToNull(itemNode.path("full_name").asText(null)));
        putIfPresent(extraAttributes, "description", blankToNull(itemNode.path("description").asText(null)));
        putIfPresent(extraAttributes, "language", blankToNull(itemNode.path("language").asText(null)));
        if (itemNode.has("stargazers_count")) {
            extraAttributes.put("stargazers_count", itemNode.path("stargazers_count").asInt());
        }
        if (itemNode.has("forks_count")) {
            extraAttributes.put("forks_count", itemNode.path("forks_count").asInt());
        }
        return Map.copyOf(extraAttributes);
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
