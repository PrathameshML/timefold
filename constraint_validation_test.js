const http = require('http');

const URL = 'http://localhost:8083/shifts/assign-v2';

// Helper to make API requests
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
        req.on('error', (e) => {
            console.error(`[${testName}] Request failed: ${e.message}`);
            resolve(null);
        });
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
        "end_time": "18:00", // 10 hour shift
        "roles": [{ "role_name": "Cashier", "max_workers": 1 }],
        "existing_users": [
            { "employee_id": "W1", "name": "Worker 1", "role": "Cashier", "rate": 20, "rating": 3 }
        ],
        "active_constraints": [],
        "time_limit_seconds": 3,
        "unimproved_time_limit_seconds": 1
    };
}

async function testMaxDailyHours() {
    console.log("==========================================");
    console.log("TEST 1 - maxDailyHours (Threshold 8.0h)");
    console.log("Scenario: 10-hour shift requested, 1 worker available.");
    console.log("==========================================");

    for (const severity of ["SOFT", "MEDIUM", "HARD"]) {
        const payload = getBasePayload();
        payload.active_constraints = [
            { "name": "everyShiftPlanned", "severity": "MEDIUM" }, // Force it to try to fill
            { "name": "maxDailyHours", "severity": severity, "value": 8.0 }
        ];

        let res = await runSolver(payload, `maxDailyHours ${severity}`);
        if (res && res.assignments_by_date) {
            const shifts = res.assignments_by_date["2026-06-01"] || [];
            console.log(`[${severity}] Assigned? ${shifts.length > 0 ? "YES" : "NO"} | Score: ${res.solver_score}`);
        }
    }
}

async function testSkillMatch() {
    console.log("\n==========================================");
    console.log("TEST 2 - skillMatch");
    console.log("Scenario: Shift requires 'java'. Worker only has 'html'.");
    console.log("==========================================");

    for (const severity of ["SOFT", "MEDIUM", "HARD"]) {
        const payload = getBasePayload();
        payload.roles[0] = { "role_name": "Developer", "max_workers": 1, "required_skills": ["java"] };
        payload.existing_users[0] = { "employee_id": "W1", "name": "Worker 1", "role": "Developer", "rate": 20, "rating": 3, "skills": ["html"] };
        
        payload.active_constraints = [
            { "name": "everyShiftPlanned", "severity": "MEDIUM" }, // Force it to try to fill
            { "name": "skillMatch", "severity": severity, "value": 100.0 }
        ];

        let res = await runSolver(payload, `skillMatch ${severity}`);
        if (res && res.assignments_by_date) {
            const shifts = res.assignments_by_date["2026-06-01"] || [];
            console.log(`[${severity}] Assigned? ${shifts.length > 0 ? "YES" : "NO"} | Score: ${res.solver_score}`);
        }
    }
}

async function testPermanentPriority() {
    console.log("\n==========================================");
    console.log("TEST 3 - permanentPriority");
    console.log("Scenario: 1 shift available. W1 is Temporary ($20), W2 is Permanent ($25).");
    console.log("==========================================");

    for (const severity of ["SOFT", "MEDIUM", "HARD"]) {
        const payload = getBasePayload();
        payload.existing_users = [
            { "employee_id": "Temp", "name": "Temp", "role": "Cashier", "rate": 20, "rating": 3, "employeeType": "Temporary" },
            { "employee_id": "Perm", "name": "Perm", "role": "Cashier", "rate": 25, "rating": 3, "employeeType": "Permanent" }
        ];
        
        payload.active_constraints = [
            { "name": "everyShiftPlanned", "severity": "MEDIUM" },
            { "name": "wageOptimization", "severity": "SOFT" }, // naturally wants cheaper Temp
            { "name": "permanentPriority", "severity": severity }
        ];

        let res = await runSolver(payload, `permanentPriority ${severity}`);
        if (res && res.assignments_by_date) {
            const shifts = res.assignments_by_date["2026-06-01"] || [];
            console.log(`[${severity}] Assigned Worker: ${shifts.length > 0 ? shifts[0].employeeName : "NONE"} | Score: ${res.solver_score}`);
        }
    }
}

async function main() {
    await testMaxDailyHours();
    await testSkillMatch();
    await testPermanentPriority();
}

main();
