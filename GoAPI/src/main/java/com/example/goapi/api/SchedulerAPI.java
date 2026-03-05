package com.example.goapi.api;

import com.example.goapi.transport.JsonRpcClient;
import com.example.goapi.transport.JsonRpcServer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

public final class SchedulerAPI {
    private SchedulerAPI() {
    }

    public static void register(JsonRpcServer server, JsonRpcClient client, JavaPlugin plugin) {
        server.register("api.scheduler.runLater", params -> {
            long ticks = longParam(params, "ticks", 0L);
            String callbackId = stringParam(params, "callback_id", "");
            Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> client.callAsync("event.callback." + callbackId, new HashMap<>()),
                ticks
            );
            return ok();
        });

        server.register("api.scheduler.runTimer", params -> {
            long ticks = longParam(params, "ticks", 0L);
            long interval = longParam(params, "interval", 20L);
            String callbackId = stringParam(params, "callback_id", "");
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> client.callAsync("event.callback." + callbackId, new HashMap<>()),
                ticks,
                interval
            );
            Map<String, Object> result = new HashMap<>();
            result.put("task_id", task.getTaskId());
            return result;
        });

        server.register("api.scheduler.cancel", params -> {
            Bukkit.getScheduler().cancelTask(intParam(params, "task_id", -1));
            return ok();
        });
    }

    private static String stringParam(Map<String, Object> params, String key, String fallback) {
        Object value = params.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int intParam(Map<String, Object> params, String key, int fallback) {
        Object value = params.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long longParam(Map<String, Object> params, String key, long fallback) {
        Object value = params.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Map<String, Object> ok() {
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }
}
