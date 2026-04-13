package jdd.lunarProject.GUI;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class GuiClickListener implements Listener {
    private final GuiManager guiManager;

    public GuiClickListener(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (guiManager.handleInventoryClick(event)) {
            return;
        }

        if (guiManager.isProtectedMenuInteraction(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (guiManager.handleInventoryDrag(event)) {
            event.setCancelled(true);
        }
    }
}
