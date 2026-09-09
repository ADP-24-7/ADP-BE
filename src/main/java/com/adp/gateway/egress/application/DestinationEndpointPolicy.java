package com.adp.gateway.egress.application;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DestinationEndpointPolicy {

    private final boolean allowPrivateDestinations;
    private final HostResolver hostResolver;

    @Autowired
    public DestinationEndpointPolicy(
        @Value("${adp.security.egress.allow-private-destinations:false}") boolean allowPrivateDestinations
    ) {
        this(allowPrivateDestinations, InetAddress::getAllByName);
    }

    DestinationEndpointPolicy(boolean allowPrivateDestinations, HostResolver hostResolver) {
        this.allowPrivateDestinations = allowPrivateDestinations;
        this.hostResolver = hostResolver;
    }

    public boolean allows(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("https".equals(scheme) || "http".equals(scheme))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || uri.getQuery() != null) {
                return false;
            }
            if (!allowPrivateDestinations && "http".equals(scheme)) {
                return false;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (isMetadataHost(host)) {
                return false;
            }
            for (InetAddress address : hostResolver.resolve(host)) {
                if (!allowPrivateDestinations && isNonPublic(address)) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException | UnknownHostException exception) {
            return false;
        }
    }

    private boolean isMetadataHost(String host) {
        return "metadata.google.internal".equals(host)
            || "metadata.google".equals(host)
            || "169.254.169.254".equals(host);
    }

    private boolean isNonPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first == 0
                || (first == 100 && second >= 64 && second <= 127)
                || first >= 224;
        }
        return bytes.length == 16 && (Byte.toUnsignedInt(bytes[0]) & 0xfe) == 0xfc;
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
