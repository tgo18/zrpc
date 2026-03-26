package com.github.tgo18.zrpc.core.common;

/**
 * Standard URL parameter keys used across zRPC.
 */
public final class URLKeys {

    private URLKeys() {}

    // Service identity
    public static final String INTERFACE     = "interface";
    public static final String VERSION       = "version";
    public static final String GROUP         = "group";
    public static final String APPLICATION   = "application";

    // Transport
    public static final String PROTOCOL      = "protocol";
    public static final String CODEC         = "codec";
    public static final String SERIALIZATION = "serialization";

    // Registry
    public static final String REGISTRY      = "registry";
    public static final String REGISTER      = "register";
    public static final String SUBSCRIBE     = "subscribe";

    // Client
    public static final String TIMEOUT       = "timeout";
    public static final String RETRIES       = "retries";
    public static final String CONNECTIONS   = "connections";
    public static final String LOADBALANCE   = "loadbalance";
    public static final String CLUSTER       = "cluster";

    // Server
    public static final String THREADS       = "threads";
    public static final String QUEUES        = "queues";
    public static final String ACCEPTS       = "accepts";

    // Side
    public static final String SIDE          = "side";
    public static final String PROVIDER_SIDE = "provider";
    public static final String CONSUMER_SIDE = "consumer";

    // Misc
    public static final String TIMESTAMP     = "timestamp";
    public static final String PID           = "pid";
    public static final String WEIGHT        = "weight";
    public static final String ENABLED       = "enabled";
    public static final String DEPRECATED    = "deprecated";
    public static final String GENERIC       = "generic";
    public static final String TOKEN         = "token";
    public static final String ASYNC         = "async";
    public static final String ONEWAY        = "oneway";
}
