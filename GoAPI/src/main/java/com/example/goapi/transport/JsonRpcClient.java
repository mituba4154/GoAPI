package com.example.goapi.transport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class JsonRpcClient {
    private final HttpClient httpClient;
    private final String url;

    public JsonRpcClient(String url) {
        this.httpClient = HttpClient.newHttpClient();
        if (url.endsWith("/rpc")) {
            this.url = url;
        } else {
            this.url = url + "/rpc";
        }
    }

    public Map<String, Object> call(String method, Map<String, Object> params) {
        try {
            JsonRpcMessage payload = JsonRpcMessage.request(method, params);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toJson(), StandardCharsets.UTF_8))
                .build();

            CompletableFuture<HttpResponse<String>> future =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            HttpResponse<String> response = future.get(500, TimeUnit.MILLISECONDS);
            JsonRpcMessage rpcResponse = JsonRpcMessage.fromJson(response.body());
            return toMap(rpcResponse == null ? null : rpcResponse.getResult());
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("ok", false);
            error.put("error", e.getMessage());
            return error;
        }
    }

    public void callAsync(String method, Map<String, Object> params) {
        try {
            JsonRpcMessage payload = JsonRpcMessage.request(method, params);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toJson(), StandardCharsets.UTF_8))
                .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // fire-and-forget
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object result) {
        if (result instanceof Map<?, ?>) {
            return (Map<String, Object>) result;
        }
        return new HashMap<>();
    }
}
