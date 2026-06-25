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
        "start_date": "2026-08-01",
        "end_date": "2026-08-01",
        "start_time": "08:00",
        "end_time": "16:00",
        "roles": [{ "role_name": "Cashier", "max_workers": 1 }],
        "existing_users": [
            { "employee_id": "W1", "name": "Worker 1", "role": "Cashier", "rate": 20, "rating": 3 }
        ],
        "active_constraints": [],
        "time_limit_seconds": 3,
        "unimproved_time_limit_seconds": 1,
        "schedule_breaks": false
    };
}

async function testMaxWeeklyHours() {
    console.log("\n==========================================");
    console.log("1. Max Weekly Hours (40h limit)");
    console.log("Scenario: 1 Employee, 6 shifts of 8 hours (48 hours total demand).");
    console.log("==========================================");

    for (const severity of ["HARD", "MEDIUM", "SOFT"]) {
        const payload = getBasePayload();
        payload.start_date = "2026-08-01";
        payload.end_date = "2026-08-06"; // 6 days = 6 shifts of 8 hours
        
        payload.active_constraints = [
            { "name": "everyShiftPlanned", "severity": "MEDIUM" }, // So MEDIUM vs MEDIUM can fight
            { "name": "maxWeeklyHours", "severity": severity, "value": 40.0 }
        ];

        let res = await runSolver(payload, `maxWeeklyHours ${severity}`);
        if (res && res.assignments_by_date) {
            let assignedCount = 0;
            for (let date in res.assignments_by_date) assignedCount += res.assignments_by_date[date].length;
            console.log(`[${severity}] Shifts Assigned: ${assignedCount}/6 | Score: ${res.solver_score}`);
        }
    }
}

async function testMaxDailyHours() {
    console.log("\n==========================================");
    console.log("2. Max Daily Hours (8h limit)");
    console.log("Scenario: 1 Employee, 2 shifts of 6 hours on the *same day* (12 hours).");
    console.log("NOTE: V2 data model only allows 1 shift per day per employee. We will test this by making a 12 hour shift instead.");
    console.log("==========================================");

    for (const severity of ["HARD", "MEDIUM", "SOFT"]) {
        const payload = getBasePayload();
        payload.end_time = "20:00"; // 12 hour shift
        
        payload.active_constraints = [
            { "name": "everyShiftPlanned", "severity": "MEDIUM" },
            { "name": "maxDailyHours", "severity": severity, "value": 8.0 }
        ];

        let res = await runSolver(payload, `maxDailyHours ${severity}`);
        if (res && res.assignments_by_date) {
            let assignedCount = res.assignments_by_date["2026-08-01"] ? 1 : 0;
            console.log(`[${severity}] Assigned? ${assignedCount > 0 ? "YES (12h)" : "NO"} | Score: ${res.solver_score}`);
        }
    }
}

async function testSkillMatch() {
    console.log("\n==========================================");
    console.log("3. Skill Match");
    console.log("Scenario: 1 Shift requiring 'Customer Service'. 1 Employee without the skill.");
    console.log("==========================================");

    for (const severity of ["HARD", "MEDIUM", "SOFT"]) {
        const payload = getBasePayload();
        payload.roles[0].required_skills = ["Customer Service"];
        payload.existing_users[0].skills = ["Stocking"];
        
        payload.active_constraints = [
            { "name": "everyShiftPlanned", "severity": "MEDIUM" },
            { "name": "skillMatch", "severity": severity }
        ];

        let res = await runSolver(payload, `skillMatch ${severity}`);
        if (res && res.assignments_by_date) {
            let assignedCount = res.assignments_by_date["2026-08-01"] ? 1 : 0;
            console.log(`[${severity}] Assigned? ${assignedCount > 0 ? "YES" : "NO"} | Score: ${res.solver_score}`);
        }
    }
}

async function testNoOverlappingShifts() {
    console.log("\n==========================================");
    console.log("4. No Overlapping Shifts");
    console.log("Scenario: 1 Employee. 2 shifts occurring simultaneously (max_workers=2).");
    console.log("==========================================");

    for (const severity of ["HARD", "MEDIUM", "SOFT"]) {
        const payload = getBasePayload();
        payload.roles[0].max_workers = 2; // Request 2 overlapping slots
        
        payload.active_constraints = [
            { "name": "everyShiftPlanned", "severity": "SOFT" },
            { "name": "noOverlappingShifts", "severity": severity }
        ];

        let res = await runSolver(payload, `noOverlappingShifts ${severity}`);
        if (res && res.assignments_by_date) {
            let assignedCount = res.assignments_by_date["2026-08-01"] ? res.assignments_by_date["2026-08-01"].length : 0;
            console.log(`[${severity}] Shifts assigned to W1: ${assignedCount}/2 | Score: ${res.solver_score}`);
        }
    }
}

