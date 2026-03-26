package com.github.tgo18.zrpc.registry;

import com.github.tgo18.zrpc.core.registry.ServiceInstance;
import com.github.tgo18.zrpc.registry.memory.InMemoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRegistryTest {

    private InMemoryRegistry registry;

    @BeforeEach
    void setUp() { registry = new InMemoryRegistry(); }

    @Test
    void registerAndGetInstances() {
        ServiceInstance inst = new ServiceInstance("com.example.Foo", "127.0.0.1", 20880);
        registry.register(inst);

        List<ServiceInstance> instances = registry.getInstances("com.example.Foo");
        assertEquals(1, instances.size());
        assertEquals("127.0.0.1", instances.get(0).getHost());
    }

    @Test
    void deregisterRemovesInstance() {
        ServiceInstance inst = new ServiceInstance("com.example.Foo", "127.0.0.1", 20880);
        registry.register(inst);
        registry.deregister(inst);
        assertTrue(registry.getInstances("com.example.Foo").isEmpty());
    }

    @Test
    void subscribeReceivesImmediateSnapshot() {
        ServiceInstance inst = new ServiceInstance("com.example.Foo", "127.0.0.1", 20880);
        registry.register(inst);

        List<List<ServiceInstance>> received = new ArrayList<>();
        registry.subscribe("com.example.Foo", (svc, instances) -> received.add(instances));

        assertEquals(1, received.size());
        assertEquals(1, received.get(0).size());
    }

    @Test
    void subscribeReceivesChanges() {
        List<Integer> sizes = new ArrayList<>();
        registry.subscribe("com.example.Bar", (svc, instances) -> sizes.add(instances.size()));

        registry.register(new ServiceInstance("com.example.Bar", "10.0.0.1", 8080));
        registry.register(new ServiceInstance("com.example.Bar", "10.0.0.2", 8080));

        // Initial empty + 2 change notifications
        assertEquals(3, sizes.size());
        assertEquals(0, sizes.get(0));
        assertEquals(1, sizes.get(1));
        assertEquals(2, sizes.get(2));
    }

    @Test
    void isAvailableAlwaysTrue() {
        assertTrue(registry.isAvailable());
    }
}
