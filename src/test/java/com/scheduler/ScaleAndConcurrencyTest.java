package com.scheduler;

import com.scheduler.service.SolverService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;

@QuarkusTest
@Tag("extreme")
public class ScaleAndConcurrencyTest {

    @Inject
    SolverService solverService;

    @Test
    public void testConcurrency10Requests() throws InterruptedException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    Map<String, Object> req = new HashMap<>();
                    req.put("shift_name", "Concurrent Shift " + requestId);
                    req.put("start_date", "2026-10-01");
                    req.put("end_date", "2026-10-01");
                    req.put("start_time", "09:00");
                    req.put("end_time", "17:00");
                    req.put("optimization", "both");
                    req.put("overrideExisting", true);
                    
                    req.put("roles", List.of(
                        Map.of("role_name", "Developer", "max_workers", 1, "rating", 3)
                    ));

                    req.put("existing_users", List.of(
                        Map.of(
                            "employee_id", "EMP_" + requestId,
                            "name", "Concurrent User",
                            "role", "Developer",
                            "rate", 20,
                            "unit", "hour",
                            "rating", 5,
                            "employeeType", "Permanent",
                            "gender", "Male"
                        )
                    ));

                    Map<String, Object> result = solverService.solveShift(req);
                    if ("success".equals(result.get("status"))) {
                        successCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        
        assertEquals(threads, successCount.get());
        assertEquals(0, errorCount.get());
    }

    @Test
    public void testConcurrency50Requests() throws InterruptedException {
        runConcurrencyTest(50);
    }

    @Test
    public void testConcurrency100Requests() throws InterruptedException {
        runConcurrencyTest(100);
    }

    private void runConcurrencyTest(int threads) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(threads, 20));
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    Map<String, Object> req = new HashMap<>();
                    req.put("shift_name", "Concurrent Shift " + threads + "_" + requestId);
                    req.put("start_date", "2026-10-01");
                    req.put("end_date", "2026-10-01");
                    req.put("start_time", "09:00");
                    req.put("end_time", "17:00");
                    req.put("optimization", "both");
                    req.put("overrideExisting", true);
                    
                    req.put("roles", List.of(
                        Map.of("role_name", "Developer", "max_workers", 1, "rating", 3)
                    ));

                    req.put("existing_users", List.of(
                        Map.of(
                            "employee_id", "EMP_CONC_" + threads + "_" + requestId,
                            "name", "Concurrent User",
                            "role", "Developer",
                            "rate", 20,
                            "unit", "hour",
                            "rating", 5,
                            "employeeType", "Permanent",
                            "gender", "Male"
                        )
                    ));

                    Map<String, Object> result = solverService.solveShift(req);
                    if ("success".equals(result.get("status"))) {
                        successCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(120, TimeUnit.SECONDS), "Concurrency test timed out");
        executor.shutdown();
        
        assertEquals(threads, successCount.get(), "Not all concurrent requests succeeded");
        assertEquals(0, errorCount.get(), "Errors occurred during concurrent execution");
    }

    @Test
    public void testLargeScaleDataset() {
        Map<String, Object> req = new HashMap<>();
        req.put("shift_name", "Large Scale Shift");
        req.put("start_date", "2026-10-01");
        req.put("end_date", "2026-10-01"); // 1 day for quick test
        req.put("start_time", "09:00");
        req.put("end_time", "17:00");
        req.put("optimization", "both");
        
        req.put("roles", List.of(
            Map.of("role_name", "Developer", "max_workers", 50, "rating", 3) // 50 required
        ));

        List<Map<String, Object>> employees = new ArrayList<>();
        // Generate 150 employees
        for (int i = 0; i < 150; i++) {
            employees.add(Map.of(
                "employee_id", "EMP_" + i,
                "name", "User " + i,
                "role", "Developer",
                "rate", 20 + (i % 30),
                "unit", "hour",
                "rating", 3 + (i % 3),
                "employeeType", "Permanent",
                "gender", "Male"
            ));
        }
        req.put("existing_users", employees);

        Map<String, Object> result = solverService.solveShift(req);
        assertEquals("success", result.get("status"));
        
        int assigned = (int) result.getOrDefault("new_assignments_made", 0);
        // Timefold should assign exactly max_workers
        assertEquals(50, assigned);
    }
}
