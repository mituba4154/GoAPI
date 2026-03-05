package com.example.goapi.transport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JsonRpcServer {
    public interface RpcHandler {
        Map<String, Object> handle(Map<String, Object> params);
    }

    private final int port;
    private final Map<String, RpcHandler> handlers = new ConcurrentHashMap<>();
    private HttpServer server;

    public JsonRpcServer(int port) {
        this.port = port;
    }

    public void start() {
        if (server != null) {
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/rpc", this::handleRpc);
            server.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start JsonRpcServer", e);
        }
    }

    public void register(String method, RpcHandler handler) {
        handlers.put(method, handler);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void handleRpc(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        JsonRpcMessage request;
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            request = JsonRpcMessage.fromJson(body);
        } catch (Exception e) {
            writeResponse(exchange, JsonRpcMessage.error(null, -32700, "Parse error"));
            return;
        }

        JsonRpcMessage response;
        RpcHandler handler = handlers.get(request.getMethod());
        if (handler == null) {
            response = JsonRpcMessage.error(request.getId(), -32601, "Method not found");
        } else {
            try {
                Map<String, Object> params = request.getParams() == null ? new HashMap<>() : request.getParams();
                Map<String, Object> result = handler.handle(params);
                response = JsonRpcMessage.response(request.getId(), result);
            } catch (Exception e) {
                response = JsonRpcMessage.error(request.getId(), -32603, e.getMessage());
            }
        }

        writeResponse(exchange, response);
    }

    private void writeResponse(HttpExchange exchange, JsonRpcMessage response) throws IOException {
        byte[] bytes = response.toJson().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
