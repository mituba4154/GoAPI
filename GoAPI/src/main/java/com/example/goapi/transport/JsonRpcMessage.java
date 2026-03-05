package com.example.goapi.transport;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JsonRpcMessage {
    private static final Gson GSON = new Gson();

    private String jsonrpc = "2.0";
    private String method;
    private Map<String, Object> params;
    private String id;
    private Object result;
    private Map<String, Object> error;

    public static JsonRpcMessage request(String method, Map<String, Object> params) {
        JsonRpcMessage message = new JsonRpcMessage();
        message.setMethod(method);
        message.setParams(params == null ? new HashMap<>() : params);
        message.setId(UUID.randomUUID().toString());
        return message;
    }

    public static JsonRpcMessage response(String id, Object result) {
        JsonRpcMessage message = new JsonRpcMessage();
        message.setId(id);
        message.setResult(result);
        return message;
    }

    public static JsonRpcMessage error(String id, int code, String message) {
        JsonRpcMessage response = new JsonRpcMessage();
        response.setId(id);
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        response.setError(error);
        return response;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static JsonRpcMessage fromJson(String json) {
        return GSON.fromJson(json, JsonRpcMessage.class);
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public Map<String, Object> getError() {
        return error;
    }

    public void setError(Map<String, Object> error) {
        this.error = error;
    }
}
