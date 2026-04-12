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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HackerNewsTrendSourceConnectorTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v0";
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchesAndMapsHackerNewsCandidates() {
        respond("/v0/topstories.json", 200, "[101,202]");
        respond("/v0/item/101.json", 200, """
            {
              "id": 101,
              "type": "story",
              "title": "Agent memory patterns",
              "url": "https://example.com/agent-memory",
              "score": 88,
              "time": 1712743200,
              "by": "alice",
              "descendants": 12
            }
            """);
        respond("/v0/item/202.json", 200, """
            {
              "id": 202,
              "type": "story",
              "title": "Other unrelated story",
              "score": 10,
              "time": 1712743201,
              "by": "bob"
            }
            """);
        server.start();

        HackerNewsTrendSourceConnector connector = new HackerNewsTrendSourceConnector(
            HttpClient.newHttpClient(),
            new ObjectMapper(),
            baseUrl
        );

        List<FetchedTrendCandidate> candidates = connector.fetchCandidates(
            new TrendSourceConnector.FetchCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                List.of("agent")
            )
        );

        assertThat(candidates).hasSize(1);
        FetchedTrendCandidate candidate = candidates.getFirst();
        assertThat(candidate.sourceType()).isEqualTo(TrendSourceType.HN);
        assertThat(candidate.sourceItemKey()).isEqualTo("101");
        assertThat(candidate.title()).isEqualTo("Agent memory patterns");
        assertThat(candidate.url()).isEqualTo("https://example.com/agent-memory");
        assertThat(candidate.normalizedScore()).isEqualTo(88.0);
        assertThat(candidate.sourcePublishedAt()).isEqualTo(Instant.ofEpochSecond(1712743200));
        assertThat(candidate.extraAttributes()).containsEntry("author", "alice");
    }

    @Test
    void rejectsInvalidTopStoriesResponse() {
        respond("/v0/topstories.json", 200, "{\"unexpected\":true}");
        server.start();

        HackerNewsTrendSourceConnector connector = new HackerNewsTrendSourceConnector(
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
            .hasMessage("Hacker News topstories response must be an array");
    }

    private void respond(String path, int statusCode, String body) {
        server.createContext(path, exchange -> {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, payload.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(payload);
            }
        });
    }
}
