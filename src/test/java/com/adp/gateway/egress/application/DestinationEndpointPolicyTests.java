package com.adp.gateway.egress.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

class DestinationEndpointPolicyTests {

    @Test
    void allowsOnlyPublicHttpsDestinationByDefault() throws Exception {
        var policy = new DestinationEndpointPolicy(false, host -> new InetAddress[] {
            InetAddress.getByAddress(new byte[] {8, 8, 8, 8})
        });

        assertThat(policy.allows("https://provider.example")).isTrue();
        assertThat(policy.allows("http://provider.example")).isFalse();
        assertThat(policy.allows("https://user@provider.example")).isFalse();
    }

    @Test
    void rejectsPrivateLoopbackLinkLocalAndMetadataDestinations() throws Exception {
        assertThat(policyFor("127.0.0.1").allows("https://provider.example")).isFalse();
        assertThat(policyFor("10.0.0.1").allows("https://provider.example")).isFalse();
        assertThat(policyFor("169.254.1.1").allows("https://provider.example")).isFalse();
        assertThat(policyFor("100.64.0.1").allows("https://provider.example")).isFalse();
        assertThat(policyFor("fc00::1").allows("https://provider.example")).isFalse();
        assertThat(new DestinationEndpointPolicy(false).allows("https://169.254.169.254")).isFalse();
    }

    @Test
    void privateDestinationRequiresExplicitLocalOverride() {
        assertThat(new DestinationEndpointPolicy(true).allows("http://127.0.0.1:8090")).isTrue();
        assertThat(new DestinationEndpointPolicy(true).allows("http://169.254.169.254")).isFalse();
    }

    private DestinationEndpointPolicy policyFor(String address) throws Exception {
        InetAddress resolved = InetAddress.getByName(address);
        return new DestinationEndpointPolicy(false, host -> new InetAddress[] {resolved});
    }
}
