package ru.leymooo.fixer;

import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.nbt.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
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
import java.util.stream.Collectors;

public class ItemChecker {

    private final static int PACKET_MAX_NBT_LENGTH = 2_097_152 - 50_000;
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
        // некоторые головы могу содержать здесь String
        NbtBase<?> skullOwnerValue = tag.getValue("SkullOwner");
        if (!(skullOwnerValue instanceof NbtCompound)) return !(skullOwnerValue.getValue() instanceof String);
        NbtCompound skullOwner = ((NbtCompound) skullOwnerValue);
        if (!skullOwner.containsKey("Properties")) return false;
        NbtCompound properties = skullOwner.getCompound("Properties");
        if (!properties.containsKey("textures")) return true;
        NbtList<NbtBase> textures = properties.getList("textures");
        for (NbtBase texture : textures.asCollection()) {
            if (!(texture instanceof NbtCompound)) continue;
            if (!((NbtCompound) texture).containsKey("Value")) continue;
            String value = ((NbtCompound) texture).getString("Value");
            if (value.trim().length() <= 0) return false;
            String decoded = new String(Base64.decodeBase64(value));
            if (decoded.isEmpty()) return true;
            JsonObject jdecoded = gson.fromJson(decoded, JsonObject.class);
            if (!jdecoded.has("textures")) return false;
            JsonObject jtextures = jdecoded.getAsJsonObject("textures");
            if (!jtextures.has("SKIN")) return false;
            JsonObject jskin = jtextures.getAsJsonObject("SKIN");
            if (!jskin.has("url")) return false;
            String url = jskin.getAsJsonPrimitive("url").getAsString();

            if (!url.isEmpty() && (url.startsWith("http://textures.minecraft.net/texture/") || url.startsWith("https://textures.minecraft.net/texture/"))) {
                return false;
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
            int level = ench.getValue();
            // проверка байпаса
            if (p.hasPermission("itemfixer.allow." + stack.getType().toString() + "." + enchant.getName() + "." + level)) {
                continue;
            }
            if (removeInvalidEnch && !enchant.canEnchantItem(stack)) {
                return true;
            }
            if (level > enchant.getMaxLevel() || level < 0) {
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
        if (p.hasPermission("itemfixer.bypass.shulker")) return false;
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
        Material type = stack.getType();
        return type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION || type == Material.TIPPED_ARROW;
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
        return checkItem(stack, p, true);
    }

    public CheckedItem checkItem(ItemStack stack, Player p, boolean cache) {
        if (stack == null || stack.getType() == Material.AIR) return null;
        if (this.world.contains(p.getWorld().getName())) return null;

        ItemStack stackOne = stack.asOne();
        CheckedItem checkedItem = checked.get(stackOne);

        if (checkedItem == null) {
            NbtCompound tag = (NbtCompound) MiniNbtFactory.fromItemTag(stack);
            String tagString = tag != null ? tag.toString() : null;
            int tagLength = tag != null ? tagString.length() : 0;
            boolean isHacked;
            try {
                isHacked = checkNbtLength(tagLength, p) || checkNbtLongArray(tag) || checkShulkerBox(stack, p) || checkNbt(stack, tag, tagString, tagLength, p) || checkEnchants(stack, p);
            } catch (Exception e) {
                plugin.getLogger().severe("Не удалось обработать предмет игрока " + p.getName() + " - " + stack.getType().name() + " - " + tag);
                e.printStackTrace();
                isHacked = true;
            }
            checkedItem = new CheckedItem(stackOne, tagLength, isHacked);
            // кешируем результат на 10 секунд
            if (cache) {
                checked.put(stackOne, checkedItem);
                WeakReference<ItemStack> weakCache = new WeakReference<>(stackOne);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    ItemStack item = weakCache.get();
                    if (item != null) {
                        checked.remove(item);
                    }
                }, 200);
            }
        }

        return checkedItem;
    }

    public void checkInventory(Inventory inventory, Player p) {
        boolean hasBypass = plugin.hasFullBypass(p);
        ArrayList<ItemStack> checkedItems = new ArrayList<>(16);
        for (ItemStack stack : inventory.getContents()) {
            // пропускаем уже проверенные предметы,
            // ибо их уже не будет в инвентаре
            if (checkedItems.contains(stack)) continue;
            if (isHacked(checkItem(stack, p, !hasBypass))) {
                inventory.remove(stack);
            }
            checkedItems.add(stack);
        }

        checkOverFlowInventory(inventory, p);
    }

