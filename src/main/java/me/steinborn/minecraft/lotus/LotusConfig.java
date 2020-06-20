package me.steinborn.minecraft.lotus;

import lilypad.client.connect.api.ConnectSettings;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMapper;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;

import java.net.InetSocketAddress;
import java.util.List;

@ConfigSerializable
public class LotusConfig implements ConnectSettings {
    private static final ObjectMapper<LotusConfig> MAPPER;

    static {
        try {
            MAPPER = ObjectMapper.forClass(LotusConfig.class); // We hold on to the instance of our ObjectMapper
        } catch (ObjectMappingException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public LotusConfig() {
    }

    public static LotusConfig loadFrom(ConfigurationNode node) throws ObjectMappingException {
        return MAPPER.bindToNew().populate(node);
    }

    @Setting(value = "connect-host")
    private String connectHost;

    @Setting(value = "connect-port")
    private int connectPort;

    @Setting(value = "connect-username")
    private String connectUsername;

    @Setting(value = "connect-password")
    private String connectPassword;

    @Setting(value = "initial-route")
    private List<String> initialRoute;

    @Override
    public InetSocketAddress getOutboundAddress() {
        return new InetSocketAddress(connectHost, connectPort);
    }

    @Override
    public String getUsername() {
        return connectUsername;
    }

    @Override
    public String getPassword() {
        return connectPassword;
    }

    public List<String> getInitialRoute() {
        return initialRoute;
    }
}
