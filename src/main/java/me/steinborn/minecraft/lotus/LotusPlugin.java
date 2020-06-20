package me.steinborn.minecraft.lotus;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import lilypad.client.connect.api.Connect;
import lilypad.client.connect.lib.ConnectImpl;
import me.steinborn.minecraft.lotus.backend.ServerConnectionInjector;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "lotus",
        name = "Lotus",
        version = "0.1",
        description = "Pretends that Velocity is a LilyPad proxy",
        authors = {"tuxed"}
)
public class LotusPlugin {
    private final ProxyServer proxy;
    private final Path pluginBasePath;
    private final Logger logger;
    private LotusVelocityListener velocityListener;
    private LotusLilyPadListener lilyPadListener;

    @Inject
    public LotusPlugin(ProxyServer proxy, @DataDirectory Path pluginBasePath, Logger logger) {
        this.proxy = proxy;
        this.pluginBasePath = pluginBasePath;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        final Path file = this.pluginBasePath.resolve("config.yml");
        final YAMLConfigurationLoader loader = YAMLConfigurationLoader.builder()
                .setPath(file)
                .build();
        try {
            final ConfigurationNode node = loader.load();
            final LotusConfig config = LotusConfig.loadFrom(node);

            Connect connect = new ConnectImpl(config);
            this.velocityListener = new LotusVelocityListener(this, connect, config);
            this.lilyPadListener = new LotusLilyPadListener(this, connect);
            connect.registerEvents(this.lilyPadListener);
            proxy.getEventManager().register(this, this.velocityListener);
            ServerConnectionInjector.inject(this);

            proxy.getScheduler().buildTask(this, new InitializeLilyPadConnection(this, connect, config))
                    .schedule();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Lotus", e);
        }
    }


    public Logger getLogger() {
        return logger;
    }

    public ProxyServer getProxy() {
        return proxy;
    }

    public LotusVelocityListener getVelocityListener() {
        return velocityListener;
    }

    public LotusLilyPadListener getLilyPadListener() {
        return lilyPadListener;
    }
}
