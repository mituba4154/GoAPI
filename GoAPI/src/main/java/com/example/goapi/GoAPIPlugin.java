package com.example.goapi;

import com.example.goapi.api.CommandRegistry;
import com.example.goapi.api.PlayerAPI;
import com.example.goapi.api.SchedulerAPI;
import com.example.goapi.api.WorldAPI;
import com.example.goapi.bridge.BridgePlugin;
import com.example.goapi.bridge.EventDispatcher;
import com.example.goapi.transport.JsonRpcClient;
import com.example.goapi.transport.JsonRpcServer;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class GoAPIPlugin extends JavaPlugin implements GoAPI {
    private JsonRpcClient client;
    private JsonRpcServer server;
    private boolean paperEnv;
    private EventDispatcher dispatcher;

    @Override
    public void onLoad() {
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            paperEnv = true;
        } catch (ClassNotFoundException e) {
            paperEnv = false;
        }
        getLogger().info("Running on " + (paperEnv ? "Paper" : "Spigot/Bukkit"));
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        client = new JsonRpcClient(getConfig().getString("go-server-url", "http://localhost:8765"));
        server = new JsonRpcServer(getConfig().getInt("java-rpc-port", 8766));
        dispatcher = new EventDispatcher(client);

        PlayerAPI.register(server, this);
        WorldAPI.register(server, this);
        SchedulerAPI.register(server, client, this);
        CommandRegistry.register(server, client, this);

        server.start();
        getServer().getPluginManager().registerEvents(new BridgePlugin(this), this);
        getServer().getServicesManager().register(GoAPI.class, this, this, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
        if (server != null) {
            server.stop();
        }
        getServer().getServicesManager().unregisterAll(this);
    }

    @Override
    public Map<String, Object> call(String method, Map<String, Object> params) {
        return client.call(method, params);
    }

    @Override
    public void callAsync(String method, Map<String, Object> params) {
        client.callAsync(method, params);
    }

    @Override
    public void onReverseCall(String method, JsonRpcServer.RpcHandler handler) {
        server.register(method, handler);
    }

    @Override
    public boolean isAlive() {
        if (client == null) {
            return false;
        }
        try {
            java.net.http.HttpClient hc = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(500))
                .build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(getConfig().getString("go-server-url", "http://localhost:8765") + "/health"))
                .timeout(java.time.Duration.ofMillis(500))
                .GET()
                .build();
            int status = hc.send(req, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
            return status == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isPaper() {
        return paperEnv;
    }

    @Override
    public EventDispatcher getDispatcher() {
        return dispatcher;
    }
}
