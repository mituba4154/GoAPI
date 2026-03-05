package com.example.goapi.bridge;

import com.example.goapi.GoAPIPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class BridgePlugin implements Listener {
    private final GoAPIPlugin api;

    public BridgePlugin(GoAPIPlugin api) {
        this.api = api;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("_api", "bukkit");
        api.getDispatcher().dispatch("event.PlayerJoinEvent", data, event);
    }
}
