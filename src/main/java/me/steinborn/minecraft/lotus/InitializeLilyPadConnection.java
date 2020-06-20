package me.steinborn.minecraft.lotus;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.ProxyVersion;
import lilypad.client.connect.api.Connect;
import lilypad.client.connect.api.request.RequestException;
import lilypad.client.connect.api.request.impl.*;
import lilypad.client.connect.api.result.StatusCode;
import lilypad.client.connect.api.result.impl.AsProxyResult;
import lilypad.client.connect.api.result.impl.AuthenticateResult;
import lilypad.client.connect.api.result.impl.GetKeyResult;
import lilypad.client.connect.api.result.impl.GetPlayersResult;
import net.kyori.text.serializer.plain.PlainComponentSerializer;

public class InitializeLilyPadConnection implements Runnable {
    private static final long DEFAULT_REQUEST_TIMEOUT = 2500L;
    private static final long RETRY_DELAY_MS = 1000L;

    private final LotusPlugin plugin;
    private final Connect connect;
    private final LotusConfig config;

    public InitializeLilyPadConnection(LotusPlugin plugin, Connect connect, LotusConfig config) {
        this.plugin = plugin;
        this.connect = connect;
        this.config = config;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        while (!this.connect.isClosed()) {
            try {
                this.connect.connect();
            } catch (RequestException e) {
                plugin.getLogger().error("Unable to connect to LilyPad Connect server.", e);
                sleep(RETRY_DELAY_MS);
                continue;
            } catch (Throwable throwable) {
                // Break out altogether
                Thread.currentThread().interrupt();
                return;
            }

            try {
                this.attemptAuthenticate();
            } catch (RequestException exception) {
                connect.disconnect();
                plugin.getLogger().error("Unable to authenticate to LilyPad Connect server.", exception);
                sleep(RETRY_DELAY_MS);
                continue;
            } catch (Throwable throwable) {
                // Break out altogether
                Thread.currentThread().interrupt();
                return;
            }

            try {
                AsProxyResult asProxyResult = this.connect.request(this.formProxyRequest()).await(DEFAULT_REQUEST_TIMEOUT);
                if (asProxyResult == null || asProxyResult.getStatusCode() != StatusCode.SUCCESS) {
                    throw new RequestException("AsProxyRequest timed out or unsuccessful");
                }
            } catch (RequestException e) {
                connect.disconnect();
                plugin.getLogger().error("Unable to authenticate to LilyPad Connect server.", e);
                sleep(RETRY_DELAY_MS);
                continue;
            } catch (Throwable throwable) {
                // Break out altogether
                Thread.currentThread().interrupt();
                return;
            }

            plugin.getLogger().info("Successfully connected to LilyPad Connect server");
            this.sendCurrentPlayers();

            while(this.connect.isConnected()) {
                try {
                    GetPlayersResult playersResult = this.connect.request(new GetPlayersRequest()).await(DEFAULT_REQUEST_TIMEOUT);
                    this.plugin.getVelocityListener().updateCounts(playersResult.getCurrentPlayers(), playersResult.getMaximumPlayers());
                } catch (RequestException e) {
                    connect.disconnect();
                    plugin.getLogger().error("Unable to authenticate to LilyPad Connect server.", e);
                    sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    // Break out altogether
                    Thread.currentThread().interrupt();
                    return;
                }
                sleep(1000L);
            }
            plugin.getLogger().error("Lost connection to LilyPad Connect server, reconnecting.");
        }


    }

    private void sendCurrentPlayers() {
        for (Player player : plugin.getProxy().getAllPlayers()) {
            try {
                this.connect.request(new NotifyPlayerRequest(true, player.getUsername(), player.getUniqueId()));
            } catch (RequestException e) {
                plugin.getLogger().error("Unable to send player " + player.getUsername() + " to Connect", e);
            }
        }
    }

    private AsProxyRequest formProxyRequest() {
        ProxyVersion version = this.plugin.getProxy().getVersion();
        return new AsProxyRequest(
                plugin.getProxy().getBoundAddress().getPort(),
                PlainComponentSerializer.INSTANCE.serialize(plugin.getProxy().getConfiguration().getMotdComponent()),
                "Lotus (running on " + version.getName() + " " + version.getVersion() + ")",
                10000
        );
    }

    private void attemptAuthenticate() throws RequestException, InterruptedException {
        GetKeyResult getKeyResult = connect.request(new GetKeyRequest()).await(DEFAULT_REQUEST_TIMEOUT);
        if (getKeyResult == null) {
            throw new RequestException("GetKeyRequest timed out");
        }

        AuthenticateResult authenticationResult = connect.request(new AuthenticateRequest(config.getUsername(),
                config.getPassword(), getKeyResult.getKey())).await(DEFAULT_REQUEST_TIMEOUT);
        if (authenticationResult == null) {
            throw new RequestException("AuthenticateRequest timed out");
        }

        if (authenticationResult.getStatusCode() != StatusCode.SUCCESS) {
            throw new RequestException("Authentication failed: " + authenticationResult.getStatusCode());
        }
    }
}
