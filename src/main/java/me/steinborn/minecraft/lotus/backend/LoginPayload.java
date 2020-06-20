package me.steinborn.minecraft.lotus.backend;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.UuidUtils;

import java.util.ArrayList;
import java.util.List;

public class LoginPayload {
    private static final Gson gson = new Gson();

    @SerializedName("s")
    private final String securityKey;
    @SerializedName("h")
    private final String host;
    @SerializedName("rIp")
    private final String realIp;
    @SerializedName("rP")
    private final int realPort;
    @SerializedName("n")
    private final String name;
    @SerializedName("u")
    private final String id;
    @SerializedName("p")
    private final List<Property> properties;

    private LoginPayload(String securityKey, String host, String realIp, int realPort, String name, String id, List<Property> properties) {
        this.securityKey = securityKey;
        this.host = host;
        this.realIp = realIp;
        this.realPort = realPort;
        this.name = name;
        this.id = id;
        this.properties = properties;
    }

    public static LoginPayload forPlayer(String securityKey, String host, Player player) {
        List<Property> translatedProperties = new ArrayList<>();
        for (GameProfile.Property property : player.getGameProfileProperties()) {
            translatedProperties.add(new Property(property.getName(), property.getValue(), property.getSignature()));
        }
        return new LoginPayload(
                securityKey,
                host,
                player.getRemoteAddress().getHostString(),
                player.getRemoteAddress().getPort(),
                player.getUsername(),
                UuidUtils.toUndashed(player.getUniqueId()),
                translatedProperties
        );
    }

    public String toJson() {
        return gson.toJson(this);
    }

    public static class Property {
        @SerializedName("n")
        private final String name;
        @SerializedName("v")
        private final String value;
        @SerializedName("s")
        private final String signature;

        public Property(String name, String value, String signature) {
            this.name = name;
            this.value = value;
            this.signature = signature;
        }
    }
}
