package com.example.goapi.api;

import com.example.goapi.transport.JsonRpcServer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class PlayerAPI {
    private PlayerAPI() {
    }

    public static void register(JsonRpcServer server, JavaPlugin plugin) {
        server.register("api.player.getName", params -> runOnMain(plugin, () -> {
            Player player = getPlayer(params);
            if (player == null) {
                return offline();
            }
            Map<String, Object> result = new HashMap<>();
            result.put("result", player.getName());
            return result;
        }));

        server.register("api.player.sendMessage", params -> runOnMain(plugin, () -> {
            Player player = getPlayer(params);
            if (player == null) {
                return offline();
            }
            player.sendMessage(stringParam(params, "message", ""));
            return ok();
        }));

        server.register("api.player.setHealth", params -> runOnMain(plugin, () -> {
            Player player = getPlayer(params);
            if (player == null) {
                return offline();
            }
            player.setHealth(doubleParam(params, "value", player.getHealth()));
            return ok();
        }));

        server.register("api.player.getHealth", params -> runOnMain(plugin, () -> {
            Player player = getPlayer(params);
            if (player == null) {
                return offline();
            }
            Map<String, Object> result = new HashMap<>();
            result.put("result", player.getHealth());
            return result;
        }));

        server.register("api.player.teleport", params -> runOnMain(plugin, () -> {
            Player player = getPlayer(params);
            if (player == null) {
                return offline();
            }
            String worldName = stringParam(params, "world", null);
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                return error("world not found");
            }
            double x = doubleParam(params, "x", player.getLocation().getX());
            double y = doubleParam(params, "y", player.getLocation().getY());
            double z = doubleParam(params, "z", player.getLocation().getZ());
            player.teleport(new Location(world, x, y, z));
            return ok();
        }));

        server.register("api.player.getLocation", params -> runOnMain(plugin, () -> {
            Player player = getPlayer(params);
            if (player == null) {
                return offline();
            }
            Location location = player.getLocation();
            Map<String, Object> result = new HashMap<>();
            result.put("world", location.getWorld() == null ? null : location.getWorld().getName());
            result.put("x", location.getX());
            result.put("y", location.getY());
            result.put("z", location.getZ());
            return result;
        }));

        server.register("api.player.kick", params -> runOnMain(plugin, () -> {
            Player player = getPlayer(params);
            if (player == null) {
                return offline();
            }
            player.kickPlayer(stringParam(params, "reason", ""));
            return ok();
        }));

        server.register("api.player.giveItem", params -> runOnMain(plugin, () -> {
            Player player = getPlayer(params);
            if (player == null) {
                return offline();
            }
            Material material = Material.matchMaterial(stringParam(params, "material", ""));
            if (material == null) {
                return error("material not found");
            }
            int amount = intParam(params, "amount", 1);
            if (amount < 1) {
                amount = 1;
            }
            player.getInventory().addItem(new ItemStack(material, amount));
            return ok();
        }));

        server.register("api.player.setGameMode", params -> runOnMain(plugin, () -> {
            Player player = getPlayer(params);
            if (player == null) {
                return offline();
            }
            try {
                GameMode mode = GameMode.valueOf(stringParam(params, "mode", "SURVIVAL").toUpperCase(Locale.ROOT));
                player.setGameMode(mode);
                return ok();
            } catch (IllegalArgumentException e) {
                return error("invalid game mode");
            }
        }));

        server.register("api.player.getGameMode", params -> runOnMain(plugin, () -> {
            Player player = getPlayer(params);
            if (player == null) {
                return offline();
            }
            Map<String, Object> result = new HashMap<>();
            result.put("result", player.getGameMode().name());
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
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(e.getMessage());
        } catch (ExecutionException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private static Player getPlayer(Map<String, Object> params) {
        try {
            String raw = stringParam(params, "player_id", null);
            if (raw == null) {
                return null;
            }
            return Bukkit.getPlayer(UUID.fromString(raw));
        } catch (Exception e) {
            return null;
        }
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

    private static Map<String, Object> offline() {
        Map<String, Object> result = new HashMap<>();
        result.put("ok", false);
        result.put("error", "player offline");
        return result;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("ok", false);
        result.put("error", message == null ? "unknown error" : message);
        return result;
    }
}