async function testConsecutiveShifts() {
    console.log("\n==========================================");
    console.log("5. Consecutive Shifts (Gap Minimization)");
    console.log("Scenario: 1 Employee. 4 possible days (Mon, Tue, Thu, Fri). They can only take 3 shifts max.");
    console.log("==========================================");

    for (const severity of ["HARD", "MEDIUM", "SOFT"]) {
        const payload = getBasePayload();
        // 4 days total, 08/01 is Monday
        payload.start_date = "2026-08-01"; // Mon
        payload.end_date = "2026-08-04"; // Mon, Tue, Wed, Thu
        
        // Let's make Wed (08/03) unavailable by giving W1 an impossible skill for that day? No, skills are per employee.
        // Let's just limit them to 24 hours (3 days).
        // If consecutiveShifts is active, they should take Mon/Tue/Wed, and drop Thu.
        payload.active_constraints = [
            { "name": "everyShiftPlanned", "severity": "SOFT" },
            { "name": "maxWeeklyHours", "severity": "HARD", "value": 24.0 }, // Can only take 3 shifts
            { "name": "consecutiveShifts", "severity": severity }
        ];

        let res = await runSolver(payload, `consecutiveShifts ${severity}`);
        if (res && res.assignments_by_date) {
            let days = Object.keys(res.assignments_by_date).sort().filter(d => res.assignments_by_date[d].length > 0);
            console.log(`[${severity}] Assigned Days: ${days.join(", ")} | Score: ${res.solver_score}`);
        }
    }
}

async function testPermanentPriority() {
    console.log("\n==========================================");
    console.log("6. Permanent Priority");
    console.log("Scenario: 1 Shift. 1 Permanent Worker ($35/hr), 1 Contract Worker ($20/hr).");
    console.log("==========================================");

    for (const severity of ["HARD", "MEDIUM", "SOFT"]) {
        const payload = getBasePayload();
        payload.existing_users = [
            { "employee_id": "W1_Contract", "name": "Contract", "role": "Cashier", "rate": 20, "rating": 3, "employeeType": "Temporary" },
            { "employee_id": "W2_Perm", "name": "Perm", "role": "Cashier", "rate": 35, "rating": 3, "employeeType": "Permanent" }
        ];
        
        payload.active_constraints = [
            { "name": "everyShiftPlanned", "severity": "HARD" },
            { "name": "wageOptimization", "severity": "SOFT" }, // Wage is naturally SOFT
            { "name": "permanentPriority", "severity": severity }
        ];

        let res = await runSolver(payload, `permanentPriority ${severity}`);
        if (res && res.assignments_by_date) {
            let worker = res.assignments_by_date["2026-08-01"] ? res.assignments_by_date["2026-08-01"][0].employeeName : "NONE";
            console.log(`[${severity}] Assigned Worker: ${worker} | Score: ${res.solver_score}`);
        }
    }
}

async function testEveryShiftPlanned() {
    console.log("\n==========================================");
    console.log("7. Every Shift Planned (vs HARD Wage Penalty)");
    console.log("Scenario: 1 Shift. 1 Employee with $50/hr wage. wageOptimization is set to HARD.");
    console.log("==========================================");

    for (const severity of ["HARD", "MEDIUM", "SOFT"]) {
        const payload = getBasePayload();
        payload.existing_users = [
            { "employee_id": "W1", "name": "Worker 1", "role": "Cashier", "rate": 50, "rating": 3 } // Expensive!
        ];
        
        payload.active_constraints = [
            { "name": "wageOptimization", "severity": "HARD" }, // Wage optimization is set to HARD!
            { "name": "everyShiftPlanned", "severity": severity }
        ];

        let res = await runSolver(payload, `everyShiftPlanned ${severity}`);
        if (res && res.assignments_by_date) {
            let assignedCount = res.assignments_by_date["2026-08-01"] ? 1 : 0;
            console.log(`[${severity}] Assigned? ${assignedCount > 0 ? "YES" : "NO"} | Score: ${res.solver_score}`);
        }
    }
}

async function main() {
    await testMaxWeeklyHours();
    await testMaxDailyHours();
    await testSkillMatch();
    await testNoOverlappingShifts();
    await testConsecutiveShifts();
    await testPermanentPriority();
    await testEveryShiftPlanned();
}

main();
