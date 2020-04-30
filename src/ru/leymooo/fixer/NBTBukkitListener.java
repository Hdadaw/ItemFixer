package ru.leymooo.fixer;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

public class NBTBukkitListener implements Listener {

    private final Main plugin;

    public NBTBukkitListener(Main Main) {
        this.plugin = Main;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent event) {
        if (event.getWhoClicked().getType() != EntityType.PLAYER) return;
        if (event.getCurrentItem() == null) return;
        if (plugin.checkItem(event.getCurrentItem(), (Player) event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (event.getItemDrop() == null) return;
        if (plugin.checkItem(event.getItemDrop().getItemStack(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(PlayerPickupItemEvent event) {
        if (event.getItem() == null) return;
        if (plugin.checkItem(event.getItem().getItemStack(), event.getPlayer())) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() == null) return;
        if (plugin.checkItem(event.getItem(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSlotChange(PlayerItemHeldEvent event) {
        if (plugin.checkItem(event.getPlayer().getInventory().getItem(event.getNewSlot()), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (ItemStack stack : event.getPlayer().getInventory().getContents()) {
            plugin.checkItem(stack, event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        NBTPacketListener.cancel.invalidate(event.getPlayer());
    }
}
