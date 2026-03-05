package com.example.goapi.api;

import com.example.goapi.transport.JsonRpcServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class WorldAPI {
    private WorldAPI() {
    }

    public static void register(JsonRpcServer server, JavaPlugin plugin) {
        server.register("api.world.getBlock", params -> runOnMain(plugin, () -> {
            World world = getWorld(params);
            if (world == null) {
                return error("world not found");
            }
            Location location = getLocation(world, params);
            Map<String, Object> result = new HashMap<>();
            result.put("material", world.getBlockAt(location).getType().name());
            return result;
        }));

        server.register("api.world.setBlock", params -> runOnMain(plugin, () -> {
            World world = getWorld(params);
            if (world == null) {
                return error("world not found");
            }
            Material material = Material.matchMaterial(stringParam(params, "material", ""));
            if (material == null) {
                return error("material not found");
            }
            world.getBlockAt(getLocation(world, params)).setType(material);
            return ok();
        }));

        server.register("api.world.getSpawn", params -> runOnMain(plugin, () -> {
            World world = getWorld(params);
            if (world == null) {
                return error("world not found");
            }
            Location spawn = world.getSpawnLocation();
            Map<String, Object> result = new HashMap<>();
            result.put("x", spawn.getX());
            result.put("y", spawn.getY());
            result.put("z", spawn.getZ());
            return result;
        }));

        server.register("api.world.setSpawn", params -> runOnMain(plugin, () -> {
            World world = getWorld(params);
            if (world == null) {
                return error("world not found");
            }
            world.setSpawnLocation(intParam(params, "x", 0), intParam(params, "y", 64), intParam(params, "z", 0));
            return ok();
        }));

        server.register("api.world.getTime", params -> runOnMain(plugin, () -> {
            World world = getWorld(params);
            if (world == null) {
                return error("world not found");
            }
            Map<String, Object> result = new HashMap<>();
            result.put("result", world.getTime());
            return result;
        }));

        server.register("api.world.setTime", params -> runOnMain(plugin, () -> {
            World world = getWorld(params);
            if (world == null) {
                return error("world not found");
            }
            world.setTime(longParam(params, "time", 0L));
            return ok();
        }));

        server.register("api.world.spawnEntity", params -> runOnMain(plugin, () -> {
            World world = getWorld(params);
            if (world == null) {
                return error("world not found");
            }
            String rawType = stringParam(params, "type", "PIG").toUpperCase(Locale.ROOT);
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(rawType);
            } catch (IllegalArgumentException e) {
                return error("entity type not found");
            }
            Entity entity = world.spawnEntity(getLocation(world, params), entityType);
            Map<String, Object> result = new HashMap<>();
            result.put("entity_id", entity.getUniqueId().toString());
            return result;
        }));
    }

    private static Map<String, Object> runOnMain(JavaPlugin plugin, Supplier<Map<String, Object>> action) {
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    future.complete(action.get());
                } catch (Exception e) {
                    future.complete(error(e.getMessage()));
                }
            });
            return future.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(e.getMessage());
        } catch (java.util.concurrent.TimeoutException e) {
            return error("main thread timeout");
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private static World getWorld(Map<String, Object> params) {
        String worldName = stringParam(params, "world", null);
        if (worldName == null) {
            return null;
        }
        return Bukkit.getWorld(worldName);
    }

    private static Location getLocation(World world, Map<String, Object> params) {
        double x = doubleParam(params, "x", 0.0D);
        double y = doubleParam(params, "y", 0.0D);
        double z = doubleParam(params, "z", 0.0D);
        return new Location(world, x, y, z);
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

    private static double doubleParam(Map<String, Object> params, String key, double fallback) {
        Object value = params.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Map<String, Object> ok() {
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("ok", false);
        result.put("error", message == null ? "unknown error" : message);
        return result;
    }
}
