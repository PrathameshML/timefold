const http = require('http');

const URL = 'http://localhost:8083/shifts/assign-v2';

function runSolver(payload, testName) {
    return new Promise((resolve) => {
        const postData = JSON.stringify(payload);
        const req = http.request(URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(postData)
            }
        }, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                if (res.statusCode !== 200) {
                    console.log(`[${testName}] Error ${res.statusCode}: ${data}`);
                    resolve(null);
                    return;
                }
                resolve(JSON.parse(data));
            });
        });
        req.on('error', (e) => resolve(null));
        req.write(postData);
        req.end();
    });
}

function getBasePayload() {
    return {
        "shift_name": "Validation Shift",
        "start_date": "2026-06-01",
        "end_date": "2026-06-01",
        "start_time": "08:00",
        "end_time": "16:00", // 8 hour shift
        "roles": [{ "role_name": "Cashier", "max_workers": 1 }],
        "existing_users": [
            { "employee_id": "W1", "name": "Worker 1", "role": "Cashier", "rate": 20, "rating": 3 }
        ],
        "active_constraints": [],
        "time_limit_seconds": 3,
        "unimproved_time_limit_seconds": 1
    };
}

async function testMaxWeeklyHours() {
    console.log("==========================================");
    console.log("TEST 4 - maxWeeklyHours (Threshold 40.0h)");
    console.log("Scenario: 42-hour shift requested, 1 worker available.");
    console.log("==========================================");

    for (const severity of ["SOFT", "MEDIUM", "HARD"]) {
        const payload = getBasePayload();
        // A 42 hour shift over 2 days
        payload.start_time = "00:00";
        payload.end_time = "21:00"; // 21 hours
        payload.end_date = "2026-06-02"; // 2 days * 21 hours = 42 hours
        
        payload.active_constraints = [
            { "name": "everyShiftPlanned", "severity": "MEDIUM" }, 
            { "name": "maxWeeklyHours", "severity": severity, "value": 40.0 }
        ];

        let res = await runSolver(payload, `maxWeeklyHours ${severity}`);
        if (res && res.assignments_by_date) {
            const shifts1 = res.assignments_by_date["2026-06-01"] || [];
            const shifts2 = res.assignments_by_date["2026-06-02"] || [];
            const totalAssigned = shifts1.length + shifts2.length;
            console.log(`[${severity}] Total Shifts Assigned: ${totalAssigned}/2 | Score: ${res.solver_score}`);
        }
    }
}

async function testUnavailableTimeslot() {
    console.log("\n==========================================");
    console.log("TEST 5 - unavailableTimeslot (Rating Mismatch)");
    console.log("Scenario: Role requires rating 4+. Worker has rating 3.");
    console.log("==========================================");

    for (const severity of ["SOFT", "MEDIUM", "HARD"]) {
        const payload = getBasePayload();
        payload.roles[0] = { "role_name": "Cashier", "max_workers": 1, "rating": 4 }; // Requires 4
        payload.existing_users[0] = { "employee_id": "W1", "name": "Worker 1", "role": "Cashier", "rate": 20, "rating": 3 };
        
        payload.active_constraints = [
            { "name": "everyShiftPlanned", "severity": "MEDIUM" },
            { "name": "unavailableTimeslot", "severity": severity }
        ];

        let res = await runSolver(payload, `unavailableTimeslot ${severity}`);
        if (res && res.assignments_by_date) {
            const shifts = res.assignments_by_date["2026-06-01"] || [];
            console.log(`[${severity}] Assigned? ${shifts.length > 0 ? "YES" : "NO"} | Score: ${res.solver_score}`);
        }
    }
}

async function testNoOverlappingShifts() {
    console.log("\n==========================================");
    console.log("TEST 6 - noOverlappingShifts");
    console.log("Scenario: Requesting 2 workers for the same date. 1 worker available.");
    console.log("==========================================");

    // This constraint is mathematically impossible to violate in V2 due to the data model,
    // but we will test it to observe the solver's behavior.
    for (const severity of ["SOFT", "MEDIUM", "HARD"]) {
        const payload = getBasePayload();
        payload.roles[0] = { "role_name": "Cashier", "max_workers": 2 }; // Request 2 overlapping slots
        
        payload.active_constraints = [
            { "name": "everyShiftPlanned", "severity": "SOFT" },
            { "name": "noOverlappingShifts", "severity": severity }
        ];

        let res = await runSolver(payload, `noOverlappingShifts ${severity}`);
        if (res && res.assignments_by_date) {
            const shifts = res.assignments_by_date["2026-06-01"] || [];
            console.log(`[${severity}] Total Shifts Assigned to W1: ${shifts.length}/2 | Score: ${res.solver_score}`);
        }
    }
}

async function testConsecutiveShifts() {
    console.log("\n==========================================");
    console.log("TEST 7 - consecutiveShifts");
    console.log("Scenario: 3 days. W1 assigned Day 1 and Day 3. Gap on Day 2.");
    console.log("==========================================");

    for (const severity of ["SOFT", "MEDIUM", "HARD"]) {
        const payload = getBasePayload();
        payload.start_date = "2026-06-01";
        payload.end_date = "2026-06-03"; // 3 days
        
        payload.existing_users = [
            { "employee_id": "W1", "name": "Worker 1", "role": "Cashier", "rate": 20, "rating": 5 },
            { "employee_id": "W2", "name": "Worker 2", "role": "Cashier", "rate": 50, "rating": 1 }
        ];
        
        payload.active_constraints = [
            { "name": "everyShiftPlanned", "severity": "HARD" },
            { "name": "wageOptimization", "severity": "SOFT" },
            { "name": "maxWeeklyHours", "severity": "HARD", "value": 16.0 }, // W1 can only do 2 days
            { "name": "consecutiveShifts", "severity": severity }
        ];

        let res = await runSolver(payload, `consecutiveShifts ${severity}`);
        if (res && res.assignments_by_date) {
            const d1 = res.assignments_by_date["2026-06-01"]?.[0]?.employeeName;
            const d2 = res.assignments_by_date["2026-06-02"]?.[0]?.employeeName;
            const d3 = res.assignments_by_date["2026-06-03"]?.[0]?.employeeName;
            console.log(`[${severity}] Assignments: D1:${d1}, D2:${d2}, D3:${d3} | Score: ${res.solver_score}`);
        }
    }
}

async function main() {
    await testMaxWeeklyHours();
    await testUnavailableTimeslot();
    await testNoOverlappingShifts();
    await testConsecutiveShifts();
}

main();
