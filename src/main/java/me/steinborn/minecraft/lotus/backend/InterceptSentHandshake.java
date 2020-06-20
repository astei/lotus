package me.steinborn.minecraft.lotus.backend;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.UuidUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import me.steinborn.minecraft.lotus.LotusPlugin;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public class InterceptSentHandshake extends ChannelOutboundHandlerAdapter {
    private static final Class<?> HANDSHAKE;
    private static final Field HANDSHAKE_ADDRESS_FIELD;

    static {
        try {
            HANDSHAKE = Class.forName("com.velocitypowered.proxy.protocol.packet.Handshake");
            HANDSHAKE_ADDRESS_FIELD = HANDSHAKE.getDeclaredField("serverAddress");
            HANDSHAKE_ADDRESS_FIELD.setAccessible(true);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final LotusPlugin plugin;

    public InterceptSentHandshake(LotusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try {
            if (msg.getClass().isAssignableFrom(HANDSHAKE)) {
                // At this low level, we don't even know _who_ is establishing the connection. We assume BungeeCord
                // forwarding and work from there.
                String rawHostname = (String) HANDSHAKE_ADDRESS_FIELD.get(msg);
                String[] hostParts = rawHostname.split("\0");
                if (hostParts.length != 4) {
                    return;
                }

                String extractedHostname = hostParts[0];
                UUID playerId = UuidUtils.fromUndashed(hostParts[2]);
                Optional<Player> player = plugin.getProxy().getPlayer(playerId);
                if (!player.isPresent()) {
                    return;
                }

                Optional<String> securityKey = plugin.getLilyPadListener().getSecurityKey((InetSocketAddress) ctx.channel().remoteAddress());
                if (!securityKey.isPresent()) {
                    return;
                }

                String json = LoginPayload.forPlayer(securityKey.get(), extractedHostname, player.get()).toJson();
                HANDSHAKE_ADDRESS_FIELD.set(msg, json);
                ctx.pipeline().remove(this);
            }
        } finally {
            ctx.write(msg, promise);
        }
    }
}
