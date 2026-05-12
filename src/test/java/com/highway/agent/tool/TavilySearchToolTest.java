package com.highway.agent.tool;

import com.highway.agent.model.SearchResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TavilySearchToolTest {

    private MockWebServer mockWebServer;
    private TavilySearchTool searchTool;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        String baseUrl = mockWebServer.url("/").toString();

        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();

        searchTool = new TavilySearchTool(webClient, new com.fasterxml.jackson.databind.ObjectMapper(),
                "test-key", "basic", 5);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void search_shouldReturnResults() {
        String mockResponse = """
                {
                  "query": "quantum computing",
                  "results": [
                    {
                      "title": "Quantum Computing Report",
                      "url": "https://example.com/quantum",
                      "content": "Quantum computing uses quantum mechanics.",
                      "score": 0.95
                    },
                    {
                      "title": "QC Overview",
                      "url": "https://example.org/overview",
                      "content": "Recent breakthroughs in quantum computing.",
                      "score": 0.88
                    }
                  ]
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setHeader("Content-Type", "application/json"));

        List<SearchResult> results = searchTool.search("quantum computing").block();

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("Quantum Computing Report", results.get(0).getTitle());
        assertEquals("https://example.com/quantum", results.get(0).getUrl());
    }

    @Test
    void search_emptyResults_shouldReturnEmptyList() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"query\":\"test\",\"results\":[]}")
                .setHeader("Content-Type", "application/json"));

        List<SearchResult> results = searchTool.search("nothing found").block();

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void search_serverError_shouldReturnEmptyList() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        List<SearchResult> results = searchTool.search("error case").block();

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}
