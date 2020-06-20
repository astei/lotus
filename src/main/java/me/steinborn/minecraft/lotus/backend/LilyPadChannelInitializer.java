package me.steinborn.minecraft.lotus.backend;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import me.steinborn.minecraft.lotus.LotusPlugin;

public class LilyPadChannelInitializer extends ChannelInitializer<Channel> {
    private final ChannelInitializer<?> original;
    private final LotusPlugin plugin;

    public LilyPadChannelInitializer(ChannelInitializer<?> original, LotusPlugin plugin) {
        this.original = original;
        this.plugin = plugin;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ch.pipeline().addLast("original-velocity", this.original);
        ch.pipeline().addLast("lilypad", new InterceptSentHandshake(plugin));
    }
}
