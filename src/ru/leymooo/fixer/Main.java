package ru.leymooo.fixer;

import com.comphenix.protocol.ProtocolLibrary;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Main extends JavaPlugin {

    private ItemChecker checker;
    private boolean version1_8;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        checkNewConfig();
        PluginManager pmanager = Bukkit.getPluginManager();
        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        version1_8 = version.startsWith("v1_8_R");
        checker = new ItemChecker(this);
        ProtocolLibrary.getProtocolManager().addPacketListener(new NBTPacketListener(this));
        pmanager.registerEvents(new NBTBukkitListener(this), this);
        pmanager.registerEvents(new TextureFix(version), this);
        Bukkit.getConsoleSender().sendMessage("§b[ItemFixer] §aenabled");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        ProtocolLibrary.getProtocolManager().removePacketListeners(this);
        NBTPacketListener.cancel.invalidateAll();
        NBTPacketListener.cancel = null;
        checker = null;
    }

    public boolean isHackItem(ItemStack stack, Player p) {
        if (hasFullBypass(p)) return false;
        return checker.isHacked(checker.checkItem(stack, p));
    }

    public void checkInventory(Inventory inventory, Player p) {
        if (hasFullBypass(p)) return;
        checker.checkInventory(inventory, p);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void checkNewConfig() {
        if (!getConfig().isSet("ignored-tags")) {
            File config = new File(getDataFolder(), "config.yml");
            config.delete();
            saveDefaultConfig();
        }
        if (getConfig().isSet("max-pps")) {
            getConfig().set("max-pps", null);
            getConfig().set("max-pps-kick-msg", null);
            saveConfig();
        }
    }

    public boolean isVersion1_8() {
        return version1_8;
    }

    public boolean hasFullBypass(Player p) {
        return p.hasPermission("itemfixer.bypass.any");
    }
}
