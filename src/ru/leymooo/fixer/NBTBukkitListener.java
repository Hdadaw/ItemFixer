package ru.leymooo.fixer;

import com.comphenix.protocol.wrappers.nbt.NbtWrapper;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.leymooo.fixer.utils.MiniNbtFactory;

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
            if (plugin.checkItem(stack, event.getPlayer())) {
                event.getPlayer().getInventory().remove(stack);
            }
        }

        cleanupOverflowInventory(event.getPlayer().getInventory());
        cleanupOverflowInventory(event.getPlayer().getEnderChest());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        NBTPacketListener.cancel.invalidate(event.getPlayer());
    }

    private static void cleanupOverflowInventory(Inventory inventory) {
        int packetSize = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null || stack.getType() == Material.AIR) continue;

            if (isOverflowPacketSize(packetSize)) {
                inventory.remove(stack);
                continue;
            }

            NbtWrapper<?> tag = MiniNbtFactory.fromItemTag(stack);
            if (tag == null) continue;
            packetSize += tag.toString().length();

            if (isOverflowPacketSize(packetSize)) {
                inventory.remove(stack);
            }
        }
    }

    private static boolean isOverflowPacketSize(int size) {
        return size > 2097152;
    }
}
