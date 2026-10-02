package com.annaschneider.minecraft1.video.story;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Client for a local <a href="https://ollama.com">Ollama</a> server ({@code POST /api/generate}). Only loopback
 * addresses are accepted, so prompts never leave the computer.
 */
public final class OllamaClient implements LlmClient {
    public static final String DEFAULT_URL = "http://127.0.0.1:11434";
    public static final String DEFAULT_MODEL = "llama3.2";
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");
    private static final int MAX_RESPONSE_CHARS = 64 * 1024;

    private final URI endpoint;
    private final String model;
    private final HttpClient http;
    private final Duration timeout;

    public OllamaClient(String baseUrl, String model) {
        this(baseUrl, model, Duration.ofSeconds(180));
    }

    public OllamaClient(String baseUrl, String model, Duration timeout) {
        URI base = validateLocalUrl(baseUrl == null || baseUrl.isBlank() ? DEFAULT_URL : baseUrl.strip());
        this.endpoint = base.resolve("/api/generate");
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.strip();
        if (!this.model.matches("[A-Za-z0-9._:/-]{1,100}")) {
            throw new IllegalArgumentException("Invalid Ollama model name: " + model);
        }
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    /** Accepts only http(s) URLs on this computer. */
    public static URI validateLocalUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid local AI address: " + url);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("The local AI address must start with http:// - got " + url);
        }
        if (!LOCAL_HOSTS.contains(host)) {
            throw new IllegalArgumentException("Only a local AI server on this computer is allowed (localhost/127.0.0.1), not " + host);
        }
        return uri;
    }

    @Override
    public String name() {
        return "ollama:" + model;
    }

    @Override
    public String complete(String prompt) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("prompt", prompt);
        body.addProperty("stream", false);
        body.addProperty("format", "json");
        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.7);
        options.addProperty("seed", 42);
        body.add("options", options);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (ConnectException ex) {
            throw new IOException("Ollama is not running at " + endpoint.resolve("/") + " (start Ollama or turn off 'Use local AI')", ex);
        } catch (HttpTimeoutException ex) {
            throw new IOException("Ollama did not answer within " + timeout.toSeconds() + " s", ex);
        }
        String text = response.body() == null ? "" : response.body();
        if (text.length() > MAX_RESPONSE_CHARS) {
            text = text.substring(0, MAX_RESPONSE_CHARS);
        }
        if (response.statusCode() == 404) {
            throw new IOException("Ollama model '" + model + "' is not installed (run: ollama pull " + model + ")");
        }
        if (response.statusCode() != 200) {
            throw new IOException("Ollama answered HTTP " + response.statusCode());
        }
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if (!json.has("response") || !json.get("response").isJsonPrimitive()) {
                throw new IOException("Unexpected answer from Ollama (no 'response' field)");
            }
            return json.get("response").getAsString();
        } catch (JsonParseException | IllegalStateException ex) {
            throw new IOException("Unexpected answer from Ollama: " + ex.getMessage(), ex);
        }
    }
}
