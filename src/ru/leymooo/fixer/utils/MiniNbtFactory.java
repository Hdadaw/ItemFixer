package ru.leymooo.fixer.utils;

import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.nbt.NbtBase;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;
import com.comphenix.protocol.wrappers.nbt.NbtWrapper;
import org.bukkit.inventory.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public class MiniNbtFactory {

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
    public static NbtWrapper<?> fromItemTag(ItemStack stack) {
        StructureModifier<NbtBase<?>> modifier = null;
        try {
            modifier = (StructureModifier<NbtBase<?>>) m.invoke(stack);
        } catch (Throwable e) {
            e.printStackTrace();
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
