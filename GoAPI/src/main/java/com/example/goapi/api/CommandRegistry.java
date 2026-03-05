package com.example.goapi.api;

import com.example.goapi.transport.JsonRpcClient;
import com.example.goapi.transport.JsonRpcServer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class CommandRegistry {
    private static final Map<String, PluginCommand> REGISTERED = new HashMap<>();

    private CommandRegistry() {
    }

    public static void register(JsonRpcServer server, JsonRpcClient client, JavaPlugin plugin) {
        server.register("api.command.register", params -> runOnMain(plugin, () -> {
            String name = stringParam(params, "name", null);
            if (name == null || name.isBlank()) {
                return error("name required");
            }

            String description = stringParam(params, "description", "");
            String usage = stringParam(params, "usage", "/" + name);

            try {
                SimpleCommandMap commandMap = getCommandMap();
                unregisterCommand(name, commandMap, plugin);

                PluginCommand command = createPluginCommand(name, plugin);
                command.setDescription(description);
                command.setUsage(usage);
                command.setExecutor((sender, cmd, label, args) -> {
                    Map<String, Object> payload = new HashMap<>();
                    if (sender instanceof Player player) {
                        payload.put("sender_id", player.getUniqueId().toString());
                    } else {
                        payload.put("sender_id", sender.getName());
                    }
                    payload.put("args", Arrays.asList(args));
                    client.callAsync("event.command." + name, payload);
                    return true;
                });
                commandMap.register(plugin.getName(), command);
                REGISTERED.put(name.toLowerCase(Locale.ROOT), command);
                return ok();
            } catch (Exception e) {
                return error(e.getMessage());
            }
        }));

        server.register("api.command.unregister", params -> runOnMain(plugin, () -> {
            String name = stringParam(params, "name", null);
            if (name == null || name.isBlank()) {
                return error("name required");
            }

            try {
                unregisterCommand(name, getCommandMap(), plugin);
                return ok();
            } catch (Exception e) {
                return error(e.getMessage());
            }
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

    private static PluginCommand createPluginCommand(String name, Plugin plugin) throws Exception {
        Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
        constructor.setAccessible(true);
        return constructor.newInstance(name, plugin);
    }

    private static SimpleCommandMap getCommandMap() throws Exception {
        Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
        field.setAccessible(true);
        return (SimpleCommandMap) field.get(Bukkit.getServer());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Command> getKnownCommands(SimpleCommandMap commandMap) throws Exception {
        Field field = SimpleCommandMap.class.getDeclaredField("knownCommands");
        field.setAccessible(true);
        return (Map<String, Command>) field.get(commandMap);
    }

    private static void unregisterCommand(String name, SimpleCommandMap commandMap, JavaPlugin plugin) throws Exception {
        String key = name.toLowerCase(Locale.ROOT);
        Map<String, Command> knownCommands = getKnownCommands(commandMap);

        Command removed = knownCommands.remove(key);
        Command namespaced = knownCommands.remove(plugin.getName().toLowerCase(Locale.ROOT) + ":" + key);
        Command registered = REGISTERED.remove(key);

        Command target = registered != null ? registered : (removed != null ? removed : namespaced);
        if (target != null) {
            target.unregister(commandMap);
        }
    }

    private static String stringParam(Map<String, Object> params, String key, String fallback) {
        Object value = params.get(key);
        return value == null ? fallback : String.valueOf(value);
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
