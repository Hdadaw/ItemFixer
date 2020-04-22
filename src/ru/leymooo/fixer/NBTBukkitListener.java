package ru.leymooo.fixer;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class NBTBukkitListener implements Listener {

    private final Main plugin;

    public NBTBukkitListener(Main Main) {
        this.plugin = Main;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent event) {
        if (event.getWhoClicked().getType() != EntityType.PLAYER) return;
        final Player p = (Player) event.getWhoClicked();
        if (event.getCurrentItem() == null) return;
        if (plugin.checkItem(event.getCurrentItem(), p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        final Player p = event.getPlayer();
        if (event.getItemDrop() == null) return;
        if (plugin.checkItem(event.getItemDrop().getItemStack(), p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSlotChange(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        ItemStack stack = p.getInventory().getItem(event.getNewSlot());
        if (plugin.checkItem(stack, p)) {
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
