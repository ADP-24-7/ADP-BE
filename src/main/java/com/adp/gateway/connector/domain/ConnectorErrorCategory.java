package com.adp.gateway.connector.domain;

public enum ConnectorErrorCategory {
    NONE,
    CONNECTION_CONFIGURATION,
    TRANSPORT,
    PROVIDER_CLIENT_ERROR,
    PROVIDER_SERVER_ERROR,
    RESPONSE_PARSE_ERROR
}
