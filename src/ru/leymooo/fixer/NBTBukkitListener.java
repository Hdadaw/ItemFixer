package ru.leymooo.fixer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
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
        if (plugin.isHackItem(event.getCurrentItem(), (Player) event.getWhoClicked())) {
            event.setCancelled(true);
            event.setCurrentItem(null);
        }
        if (plugin.isHackItem(event.getCursor(), (Player) event.getWhoClicked())) {
            event.setCancelled(true);
            event.getView().setCursor(null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack stack = event.getItemDrop().getItemStack();
        if (plugin.isHackItem(stack, player)) {
            event.setCancelled(true);
            ItemChecker.hardRemoveItem(player, stack);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(PlayerPickupItemEvent event) {
        if (plugin.isHackItem(event.getItem().getItemStack(), event.getPlayer())) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;
        Player player = event.getPlayer();
        if (plugin.isHackItem(item, player)) {
            event.setCancelled(true);
            ItemChecker.hardRemoveItem(player, item);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSlotChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (plugin.isHackItem(item, player)) {
            event.setCancelled(true);
            ItemChecker.hardRemoveItem(player, item);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // сразу инвентарь почему-то не изменяется
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.checkInventory(event.getPlayer().getInventory(), event.getPlayer());
            plugin.checkInventory(event.getPlayer().getEnderChest(), event.getPlayer());
        }, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Location location = event.getInventory().getLocation();
        if (location == null) return;

        BlockState blockState = location.getBlock().getState();
        if (blockState instanceof Container) {
            plugin.checkInventory(((Container) blockState).getInventory(), (Player) event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        NBTPacketListener.cancel.invalidate(event.getPlayer());
    }
}
