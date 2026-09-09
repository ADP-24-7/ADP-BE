package com.adp.gateway.egress.application;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.adp.gateway.observability.GatewayObservability;

@Component
public class DestinationEndpointPolicy {

    private final boolean allowPrivateDestinations;
    private final Set<String> allowedHosts;
    private final HostResolver hostResolver;
    private final GatewayObservability observability;

    @Autowired
    public DestinationEndpointPolicy(
        @Value("${adp.security.egress.allow-private-destinations:false}") boolean allowPrivateDestinations,
        @Value("${adp.security.egress.allowed-hosts:integrate.api.nvidia.com}") String allowedHosts,
        GatewayObservability observability
    ) {
        this(allowPrivateDestinations, parseAllowedHosts(allowedHosts), InetAddress::getAllByName, observability);
    }

    public DestinationEndpointPolicy(boolean allowPrivateDestinations) {
        this(allowPrivateDestinations, Set.of(), InetAddress::getAllByName, null);
    }

    DestinationEndpointPolicy(
        boolean allowPrivateDestinations,
        Set<String> allowedHosts,
        HostResolver hostResolver
    ) {
        this(allowPrivateDestinations, allowedHosts, hostResolver, null);
    }

    private DestinationEndpointPolicy(
        boolean allowPrivateDestinations,
        Set<String> allowedHosts,
        HostResolver hostResolver,
        GatewayObservability observability
    ) {
        this.allowPrivateDestinations = allowPrivateDestinations;
        this.allowedHosts = Set.copyOf(allowedHosts);
        this.hostResolver = hostResolver;
        this.observability = observability;
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
                return rejected();
            }
            if (!allowPrivateDestinations && ("http".equals(scheme) || (uri.getPort() != -1 && uri.getPort() != 443))) {
                return rejected();
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (!allowPrivateDestinations && !allowedHosts.contains(host)) {
                return rejected();
            }
            if (isMetadataHost(host)) {
                return rejected();
            }
            for (InetAddress address : hostResolver.resolve(host)) {
                if (!allowPrivateDestinations && isNonPublic(address)) {
                    return rejected();
                }
            }
            return true;
        } catch (IllegalArgumentException | UnknownHostException exception) {
            return rejected();
        }
    }

    private boolean rejected() {
        if (observability != null) {
            observability.security(GatewayObservability.SecurityOutcome.DESTINATION_REJECTED);
        }
        return false;
    }

    private static Set<String> parseAllowedHosts(String configuredHosts) {
        return Arrays.stream(configuredHosts.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
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
