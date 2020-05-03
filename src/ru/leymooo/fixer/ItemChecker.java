package ru.leymooo.fixer;

import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.nbt.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.codec.binary.Base64;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.banner.Pattern;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public class ItemChecker {

    private final static int INV_MAX_NBT_LENGTH = 100_000;
    private final static int ITEM_MAX_NBT_LENGTH = 50_000;
    private final Gson gson = new Gson();
    private final Boolean removeInvalidEnch;
    private final Boolean checkench;
    private final HashSet<String> nbt;
    private final HashSet<String> world;
    private final EnumSet<Material> tiles;
    private final HashSet<String> ignoreNbt;
    private final Main plugin;
    private final Map<ItemStack, CheckedItem> checked = new WeakHashMap<>();

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
    }

    @SuppressWarnings("rawtypes")
    public boolean isCrashSkull(NbtCompound tag) {
        if (!tag.containsKey("SkullOwner")) return false;
        NbtCompound skullOwner = tag.getCompound("SkullOwner");
        if (!skullOwner.containsKey("Properties")) return false;
        NbtCompound properties = skullOwner.getCompound("Properties");
        if (!properties.containsKey("textures")) return true;
        NbtList<NbtBase> textures = properties.getList("textures");
        for (NbtBase texture : textures.asCollection()) {
            if (!(texture instanceof NbtCompound)) continue;
            if (!((NbtCompound) texture).containsKey("Value")) continue;
            if (((NbtCompound) texture).getString("Value").trim().length() > 0) {
                String decoded = new String(Base64.decodeBase64(((NbtCompound) texture).getString("Value")));
                if (decoded.isEmpty()) return true;
                if (decoded.contains("textures")) {
                    JsonObject jdecoded = gson.fromJson(decoded, JsonObject.class);
                    if (!jdecoded.has("textures")) return false;
                    JsonObject jtextures = jdecoded.getAsJsonObject("textures");
                    if (!jtextures.has("SKIN")) return false;
                    JsonObject jskin = jtextures.getAsJsonObject("SKIN");
                    if (!jskin.has("url")) return false;
                    String url = jskin.getAsJsonPrimitive("url").getAsString();

                    if (url.isEmpty() || url.trim().length() == 0) return true;
                    if (url.startsWith("http://textures.minecraft.net/texture/") || url.startsWith("https://textures.minecraft.net/texture/")) {
                        return false;
                    }
                }
            }

        }
        return true;
    }

    private boolean checkEnchants(ItemStack stack, Player p) {
        if (!checkench) return false;
        if (p.hasPermission("itemfixer.bypass.enchant")) return false;
        if (!stack.hasItemMeta() || !stack.getItemMeta().hasEnchants()) return false;
        for (Map.Entry<Enchantment, Integer> ench : stack.getItemMeta().getEnchants().entrySet()) {
            Enchantment enchant = ench.getKey();
            // проверка байпаса
            if (p.hasPermission("itemfixer.allow." + stack.getType().toString() + "." + enchant.getName() + "." + ench.getValue())) {
                continue;
            }
            if (removeInvalidEnch && !enchant.canEnchantItem(stack)) {
                return true;
            }
            if (ench.getValue() > enchant.getMaxLevel() || ench.getValue() < 0) {
                return true;
            }
        }
        return false;
    }

    private boolean checkNbt(ItemStack stack, NbtCompound tag, String tagS, int tagLength, Player p) {
        if (tag == null) return false;
        if (p.hasPermission("itemfixer.bypass.nbt")) return false;

        Material mat = stack.getType();
        if (this.isCrashItem(stack, tag, tagLength, mat)) {
            return true;
        }
        for (String nbt1 : nbt) {
            if (tag.containsKey(nbt1)) {
                return true;
            }
        }
        if (tag.containsKey("BlockEntityTag") && !isShulkerBox(stack)) {
            if (mat != Material.BANNER && (plugin.isVersion1_8() || mat != Material.SHIELD)) {
                return !ignoreNbt.contains("BlockEntityTag");
            }
        } else if (mat == Material.WRITTEN_BOOK && ((!ignoreNbt.contains("ClickEvent") && tagS.contains("ClickEvent"))
                || (!ignoreNbt.contains("run_command") && tagS.contains("run_command")))) {
            return true;
        } else if (mat == Material.MONSTER_EGG && !ignoreNbt.contains("EntityTag") && tag.containsKey("EntityTag") && checkMonsterEgg(tag)) {
            return true;
        } else if (mat == Material.ARMOR_STAND && !ignoreNbt.contains("EntityTag") && tag.containsKey("EntityTag")) {
            return true;
        } else if ((mat == Material.SKULL || mat == Material.SKULL_ITEM) && stack.getDurability() == 3) {
            return isCrashSkull(tag);
        } else if (mat == Material.FIREWORK && !ignoreNbt.contains("Explosions") && checkFireWork(stack)) {
            return true;
        } else if (checkBanner(stack)) {
            return true;
        } else
            return isPotion(stack) && (!ignoreNbt.contains("CustomPotionEffects") && tag.containsKey("CustomPotionEffects")
                    && (checkPotion(stack, p) || checkCustomColor(tag)));
        return false;
    }

    private boolean checkShulkerBox(ItemStack stack, Player p) {
        if (!isShulkerBox(stack)) return false;
        for (ItemStack is : ((ShulkerBox) ((BlockStateMeta) stack.getItemMeta()).getBlockState()).getInventory().getContents()) {
            if (is == null) continue;
            if (isShulkerBox(is) || isHacked(checkItem(is, p))) {
                return true;
            }
        }
        return false;
    }

    private boolean isPotion(ItemStack stack) {
        try {
            return stack.hasItemMeta() && stack.getItemMeta() instanceof PotionMeta;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private boolean checkCustomColor(NbtCompound tag) {
        if (!tag.containsKey("CustomPotionColor")) return false;
        int color = tag.getInteger("CustomPotionColor");
        try {
            Color.fromBGR(color);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private boolean checkPotion(ItemStack stack, Player p) {
        if (!p.hasPermission("itemfixer.bypass.potion")) {
            PotionMeta meta = (PotionMeta) stack.getItemMeta();
            for (PotionEffect ef : meta.getCustomEffects()) {
                // проверка байпаса
                if (p.hasPermission("itemfixer.allow." + ef.getType().toString() + "." + (ef.getAmplifier() + 1))) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private boolean isShulkerBox(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        if (plugin.isVersion1_8()) return false;
        if (!stack.hasItemMeta()) return false;

        if (stack.getType().name().endsWith("SHULKER_BOX")) {
            try {
                if (stack.getItemMeta() instanceof BlockStateMeta) return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return false;
    }

    public CheckedItem checkItem(ItemStack stack, Player p) {
        if (stack == null || stack.getType() == Material.AIR) return null;
        if (this.world.contains(p.getWorld().getName())) return null;

        CheckedItem checkedItem = checked.get(stack);

        if (checkedItem == null) {
            NbtCompound tag = (NbtCompound) MiniNbtFactory.fromItemTag(stack);
            String tagString = tag != null ? tag.toString() : null;
            int tagLength = tag != null ? tagString.length() : 0;
            boolean isHacked;
            try {
                isHacked = checkNbtLength(tagLength, p) || checkShulkerBox(stack, p) || checkNbt(stack, tag, tagString, tagLength, p) || checkEnchants(stack, p);
            } catch (Exception e) {
                plugin.getLogger().severe("Не удалось обработать предмет игрока " + p.getName() + " - " + stack.getType().name() + " - " + tag);
                e.printStackTrace();
                isHacked = true;
            }
            checkedItem = new CheckedItem(stack, tagLength, isHacked);
            // кешируем результат на 30 секунд
            checked.put(stack, checkedItem);
            WeakReference<ItemStack> weakCache = new WeakReference<>(stack);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ItemStack item = weakCache.get();
                if (item != null) {
                    checked.remove(item);
                }
            }, 600);
        }

        return checkedItem;
    }

    public void checkInventory(Inventory inventory, Player p) {
        ArrayList<ItemStack> checkedItems = new ArrayList<>(16);
        for (ItemStack stack : inventory.getContents()) {
            // пропускаем уже проверенные предметы,
            // ибо их уже не будет в инвентаре
            if (checkedItems.contains(stack)) continue;
            if (isHacked(checkItem(stack, p))) {
                inventory.remove(stack);
            }
            checkedItems.add(stack);
        }

        checkOverFlowInventory(inventory, p);
    }

    private void checkOverFlowInventory(Inventory inventory, Player p) {
        int fullInventorSize = 0;
        ItemStack[] contents = inventory.getContents();
        Map<Integer, Integer> itemSizeMap = new HashMap<>(contents.length);

        // вычисляем размеры всех предметов и инвентаря в целом
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() == Material.AIR) continue;

            CheckedItem checkedItem = checkItem(stack, p);
            if (checkedItem == null) continue;
            int tagSize = checkedItem.tagLength;
            fullInventorSize += tagSize;
            itemSizeMap.put(slot, tagSize);
        }

        // если размер инвентаря превышен,
        // то удаляем из него самые объемные предметы,
        // пока он не уменьшится до допустимых размеров
        if (fullInventorSize > INV_MAX_NBT_LENGTH) {
            for (Map.Entry<Integer, Integer> entry : itemSizeMap.entrySet().stream().sorted(Comparator.comparingInt((ToIntFunction<Map.Entry<Integer, Integer>>) Map.Entry::getValue).reversed()).collect(Collectors.toList())) {
                inventory.setItem(entry.getKey(), null);
                if ((fullInventorSize -= entry.getValue()) < INV_MAX_NBT_LENGTH) break;
            }
        }
    }

    private boolean checkNbtLength(int tagLength, Player p) {
        if (p.hasPermission("itemfixer.bypass.nbtlength")) return false;
        return tagLength > ITEM_MAX_NBT_LENGTH;
    }

    private boolean checkBanner(ItemStack stack) {
        if (stack.getType() != Material.BANNER) return false;

        BannerMeta meta = (BannerMeta) stack.getItemMeta();
        if (meta == null) return false;

        for (Pattern pattern : meta.getPatterns()) {
            if (pattern.getPattern() == null) {
                return true;
            }
        }

        return false;
    }

    public boolean checkFireWork(ItemStack stack) {
        FireworkMeta meta = (FireworkMeta) stack.getItemMeta();
        return meta.getPower() > 3 || meta.getEffectsSize() > 8;
    }

    private boolean isCrashItem(ItemStack stack, NbtCompound tag, int tagL, Material mat) {
        if (stack.getAmount() < 1 || stack.getAmount() > 64 || tag.getKeys().size() > 20) {
            return true;
        }
        if ((mat == Material.NAME_TAG || tiles.contains(mat)) && tagL > 600) {
            return true;
        }
        if (isShulkerBox(stack)) return false;
        return mat == Material.WRITTEN_BOOK ? (tagL >= 16000) : (tagL >= 13000);
    }

    private boolean checkMonsterEgg(NbtCompound tag) {
        NbtCompound enttag = tag.getCompound("EntityTag");
        int size = enttag.getKeys().size();
        if (size < 2) return false;
        if (size > 2) return true;

        try {
            enttag.getString("id");
            enttag.getByte("Color");
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    boolean isHacked(CheckedItem checked) {
        return checked != null && checked.isHacked;
    }

    static class CheckedItem {
        final ItemStack itemStack;
        final int tagLength;
        final boolean isHacked;

        private CheckedItem(ItemStack itemStack, int tagLength, boolean isHacked) {
            this.itemStack = itemStack;
            this.tagLength = tagLength;
            this.isHacked = isHacked;
        }
    }

    private static class MiniNbtFactory {

        private static MethodHandle m;

        static {
            try {
                Method m = NbtFactory.class.getDeclaredMethod("getStackModifier", ItemStack.class);
                m.setAccessible(true);
                MiniNbtFactory.m = MethodHandles.lookup().unreflect(m);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @SuppressWarnings("unchecked")
        static NbtWrapper<?> fromItemTag(ItemStack stack) {
            StructureModifier<NbtBase<?>> modifier;
            try {
                modifier = (StructureModifier<NbtBase<?>>) m.invoke(stack);
            } catch (Throwable e) {
                e.printStackTrace();
                return null;
            }
            NbtBase<?> result = modifier.read(0);
            //Try fix old items
            if (result != null && result.toString().contains("{\"name\": \"null\"}")) {
                modifier.write(0, null);
                result = modifier.read(0);
            }
            if (result == null) {
                return null;
            }
            return NbtFactory.fromBase(result);
        }
    }
}
