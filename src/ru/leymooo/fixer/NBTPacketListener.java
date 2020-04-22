package ru.leymooo.fixer;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.google.common.base.Charsets;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.buffer.ByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.TimeUnit;

public class NBTPacketListener extends PacketAdapter {

    public static Cache<Player, Object> cancel;
    private final Main plugin;

    public NBTPacketListener(Main plugin) {
        super(plugin, ListenerPriority.HIGHEST, PacketType.Play.Client.SET_CREATIVE_SLOT, PacketType.Play.Client.CUSTOM_PAYLOAD);
        this.plugin = plugin;
        cancel = CacheBuilder.newBuilder()
                .concurrencyLevel(2)
                .initialCapacity(20)
                .expireAfterWrite(2, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        if (event.isCancelled()) return;
        Player p = event.getPlayer();
        if (p == null) return;
        if (this.needCancel(p)) {
            event.setCancelled(true);
            return;
        }
        if (event.getPacketType() == PacketType.Play.Client.SET_CREATIVE_SLOT && p.getGameMode() == GameMode.CREATIVE) {
            this.proccessSetCreativeSlot(event, p);
        } else if (event.getPacketType() == PacketType.Play.Client.CUSTOM_PAYLOAD && !plugin.isUnsupportedVersion() && !p.hasPermission("itemfixer.bypass.packet")) {
            this.proccessCustomPayload(event, p);
        }
    }

    private void proccessSetCreativeSlot(PacketEvent event, Player p) {
        ItemStack stack = event.getPacket().getItemModifier().readSafely(0);
        if (plugin.checkItem(stack, p)) {
            cancel.put(p, new Object());
        }
    }

    private void proccessCustomPayload(PacketEvent event, Player p) {
        String channel = event.getPacket().getStrings().readSafely(0);
        if (("MC|BEdit".equals(channel) || "MC|BSign".equals(channel))) {
            cancel.put(p, new Object());
        } else if ("REGISTER".equals(channel)) {
            checkRegisterChannel(event, p);
        }
    }

    /**
     * @author justblender
     */
    private void checkRegisterChannel(PacketEvent event, Player p) {
        int channelsSize = p.getListeningPluginChannels().size();
        final PacketContainer container = event.getPacket();
        final ByteBuf buffer = (container.getSpecificModifier(ByteBuf.class).read(0)).copy();
        final String[] channels = buffer.toString(Charsets.UTF_8).split("\0");
        for (int i = 0; i < channels.length; i++) {
            if (++channelsSize > 120) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> p.kickPlayer("Too many channels registered (max: 120)"));
                break;
            }
        }
        buffer.release();
    }

    private boolean needCancel(Player p) {
        return cancel.getIfPresent(p) != null;
    }
}
