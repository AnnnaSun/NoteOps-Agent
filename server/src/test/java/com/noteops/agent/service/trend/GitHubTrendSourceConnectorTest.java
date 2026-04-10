package com.noteops.agent.service.trend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.trend.TrendSourceType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubTrendSourceConnectorTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchesAndMapsGitHubCandidates() {
        respond("/search/repositories", 200, """
            {
              "items": [
                {
                  "id": 42,
                  "full_name": "openai/codex",
                  "html_url": "https://github.com/openai/codex",
                  "description": "Agentic coding",
                  "language": "Java",
                  "stargazers_count": 2500,
                  "forks_count": 120,
                  "pushed_at": "2026-04-10T10:00:00Z"
                }
              ]
            }
            """);
        server.start();

        GitHubTrendSourceConnector connector = new GitHubTrendSourceConnector(
            HttpClient.newHttpClient(),
            new ObjectMapper(),
            baseUrl
        );

        List<FetchedTrendCandidate> candidates = connector.fetchCandidates(
            new TrendSourceConnector.FetchCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                List.of("agent", "coding")
            )
        );

        assertThat(candidates).hasSize(1);
        FetchedTrendCandidate candidate = candidates.getFirst();
        assertThat(candidate.sourceType()).isEqualTo(TrendSourceType.GITHUB);
        assertThat(candidate.sourceItemKey()).isEqualTo("42");
        assertThat(candidate.title()).isEqualTo("openai/codex");
        assertThat(candidate.url()).isEqualTo("https://github.com/openai/codex");
        assertThat(candidate.sourcePublishedAt()).isEqualTo(Instant.parse("2026-04-10T10:00:00Z"));
        assertThat(candidate.extraAttributes()).containsEntry("language", "Java");
        assertThat(candidate.normalizedScore()).isGreaterThan(0.0);
    }

    @Test
    void rejectsInvalidItemsResponse() {
        respond("/search/repositories", 200, "{\"unexpected\":true}");
        server.start();

        GitHubTrendSourceConnector connector = new GitHubTrendSourceConnector(
            HttpClient.newHttpClient(),
            new ObjectMapper(),
            baseUrl
        );

        assertThatThrownBy(() -> connector.fetchCandidates(
            new TrendSourceConnector.FetchCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                List.of("agent")
            )
        ))
            .isInstanceOf(ApiException.class)
            .hasMessage("GitHub repositories response must contain items array");
    }

    private void respond(String path, int statusCode, String body) {
        server.createContext(path, exchange -> {
            byte[] payload = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, payload.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(payload);
            }
        });
    }
}
