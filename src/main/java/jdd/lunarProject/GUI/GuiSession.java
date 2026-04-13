package jdd.lunarProject.GUI;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class GuiSession implements InventoryHolder {
    private final GuiType guiType;
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> clickActions = new HashMap<>();
    private final Map<String, String> context = new HashMap<>();

    public GuiSession(GuiType guiType, int size, String title) {
        this.guiType = guiType;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    public GuiType getGuiType() {
        return guiType;
    }

    public Map<String, String> getContext() {
        return context;
    }

    public void setContext(String key, String value) {
        context.put(key, value);
    }

    public void setItem(int slot, ItemStack itemStack, Consumer<InventoryClickEvent> clickHandler) {
        inventory.setItem(slot, itemStack);
        if (clickHandler != null) {
            clickActions.put(slot, clickHandler);
        }
    }

    public void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> clickHandler = clickActions.get(event.getRawSlot());
        if (clickHandler != null) {
            clickHandler.accept(event);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
