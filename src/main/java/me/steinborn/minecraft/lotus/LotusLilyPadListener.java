package me.steinborn.minecraft.lotus;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import lilypad.client.connect.api.Connect;
import lilypad.client.connect.api.event.EventListener;
import lilypad.client.connect.api.event.RedirectEvent;
import lilypad.client.connect.api.event.ServerAddEvent;
import lilypad.client.connect.api.event.ServerRemoveEvent;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class LotusLilyPadListener {

    private final Map<InetSocketAddress, String> endpointToServer = new ConcurrentHashMap<>();
    private final Map<String, ServerWithKey> nameToVelocityServer = new ConcurrentHashMap<>();
    private final LotusPlugin plugin;
    private final ProxyServer server;
    private final Connect connect;

    public LotusLilyPadListener(LotusPlugin plugin, Connect connect) {
        this.plugin = plugin;
        this.server = plugin.getProxy();
        this.connect = connect;
    }

    @EventListener
    public void onServerAdd(ServerAddEvent event) {
        String destinationStr = event.getAddress().getAddress().getHostAddress();
        InetSocketAddress destination = event.getAddress();
        if (destinationStr.equals("127.0.0.1") || destinationStr.equals("localhost")) {
            destination = new InetSocketAddress(this.connect.getSettings().getOutboundAddress().getHostName(), event.getAddress().getPort());
        }

        Optional<RegisteredServer> prev = this.server.getServer(event.getServer());
        prev.ifPresent(registeredServer -> this.server.unregisterServer(registeredServer.getServerInfo()));

        RegisteredServer server = this.server.registerServer(new ServerInfo(event.getServer(), destination));
        this.nameToVelocityServer.put(event.getServer(), new ServerWithKey(server, event.getSecurityKey()));
        this.endpointToServer.put(destination, event.getServer());

        this.plugin.getLogger().info("Updated server {} with IP {}", event.getServer(), destination);
    }

    @EventListener
    public void onServerRemove(ServerRemoveEvent event) {
        ServerWithKey affected = this.nameToVelocityServer.remove(event.getServer());
        if (affected != null) {
            this.server.unregisterServer(affected.server.getServerInfo());
            this.endpointToServer.remove(affected.server.getServerInfo().getAddress());
            this.plugin.getLogger().info("Removed server {}", event.getServer());
        }
    }

    @EventListener
    public void onRedirect(RedirectEvent redirectEvent) {
        Optional<Player> player = this.server.getPlayer(redirectEvent.getPlayer());
        if (!player.isPresent()) {
            return;
        }

        ServerWithKey to = this.nameToVelocityServer.get(redirectEvent.getServer());
        if (to == null) {
            return;
        }

        this.plugin.getLogger().info("Doing redirect of player {} to {}", player.get().getUsername(),
                to.server.getServerInfo().getName());
        player.get().createConnectionRequest(to.server).fireAndForget();
    }

    public Optional<String> getSecurityKey(InetSocketAddress destination) {
        String serverName = this.endpointToServer.get(destination);
        if (serverName == null) {
            return Optional.empty();
        }

        ServerWithKey serverInfo = this.nameToVelocityServer.get(serverName);
        return serverInfo == null ? Optional.empty() : Optional.of(serverInfo.securityKey);
    }

    private static class ServerWithKey {
        private final RegisteredServer server;
        private final String securityKey;

        private ServerWithKey(RegisteredServer server, String securityKey) {
            this.server = server;
            this.securityKey = securityKey;
        }
    }

}
