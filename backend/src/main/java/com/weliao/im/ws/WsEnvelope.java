package com.weliao.im.ws;

/**
 * WebSocket 消息信封：{ type, data }。
 */
public record WsEnvelope(String type, Object data) {

    public static WsEnvelope of(String type, Object data) {
        return new WsEnvelope(type, data);
    }

    public static WsEnvelope of(String type) {
        return new WsEnvelope(type, null);
    }
}
