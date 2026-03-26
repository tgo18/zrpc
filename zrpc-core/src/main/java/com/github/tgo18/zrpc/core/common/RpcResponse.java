package com.github.tgo18.zrpc.core.common;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an RPC invocation response.
 */
public class RpcResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        OK(200),
        CLIENT_ERROR(400),
        SERVER_ERROR(500),
        TIMEOUT(408),
        SERVICE_NOT_FOUND(404),
        SERIALIZATION_ERROR(600),
        NETWORK_ERROR(700);

        private final int code;
        Status(int code) { this.code = code; }
        public int getCode() { return code; }
    }

    /** Matches the corresponding request ID */
    private long requestId;

    /** Response status */
    private Status status = Status.OK;

    /** Return value (null for void methods) */
    private Object result;

    /** Exception if the invocation threw */
    private Throwable exception;

    /** Response attachments (trace IDs propagated back, etc.) */
    private Map<String, String> attachments = new HashMap<>();

    public RpcResponse() {}

    public RpcResponse(long requestId) {
        this.requestId = requestId;
    }

    public static RpcResponse success(long requestId, Object result) {
        RpcResponse resp = new RpcResponse(requestId);
        resp.result = result;
        resp.status = Status.OK;
        return resp;
    }

    public static RpcResponse error(long requestId, Status status, Throwable exception) {
        RpcResponse resp = new RpcResponse(requestId);
        resp.status = status;
        resp.exception = exception;
        return resp;
    }

    public static RpcResponse error(long requestId, Throwable exception) {
        return error(requestId, Status.SERVER_ERROR, exception);
    }

    public boolean isSuccess() { return status == Status.OK && exception == null; }
    public boolean hasException() { return exception != null; }

    public long getRequestId() { return requestId; }
    public void setRequestId(long requestId) { this.requestId = requestId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }

    public Throwable getException() { return exception; }
    public void setException(Throwable exception) { this.exception = exception; }

    public Map<String, String> getAttachments() { return attachments; }
    public void setAttachments(Map<String, String> attachments) { this.attachments = attachments; }

    public void setAttachment(String key, String value) { attachments.put(key, value); }
    public String getAttachment(String key) { return attachments.get(key); }

    @Override
    public String toString() {
        return "RpcResponse{requestId=" + requestId +
                ", status=" + status +
                (hasException() ? ", exception=" + exception.getMessage() : "") +
                "}";
    }
}
