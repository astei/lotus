package me.steinborn.minecraft.lotus;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.util.ProxyVersion;
import lilypad.client.connect.api.Connect;
import lilypad.client.connect.api.request.RequestException;
import lilypad.client.connect.api.request.impl.*;
import lilypad.client.connect.api.result.StatusCode;
import lilypad.client.connect.api.result.impl.AsProxyResult;
import lilypad.client.connect.api.result.impl.AuthenticateResult;
import lilypad.client.connect.api.result.impl.GetKeyResult;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class InitializeLilyPadConnection implements Runnable {
    private static final long DEFAULT_REQUEST_TIMEOUT = 2500L;
    private static final long RETRY_DELAY_MS = 1000L;

    private final LotusPlugin plugin;
    private final Connect connect;
    private final LotusConfig config;
    private ScheduledTask playerCountUpdateTask;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

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
            if (shutdown.get()) return;

            try {
                this.connect.connect();
            } catch (RequestException e) {
                plugin.getLogger().error("Unable to connect to LilyPad Connect server.", e);
                sleep(RETRY_DELAY_MS);
                continue;
            } catch (Throwable throwable) {
                // Break out altogether
                Thread.currentThread().interrupt();
                break;
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
                break;
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
                break;
            }

            plugin.getLogger().info("Successfully connected to LilyPad Connect server");
            this.sendCurrentPlayers();

            if (this.playerCountUpdateTask == null) {
                this.playerCountUpdateTask = this.plugin.getProxy().getScheduler().buildTask(this.plugin, this::updateCount)
                        .delay(3, TimeUnit.SECONDS)
                        .repeat(1, TimeUnit.SECONDS)
                        .schedule();
            }

            while(this.connect.isConnected()) {
                sleep(1000L);
            }
            plugin.getLogger().error("Lost connection to LilyPad Connect server, reconnecting.");
        }

        if (this.playerCountUpdateTask != null) {
            this.playerCountUpdateTask.cancel();
        }
    }

    private void updateCount() {
        if (!this.connect.isConnected() || this.connect.isClosed()) {
            return;
        }

        try {
            this.connect.request(new GetPlayersRequest())
                    .registerListener(result -> {
                        this.plugin.getVelocityListener().updateCounts(result.getCurrentPlayers(),
                                result.getMaximumPlayers());
                    });
        } catch (RequestException e) {
            // Swallow the exception, since the other case doesn't make a lot of sense?
        }
    }

    private void sendCurrentPlayers() {
        for (Player player : plugin.getProxy().getAllPlayers()) {
            try {
                this.connect.requestLater(new NotifyPlayerRequest(true, player.getUsername(), player.getUniqueId()));
            } catch (RequestException e) {
                plugin.getLogger().error("Unable to send player " + player.getUsername() + " to Connect", e);
            }
        }
        this.connect.flush();
    }

    private AsProxyRequest formProxyRequest() {
        ProxyVersion version = this.plugin.getProxy().getVersion();
        return new AsProxyRequest(
                plugin.getProxy().getBoundAddress().getPort(),
                PlainComponentSerializer.plain().serialize(plugin.getProxy().getConfiguration().getMotd()),
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

    public void shutdown() {
        this.shutdown.set(true);
        if (this.playerCountUpdateTask != null) this.playerCountUpdateTask.cancel();
        this.connect.disconnect();
    }
}
