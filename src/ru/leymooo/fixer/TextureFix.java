package ru.leymooo.fixer;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.EnumSet;

public class TextureFix implements Listener {

    private final Object2IntOpenHashMap<Material> limit = new Object2IntOpenHashMap<>();
    private final EnumSet<Material> ignore = EnumSet.noneOf(Material.class);

    public TextureFix(String version) {
        //Вроде все предменты что имеют SubId
        //Material, MaxSubId
        limit.put(Material.STONE, 6);
        limit.put(Material.DIRT, 2);
        limit.put(Material.WOOD, 5);
        limit.put(Material.SAPLING, 5);
        limit.put(Material.SAND, 1);
        limit.put(Material.LOG, 3);
        limit.put(Material.LEAVES, 3);
        limit.put(Material.SPONGE, 1);
        limit.put(Material.SANDSTONE, 2);
        limit.put(Material.LONG_GRASS, 2);
        limit.put(Material.WOOL, 15);
        limit.put(Material.RED_ROSE, 8);
        limit.put(Material.DOUBLE_STEP, 7);
        limit.put(Material.STEP, 7);
        limit.put(Material.MONSTER_EGGS, 5);
        limit.put(Material.SMOOTH_BRICK, 3);
        limit.put(Material.WOOD_DOUBLE_STEP, 5);
        limit.put(Material.WOOD_STEP, 5);
        limit.put(Material.COBBLE_WALL, 1);
        limit.put(Material.QUARTZ_BLOCK, 2);
        limit.put(Material.STAINED_CLAY, 15);
        limit.put(Material.STAINED_GLASS, 15);
        limit.put(Material.STAINED_GLASS_PANE, 15);
        limit.put(Material.LEAVES_2, 1);
        limit.put(Material.LOG_2, 1);
        limit.put(Material.PRISMARINE, 2);
        limit.put(Material.CARPET, 15);
        limit.put(Material.DOUBLE_PLANT, 5);
        limit.put(Material.RED_SANDSTONE, 2);
        limit.put(Material.COAL, 1);
        limit.put(Material.RAW_FISH, 3);
        limit.put(Material.COOKED_FISH, 1);
        limit.put(Material.INK_SACK, 15);
        limit.put(Material.SKULL_ITEM, 5);
        limit.put(Material.GOLDEN_APPLE, 1);
        limit.put(Material.BANNER, 15);
        limit.put(Material.ANVIL, 2);
        if (version.startsWith("v1_12_R")) {
            limit.put(Material.CONCRETE, 15);
            limit.put(Material.CONCRETE_POWDER, 15);
            limit.put(Material.BED, 15);
        }
        //Предметы с прочностью.
        ignore.addAll(Arrays.asList(Material.MAP, Material.EMPTY_MAP, Material.CARROT_STICK, Material.BOW, Material.FISHING_ROD, Material.FLINT_AND_STEEL, Material.SHEARS));
        if (version.startsWith("v1_8_R")) {
            ignore.add(Material.MONSTER_EGG);
            ignore.add(Material.POTION);
            limit.put(Material.SKULL_ITEM, 4);
        }
        if (Material.matchMaterial("SHIELD") != null) {
            ignore.add(Material.SHIELD);
            ignore.add(Material.ELYTRA);
        }

        //Деревянные инструменты
        ignore.addAll(Arrays.asList(Material.WOOD_SPADE, Material.WOOD_PICKAXE, Material.WOOD_AXE, Material.WOOD_SWORD, Material.WOOD_HOE));
        //Золотые инструменты
        ignore.addAll(Arrays.asList(Material.GOLD_SPADE, Material.GOLD_PICKAXE, Material.GOLD_AXE, Material.GOLD_SWORD, Material.GOLD_HOE));
        //Каменные инструменты
        ignore.addAll(Arrays.asList(Material.STONE_SPADE, Material.STONE_PICKAXE, Material.STONE_AXE, Material.STONE_SWORD, Material.STONE_HOE));
        //Железные инструменты
        ignore.addAll(Arrays.asList(Material.IRON_SPADE, Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_SWORD, Material.IRON_HOE));
        //Алмазные инструменты
        ignore.addAll(Arrays.asList(Material.DIAMOND_SPADE, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE, Material.DIAMOND_SWORD, Material.DIAMOND_HOE));
        //Разная броня
        ignore.addAll(Arrays.asList(Material.LEATHER_BOOTS, Material.LEATHER_CHESTPLATE, Material.LEATHER_HELMET, Material.LEATHER_LEGGINGS));
        ignore.addAll(Arrays.asList(Material.IRON_BOOTS, Material.IRON_CHESTPLATE, Material.IRON_HELMET, Material.IRON_LEGGINGS));
        ignore.addAll(Arrays.asList(Material.GOLD_BOOTS, Material.GOLD_CHESTPLATE, Material.GOLD_HELMET, Material.GOLD_LEGGINGS));
        ignore.addAll(Arrays.asList(Material.DIAMOND_BOOTS, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, Material.DIAMOND_LEGGINGS));
        ignore.addAll(Arrays.asList(Material.CHAINMAIL_BOOTS, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_HELMET, Material.CHAINMAIL_LEGGINGS));
        limit.defaultReturnValue(-1);
        limit.trim();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHold(PlayerItemHeldEvent e) {
        ItemStack it = e.getPlayer().getInventory().getItem(e.getNewSlot());
        if (isInvalidTexture(it)) {
            e.setCancelled(true);
            ItemChecker.hardRemoveItem(e.getPlayer(), it);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (isInvalidTexture(e.getItem())) {
            e.setCancelled(true);
            ItemChecker.hardRemoveItem(e.getPlayer(), e.getItem());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        ItemStack item = e.getCurrentItem();
        if (e.getWhoClicked().getType() == EntityType.PLAYER && isInvalidTexture(item)) {
            e.setCancelled(true);
            ItemChecker.hardRemoveItem((Player) e.getWhoClicked(), item);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent e) {
        if (isInvalidTexture(e.getEntity().getItemStack())) {
            e.setCancelled(true);
        }
    }

    public boolean isInvalidTexture(ItemStack it) {
        if (it == null) return false;

        Material type = it.getType();
        short durability = it.getDurability();

        // костыль на исключение 44:2
        if (type == Material.STEP && durability == 2) {
            return true;
        }

        if (type != Material.AIR && durability != 0) {
            int limit = this.limit.getInt(type);
            if (limit != -1) {
                return (durability < 0 || (durability > limit));
            }
            return !ignore.contains(type);
        }
        return false;
    }
}
