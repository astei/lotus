package me.steinborn.minecraft.lotus.backend;

import com.velocitypowered.api.proxy.ProxyServer;
import io.netty.channel.ChannelInitializer;
import me.steinborn.minecraft.lotus.LotusPlugin;

import java.lang.reflect.Method;

public class ServerConnectionInjector {
    public static void inject(LotusPlugin plugin) throws Exception {
        ProxyServer server = plugin.getProxy();
        Object cm = Reflection.getField(server, "cm");
        Object initializerHolder = Reflection.getField(cm, "backendChannelInitializer");
        ChannelInitializer<?> ci = (ChannelInitializer<?>) Reflection.getField(initializerHolder, "initializer");

        Method setMethod = initializerHolder.getClass().getDeclaredMethod("set", ChannelInitializer.class);
        setMethod.invoke(initializerHolder, new LilyPadChannelInitializer(ci, plugin));
    }
}
