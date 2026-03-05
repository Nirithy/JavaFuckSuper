package com.obfuscator.generator;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DynamicNameGeneratorTest {

    @Test
    public void testHighConcurrencyNoConflicts() throws InterruptedException {
        int threadCount = 100;
        int iterationsPerThread = 1000;
        int totalExpectedNames = threadCount * iterationsPerThread;

        Set<String> generatedNames = ConcurrentHashMap.newKeySet();
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        String name = DynamicNameGenerator.generate();
                        generatedNames.add(name);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all threads to finish
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Threads did not complete in time");
        executorService.shutdown();

        // Ensure no two names generated were identical
        assertEquals(totalExpectedNames, generatedNames.size(), "There should be no naming conflicts under high concurrency");
    }
}