    private void checkOverFlowInventory(Inventory inventory, Player p) {
        boolean hasBypass = plugin.hasFullBypass(p);
        int fullInventorSize = 0;
        ItemStack[] contents = inventory.getContents();
        Int2IntMap itemSizeMap = new Int2IntArrayMap(contents.length);

        // вычисляем размеры всех предметов и инвентаря в целом
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() == Material.AIR) continue;

            CheckedItem checkedItem = checkItem(stack, p, !hasBypass);
            if (checkedItem == null) continue;
            int tagSize = checkedItem.tagLength;
            fullInventorSize += tagSize;
            itemSizeMap.put(slot, tagSize);
        }

        // если размер инвентаря превышен,
        // то удаляем из него самые объемные предметы,
        // пока он не уменьшится до допустимых размеров
        final int maxNbtLength = p.hasPermission("itemfixer.bypass.nbtlength") ? PACKET_MAX_NBT_LENGTH : INV_MAX_NBT_LENGTH;
        if (fullInventorSize > maxNbtLength) {
            for (Int2IntMap.Entry entry : itemSizeMap.int2IntEntrySet().stream().sorted(Comparator.comparingInt(Int2IntMap.Entry::getIntValue).reversed()).collect(Collectors.toList())) {
                inventory.setItem(entry.getIntKey(), null);
                if ((fullInventorSize -= entry.getIntValue()) < maxNbtLength) break;
            }
        }
    }

    private boolean checkNbtLength(int tagLength, Player p) {
        if (tagLength > PACKET_MAX_NBT_LENGTH) return true;
        if (p.hasPermission("itemfixer.bypass.nbtlength")) return false;
        return tagLength > ITEM_MAX_NBT_LENGTH;
    }

    private boolean checkNbtLongArray(NbtCompound tag) {
        if (tag == null) return false;

        List<NbtBase<?>> toIterate = new ArrayList<>();
        List<Iterable<?>> newToIterate = new ArrayList<>();

        for (NbtBase<?> nbtBase : tag) {
            toIterate.add(nbtBase);
        }

        while (true) {
            for (NbtBase<?> child : toIterate) {
                if (child.getType() == NbtType.TAG_LONG_ARRAY) {
                    return true;
                }

                if (child instanceof NbtList) {
                    NbtList<?> childList = (NbtList<?>) child;
                    if (childList.size() == 0) continue;
                    for (Object o : childList.asCollection()) {
                        if (o instanceof NbtBase) {
                            newToIterate.add(childList);
                            break;
                        }
                    }
                    continue;
                }
                if (child instanceof NbtCompound) {
                    newToIterate.add((NbtCompound) child);
                }
            }

            if (newToIterate.isEmpty()) {
                break;
            } else {
                toIterate.clear();
                for (Iterable<?> nbtBase : newToIterate) {
                    for (Object base : nbtBase) {
                        if (base instanceof NbtBase) {
                            toIterate.add((NbtBase<?>) base);
                        }
                    }
                }
                newToIterate.clear();
            }
        }

        return false;
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
        if (enttag.getKeys().size() > 2) return true;

        String id;
        try {
            id = enttag.getString("id");
            NbtBase<?> color = enttag.getValue("Color");
            if (color != null && color.getType() != NbtType.TAG_BYTE) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            return true;
        }

        if (id != null) {
            switch (id.toLowerCase()) {
                case "item":
                case "xp_orb":
                case "area_effect_cloud":
                case "egg":
                case "leash_knot":
                case "painting":
                case "arrow":
                case "snowball":
                case "fireball":
                case "small_fireball":
                case "ender_pearl":
                case "eye_of_ender_signal":
                case "potion":
                case "xp_bottle":
                case "item_frame":
                case "wither_skull":
                case "tnt":
                case "falling_block":
                case "fireworks_rocket":
                case "spectral_arrow":
                case "shulker_bullet":
                case "dragon_fireball":
                case "armor_stand":
                case "evocation_fangs":
                case "commandblock_minecart":
                case "boat":
                case "minecart":
                case "chest_minecart":
                case "furnace_minecart":
                case "tnt_minecart":
                case "hopper_minecart":
                case "spawner_minecart":
                case "giant":
                case "ender_dragon":
                case "wither":
                case "snowman":
                case "villager_golem":
                case "llama_spit":
                case "ender_crystal":
                    return true;
            }
        }

        return false;
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
