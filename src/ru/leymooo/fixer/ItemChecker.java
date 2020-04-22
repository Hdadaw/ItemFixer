package ru.leymooo.fixer;

import com.comphenix.protocol.wrappers.nbt.NbtBase;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;
import com.comphenix.protocol.wrappers.nbt.NbtList;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.codec.binary.Base64;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.banner.Pattern;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.potion.PotionEffect;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ItemChecker {

    private static final Object OBJECT = new Object();
    private final Gson gson = new Gson();
    private final Boolean removeInvalidEnch;
    private final Boolean checkench;
    private final HashSet<String> nbt;
    private final HashSet<String> world;
    private final EnumSet<Material> tiles;
    private final HashSet<String> ignoreNbt;
    private final Cache<Integer, Object> checked;
    private final Main plugin;

    public ItemChecker(Main main) {
        this.plugin = main;
        ignoreNbt = new HashSet<>(plugin.getConfig().getStringList("ignored-tags"));
        nbt = new HashSet<>(Arrays.asList("ActiveEffects", "Command", "CustomName", "AttributeModifiers", "Unbreakable"));
        nbt.removeAll(ignoreNbt);
        tiles = EnumSet.copyOf(Arrays.asList(
                Material.FURNACE, Material.CHEST, Material.TRAPPED_CHEST, Material.DROPPER, Material.DISPENSER, Material.COMMAND_MINECART, Material.HOPPER_MINECART,
                Material.HOPPER, Material.BREWING_STAND_ITEM, Material.BEACON, Material.SIGN, Material.MOB_SPAWNER, Material.NOTE_BLOCK, Material.COMMAND, Material.JUKEBOX));
        world = new HashSet<>(plugin.getConfig().getStringList("ignore-worlds"));
        checkench = plugin.getConfig().getBoolean("check-enchants");
        removeInvalidEnch = plugin.getConfig().getBoolean("remove-invalid-enchants");
        checked = CacheBuilder.newBuilder()
                .concurrencyLevel(2)
                .initialCapacity(50)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build();
    }

    @SuppressWarnings("rawtypes")
    public boolean isCrashSkull(NbtCompound tag) {
        if (!tag.containsKey("SkullOwner")) return false;
        NbtCompound skullOwner = tag.getCompound("SkullOwner");
        if (!skullOwner.containsKey("Properties")) return false;
        NbtCompound properties = skullOwner.getCompound("Properties");
        if (properties.containsKey("textures")) {
            NbtList<NbtBase> textures = properties.getList("textures");
            for (NbtBase texture : textures.asCollection()) {
                if (texture instanceof NbtCompound) {
                    if (((NbtCompound) texture).containsKey("Value")) {
                        if (((NbtCompound) texture).getString("Value").trim().length() > 0) {
                            String decoded;
                            try {
                                decoded = new String(Base64.decodeBase64(((NbtCompound) texture).getString("Value")));
                            } catch (Exception e) {
                                tag.remove("SkullOwner");
                                return true;
                            }
                            if (decoded.isEmpty()) {
                                tag.remove("SkullOwner");
                                return true;
                            }
                            if (decoded.contains("textures")) {
                                try {
                                    JsonObject jdecoded = gson.fromJson(decoded, JsonObject.class);
                                    if (!jdecoded.has("textures")) return false;
                                    JsonObject jtextures = jdecoded.getAsJsonObject("textures");
                                    if (!jtextures.has("SKIN")) return true;
                                    JsonObject jskin = jtextures.getAsJsonObject("SKIN");
                                    if (!jskin.has("url")) return true;
                                    String url = jskin.getAsJsonPrimitive("url").getAsString();

                                    if (url.isEmpty() || url.trim().length() == 0) return true;
                                    if (url.startsWith("http://textures.minecraft.net/texture/") || url.startsWith("https://textures.minecraft.net/texture/"))
                                        return false;
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        tag.remove("SkullOwner");
        return true;
    }

    private boolean checkEnchants(ItemStack stack, Player p) {
        boolean cheat = false;
        if (checkench && !p.hasPermission("itemfixer.bypass.enchant") && stack.hasItemMeta() && stack.getItemMeta().hasEnchants()) {
            final ItemMeta meta = stack.getItemMeta();
            Map<Enchantment, Integer> enchantments;
            try {
                enchantments = meta.getEnchants();
            } catch (Exception e) {
                clearData(stack);
                return true;
            }
            for (Map.Entry<Enchantment, Integer> ench : enchantments.entrySet()) {
                Enchantment enchant = ench.getKey();
                String perm = "itemfixer.allow." + stack.getType().toString() + "." + enchant.getName() + "." + ench.getValue();
                if (removeInvalidEnch && !enchant.canEnchantItem(stack) && !p.hasPermission(perm)) {
                    meta.removeEnchant(enchant);
                    cheat = true;
                }
                if ((ench.getValue() > enchant.getMaxLevel() || ench.getValue() < 0) && !p.hasPermission(perm)) {
                    meta.removeEnchant(enchant);
                    cheat = true;
                }
            }
            if (cheat) stack.setItemMeta(meta);
        }
        return cheat;
    }

    private boolean checkNbt(ItemStack stack, Player p) {
        boolean cheat = false;
        try {
            if (p.hasPermission("itemfixer.bypass.nbt")) return false;
            Material mat = stack.getType();
            NbtCompound tag = (NbtCompound) NbtFactory.fromItemTag(stack);
            if (tag == null) return false;
            if (this.isCrashItem(stack, tag, mat)) {
                tag.getKeys().clear();
                stack.setAmount(1);
                return true;
            }
            final String tagS = tag.toString();
            for (String nbt1 : nbt) {
                if (tag.containsKey(nbt1)) {
                    tag.remove(nbt1);
                    cheat = true;
                }
            }
            if (tag.containsKey("BlockEntityTag") && !isShulkerBox(stack, stack) && !needIgnore(stack) && !ignoreNbt.contains("BlockEntityTag")) {
                tag.remove("BlockEntityTag");
                cheat = true;
            } else if (mat == Material.WRITTEN_BOOK && ((!ignoreNbt.contains("ClickEvent") && tagS.contains("ClickEvent"))
                    || (!ignoreNbt.contains("run_command") && tagS.contains("run_command")))) {
                tag.getKeys().clear();
                cheat = true;
            } else if (mat == Material.MONSTER_EGG && !ignoreNbt.contains("EntityTag") && tag.containsKey("EntityTag") && fixEgg(tag)) {
                cheat = true;
            } else if (mat == Material.ARMOR_STAND && !ignoreNbt.contains("EntityTag") && tag.containsKey("EntityTag")) {
                tag.remove("EntityTag");
                cheat = true;
            } else if ((mat == Material.SKULL || mat == Material.SKULL_ITEM) && stack.getDurability() == 3) {
                if (isCrashSkull(tag)) {
                    cheat = true;
                }
            } else if (mat == Material.FIREWORK && !ignoreNbt.contains("Explosions") && checkFireWork(stack)) {
                cheat = true;
            } else if (mat == Material.BANNER && checkBanner(stack)) {
                cheat = true;
            } else if (isPotion(stack) && !ignoreNbt.contains("CustomPotionEffects") && tag.containsKey("CustomPotionEffects")
                    && (checkPotion(stack, p) || checkCustomColor(tag.getCompound("CustomPotionEffects")))) {
                cheat = true;
            }
        } catch (Exception ignored) {
        }
        return cheat;
    }

    private boolean needIgnore(ItemStack stack) {
        Material m = stack.getType();
        return (m == Material.BANNER || (!plugin.isVersion1_8() && (m == Material.SHIELD)));
    }

    private void checkShulkerBox(ItemStack stack, Player p) {
        if (!isShulkerBox(stack, stack)) return;
        BlockStateMeta meta = (BlockStateMeta) stack.getItemMeta();
        ShulkerBox box = (ShulkerBox) meta.getBlockState();
        for (ItemStack is : box.getInventory().getContents()) {
            if (isShulkerBox(is, stack) || isHackedItem(is, p)) {
                box.getInventory().clear();
                meta.setBlockState(box);
                stack.setItemMeta(meta);
                return;
            }
        }
    }

    private boolean isPotion(ItemStack stack) {
        try {
            return stack.hasItemMeta() && stack.getItemMeta() instanceof PotionMeta;
        } catch (IllegalArgumentException e) {
            clearData(stack);
            return false;
        }
    }

    private boolean checkCustomColor(NbtCompound tag) {
        if (tag.containsKey("CustomPotionColor")) {
            int color = tag.getInteger("CustomPotionColor");
            try {
                Color.fromBGR(color);
            } catch (IllegalArgumentException e) {
                tag.remove("CustomPotionColor");
                return true;
            }
        }
        return false;
    }

    private boolean checkPotion(ItemStack stack, Player p) {
        boolean cheat = false;
        if (!p.hasPermission("itemfixer.bypass.potion")) {
            PotionMeta meta = (PotionMeta) stack.getItemMeta();
            for (PotionEffect ef : meta.getCustomEffects()) {
                String perm = "itemfixer.allow." + ef.getType().toString() + "." + (ef.getAmplifier() + 1);
                if (!p.hasPermission(perm)) {
                    meta.removeCustomEffect(ef.getType());
                    cheat = true;
                }
            }
            if (cheat) {
                stack.setItemMeta(meta);
            }
        }
        return cheat;
    }

    private boolean isShulkerBox(ItemStack stack, ItemStack rootStack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        if (plugin.isVersion1_8()) return false;
        if (!stack.hasItemMeta()) return false;
        try {
            if (!(stack.getItemMeta() instanceof BlockStateMeta)) return false;
        } catch (IllegalArgumentException e) {
            clearData(rootStack); //Уууух. Костылики
            return false;
        }
        BlockStateMeta meta = (BlockStateMeta) stack.getItemMeta();
        return meta.getBlockState() instanceof ShulkerBox;
    }

    public boolean isHackedItem(ItemStack stack, Player p) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        if (this.world.contains(p.getWorld().getName())) return false;
        int stackHash = stack.hashCode();
        if (this.checked.getIfPresent(stackHash) != null) return false; // если предмет уже проверялся и не читерский
        this.checkShulkerBox(stack, p);
        if (this.checkNbt(stack, p) & checkEnchants(stack, p)) {
            return true;
        } else {
            this.checked.put(stackHash, OBJECT);
            return false;
        }
    }

    private boolean checkBanner(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        boolean cheat = false;
        if (meta instanceof BannerMeta) {
            BannerMeta bmeta = (BannerMeta) meta;
            ArrayList<Pattern> patterns = new ArrayList<>();
            for (Pattern pattern : bmeta.getPatterns()) {
                if (pattern.getPattern() == null) {
                    cheat = true;
                    continue;
                }
                patterns.add(pattern);
            }
            if (cheat) {
                bmeta.setPatterns(patterns);
                stack.setItemMeta(bmeta);
            }
        }
        return cheat;
    }

    public boolean checkFireWork(ItemStack stack) {
        boolean changed = false;
        FireworkMeta meta = (FireworkMeta) stack.getItemMeta();
        if (meta.getPower() > 3) {
            meta.setPower(3);
            changed = true;
        }
        if (meta.getEffectsSize() > 8) {
            List<FireworkEffect> list = meta.getEffects().stream().limit(8).collect(Collectors.toList());
            meta.clearEffects();
            meta.addEffects(list);
            changed = true;
        }
        if (changed) {
            stack.setItemMeta(meta);
        }
        return changed;
    }

    private boolean isCrashItem(ItemStack stack, NbtCompound tag, Material mat) {
        if (stack.getAmount() < 1 || stack.getAmount() > 64 || tag.getKeys().size() > 20) {
            return true;
        }
        int tagL = tag.toString().length();
        if ((mat == Material.NAME_TAG || tiles.contains(mat)) && tagL > 600) {
            return true;
        }
        if (isShulkerBox(stack, stack)) return false;
        return mat == Material.WRITTEN_BOOK ? (tagL >= 22000) : (tagL >= 13000);
    }

    private boolean fixEgg(NbtCompound tag) {
        NbtCompound enttag = tag.getCompound("EntityTag");
        int size = enttag.getKeys().size();
        if (size >= 2) {
            Object id = enttag.getObject("id");
            Object color = enttag.getObject("Color");
            enttag.getKeys().clear();
            if (id instanceof String) {
                enttag.put("id", (String) id);
            }
            if (color instanceof Byte) {
                enttag.put("Color", (byte) color);
            }
            tag.put("EntityTag", enttag);
            return color == null || size >= 3;
        }
        return false;
    }

    private void clearData(ItemStack stack) {
        NbtCompound tag = (NbtCompound) NbtFactory.fromItemTag(stack);
        if (tag == null) return;
        tag.getKeys().clear();
    }
}
