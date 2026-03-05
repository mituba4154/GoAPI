package com.example.goapi.bridge;

import com.example.goapi.transport.JsonRpcClient;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.lang.reflect.Method;
import java.util.Map;

public class EventDispatcher {
    private final JsonRpcClient client;

    public EventDispatcher(JsonRpcClient client) {
        this.client = client;
    }

    public void dispatch(String eventKey, Map<String, Object> data, Event event) {
        populateCommonFields(data, event);
        client.callAsync(eventKey, data);
    }

    private void populateCommonFields(Map<String, Object> data, Event event) {
        try {
            Object playerObj = invoke(event, "getPlayer");
            if (playerObj instanceof Player player) {
                data.put("player_id", player.getUniqueId().toString());
            }
        } catch (Exception ignored) {
        }

        try {
            Object entityObj = invoke(event, "getEntity");
            if (entityObj instanceof Entity entity) {
                data.put("entity_id", entity.getUniqueId().toString());
            }
        } catch (Exception ignored) {
        }

        try {
            Object cancelled = invoke(event, "isCancelled");
            if (cancelled instanceof Boolean) {
                data.put("cancelled", cancelled);
            }
        } catch (Exception ignored) {
        }

        try {
            Object blockObj = invoke(event, "getBlock");
            if (blockObj instanceof Block block) {
                data.put("block_x", block.getX());
                data.put("block_y", block.getY());
                data.put("block_z", block.getZ());
                data.put("block_material", block.getType().name());
            }
        } catch (Exception ignored) {
        }
    }

    private Object invoke(Event event, String methodName) throws Exception {
        Method method = event.getClass().getMethod(methodName);
        return method.invoke(event);
    }
}
