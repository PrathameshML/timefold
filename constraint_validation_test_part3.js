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
        "start_date": "2026-07-01",
        "end_date": "2026-07-01",
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

async function testOvertimeThreshold() {
    console.log("==========================================");
    console.log("TEST 8 - overtimeThreshold (Threshold 8.0h)");
    console.log("Scenario: 10-hour shift requested, 1 worker available.");
    console.log("==========================================");

    for (const severity of ["SOFT", "MEDIUM", "HARD"]) {
        const payload = getBasePayload();
        
        payload.active_constraints = [
            { "name": "everyShiftPlanned", "severity": "MEDIUM" }, 
            { "name": "overtimeThreshold", "severity": severity, "value": 8.0 }
        ];

        let res = await runSolver(payload, `overtimeThreshold ${severity}`);
        if (res && res.assignments_by_date) {
            const shifts = res.assignments_by_date["2026-07-01"] || [];
            console.log(`[${severity}] Assigned? ${shifts.length > 0 ? "YES" : "NO"} | Score: ${res.solver_score}`);
        }
    }
}

async function testBreakAfterHours() {
    console.log("\n==========================================");
    console.log("TEST 9 - breakAfterHours (Threshold 4.0h)");
    console.log("Scenario: 10-hour shift requested, 1 worker available. No scheduled breaks.");
    console.log("==========================================");

    for (const severity of ["SOFT", "MEDIUM", "HARD"]) {
        const payload = getBasePayload();
        payload.schedule_breaks = false;
        
        payload.active_constraints = [
            { "name": "everyShiftPlanned", "severity": "MEDIUM" },
            { "name": "breakAfterHours", "severity": severity, "value": 4.0 }
        ];

        let res = await runSolver(payload, `breakAfterHours ${severity}`);
        if (res && res.assignments_by_date) {
            const shifts = res.assignments_by_date["2026-07-01"] || [];
            console.log(`[${severity}] Assigned? ${shifts.length > 0 ? "YES" : "NO"} | Score: ${res.solver_score}`);
        }
    }
}

async function main() {
    await testOvertimeThreshold();
    await testBreakAfterHours();
}

main();
