package com.example.goapi;

import com.example.goapi.bridge.EventDispatcher;
import com.example.goapi.transport.JsonRpcServer;

import java.util.Map;

public interface GoAPI {
    Map<String, Object> call(String method, Map<String, Object> params);

    void callAsync(String method, Map<String, Object> params);

    void onReverseCall(String method, JsonRpcServer.RpcHandler handler);

    boolean isAlive();

    boolean isPaper();

    EventDispatcher getDispatcher();
}
