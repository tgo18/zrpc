package com.github.tgo18.zrpc.core;

import com.github.tgo18.zrpc.core.common.URL;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class URLTest {

    @Test
    void builderAndGetters() {
        URL url = URL.builder()
                .protocol("zrpc")
                .host("127.0.0.1")
                .port(20880)
                .path("com.example.FooService")
                .parameter("version", "1.0")
                .parameter("timeout", "3000")
                .build();

        assertEquals("zrpc", url.getProtocol());
        assertEquals("127.0.0.1", url.getHost());
        assertEquals(20880, url.getPort());
        assertEquals("com.example.FooService", url.getPath());
        assertEquals("1.0", url.getParameter("version"));
        assertEquals(3000, url.getIntParameter("timeout", 0));
        assertEquals("127.0.0.1:20880", url.getAddress());
    }

    @Test
    void parseFromString() {
        URL url = URL.valueOf("zrpc://10.0.0.1:20880/com.example.FooService?version=2.0&group=test");
        assertEquals("zrpc", url.getProtocol());
        assertEquals("10.0.0.1", url.getHost());
        assertEquals(20880, url.getPort());
        assertEquals("com.example.FooService", url.getPath());
        assertEquals("2.0", url.getParameter("version"));
        assertEquals("test", url.getParameter("group"));
    }

    @Test
    void addParameterImmutability() {
        URL original = URL.builder().host("localhost").port(8080).build();
        URL withParam = original.addParameter("key", "value");
        assertNull(original.getParameter("key"));
        assertEquals("value", withParam.getParameter("key"));
    }

    @Test
    void toStringRoundTrip() {
        String raw = "zrpc://127.0.0.1:20880/com.Foo?version=1.0";
        URL url = URL.valueOf(raw);
        assertTrue(url.toString().contains("version=1.0"));
        assertTrue(url.toString().contains("127.0.0.1:20880"));
    }
}
