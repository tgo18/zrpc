package com.github.tgo18.zrpc.core.exception;

/**
 * Base exception for all zRPC framework errors.
 */
public class RpcException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static final int UNKNOWN_EXCEPTION         = 0;
    public static final int NETWORK_EXCEPTION         = 1;
    public static final int TIMEOUT_EXCEPTION         = 2;
    public static final int BIZ_EXCEPTION             = 3;
    public static final int FORBIDDEN_EXCEPTION       = 4;
    public static final int SERIALIZATION_EXCEPTION   = 5;
    public static final int NO_INVOKER_AVAILABLE      = 6;
    public static final int LIMIT_EXCEEDED_EXCEPTION  = 7;

    private final int code;

    public RpcException(String message) {
        super(message);
        this.code = UNKNOWN_EXCEPTION;
    }

    public RpcException(int code, String message) {
        super(message);
        this.code = code;
    }

    public RpcException(String message, Throwable cause) {
        super(message, cause);
        this.code = UNKNOWN_EXCEPTION;
    }

    public RpcException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() { return code; }

    public boolean isTimeout() { return code == TIMEOUT_EXCEPTION; }
    public boolean isNetwork() { return code == NETWORK_EXCEPTION; }
    public boolean isBiz() { return code == BIZ_EXCEPTION; }
    public boolean isSerialization() { return code == SERIALIZATION_EXCEPTION; }
    public boolean isNoInvoker() { return code == NO_INVOKER_AVAILABLE; }
}
