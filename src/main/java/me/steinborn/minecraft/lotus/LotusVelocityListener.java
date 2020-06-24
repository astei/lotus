package me.steinborn.minecraft.lotus;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import lilypad.client.connect.api.Connect;
import lilypad.client.connect.api.request.RequestException;
import lilypad.client.connect.api.request.impl.NotifyPlayerRequest;
import lilypad.client.connect.api.result.StatusCode;
import lilypad.client.connect.api.result.impl.NotifyPlayerResult;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class LotusVelocityListener {
    private static final Component UNABLE_TO_REGISTER = TextComponent.of("Unable to register you with the network", TextColor.RED);
    private static final ResultedEvent.ComponentResult UNABLE_TO_REGISTER_RESULT = ResultedEvent.ComponentResult.denied(UNABLE_TO_REGISTER);

    private final AtomicInteger onlinePlayers = new AtomicInteger();
    private final AtomicInteger maxPlayers = new AtomicInteger();
    private final LotusPlugin plugin;
    private final Connect connect;
    private final LotusConfig config;

    public LotusVelocityListener(LotusPlugin plugin, Connect connect, LotusConfig config) {
        this.plugin = plugin;
        this.connect = connect;
        this.config = config;
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        ServerPing.Builder builder = event.getPing().asBuilder()
                .onlinePlayers(onlinePlayers.get());
        if (!config.isUseMaxPlayersFromProxy()) {
            builder.maximumPlayers(maxPlayers.get());
        }
        event.setPing(builder.build());
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onLogin(LoginEvent event) {
        try {
            NotifyPlayerResult result = this.connect.request(new NotifyPlayerRequest(true, event.getPlayer().getUsername(),
                    event.getPlayer().getUniqueId())).await(4000);
            if (result == null || result.getStatusCode() != StatusCode.SUCCESS) {
                // Unable to log player in
                event.setResult(UNABLE_TO_REGISTER_RESULT);
            }
        } catch (RequestException | InterruptedException e) {
            this.plugin.getLogger().error("Unable to notify LilyPad Connect about connect of {}", event.getPlayer().getUsername(),
                    e);
            event.setResult(UNABLE_TO_REGISTER_RESULT);
        }
    }

    @Subscribe(order = PostOrder.LAST)
    public void onLoginLast(LoginEvent event) {
        if (!event.getResult().isAllowed() && event.getResult() != UNABLE_TO_REGISTER_RESULT) {
            try {
                this.connect.request(new NotifyPlayerRequest(false, event.getPlayer().getUsername(),
                        event.getPlayer().getUniqueId()));
            } catch (RequestException e) {
                this.plugin.getLogger().error("Unable to notify LilyPad Connect about failed connect of {}", event.getPlayer().getUsername(),
                        e);
            }
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        try {
            this.connect.request(new NotifyPlayerRequest(false, event.getPlayer().getUsername(),
                    event.getPlayer().getUniqueId()));
        } catch (RequestException e) {
            this.plugin.getLogger().error("Unable to notify LilyPad Connect about disconnect of {}", event.getPlayer().getUsername(),
                    e);
        }
    }

    @Subscribe
    public void onInitialChooseServer(PlayerChooseInitialServerEvent event) {
        List<RegisteredServer> servers = new ArrayList<>();
        for (String serverName : this.config.getInitialRoute()) {
            Optional<RegisteredServer> server = this.plugin.getProxy().getServer(serverName);
            server.ifPresent(servers::add);
        }

        if (servers.isEmpty()) {
            return;
        }

        event.setInitialServer(servers.get(ThreadLocalRandom.current().nextInt(servers.size())));
    }

    public void updateCounts(int now, int max) {
        this.onlinePlayers.set(now);
        this.maxPlayers.set(max);
    }
}
