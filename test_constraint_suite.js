const http = require('http');

const URL = 'http://localhost:8083/shifts/assign-v2';

function buildBasePayload(testName, employees, rolesArr, constraints, days = 7) {
    let endDate = new Date('2026-06-01');
    endDate.setDate(endDate.getDate() + (days - 1));
    return {
        "shift_name": testName,
        "start_date": "2026-06-01",
        "end_date": endDate.toISOString().split('T')[0],
        "start_time": "08:00",
        "end_time": "16:00",
        "roles": rolesArr,
        "existing_users": employees,
        "active_constraints": constraints,
        "time_limit_seconds": 3,
        "unimproved_time_limit_seconds": 1
    };
}

function runTest(payload, testLabel) {
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
                try {
                    if (res.statusCode !== 200 && res.statusCode !== 409) {
                        console.error(`HTTP ${res.statusCode}: ${data}`);
                        resolve({ error: `HTTP ${res.statusCode}` });
                        return;
                    }
                    const parsed = JSON.parse(data);
                    if (parsed.status === "error") {
                        console.error(`Solver Error: ${parsed.message}`);
                    }
                    resolve(parsed);
                } catch (e) {
                    console.error("Parse Error:", e);
                    resolve({ error: "Parse Error" });
                }
            });
        });
        req.on('error', (e) => resolve({ error: e.message }));
        req.write(postData);
        req.end();
    });
}

function countAssignments(res) {
    let count = 0;
    if (res && res.assignments_by_date) {
        for (const date in res.assignments_by_date) {
            count += res.assignments_by_date[date].length;
        }
    }
    return count;
}

function countOvertime(res) {
    let otHours = 0;
    if (res && res.assignments_by_date) {
        for (const date in res.assignments_by_date) {
            res.assignments_by_date[date].forEach(a => {
                if (a.ot_hours) otHours += a.ot_hours;
            });
        }
    }
    return otHours;
}

function getEmployeeAssignments(res, empName) {
    let count = 0;
    if (res && res.assignments_by_date) {
        for (const date in res.assignments_by_date) {
            res.assignments_by_date[date].forEach(a => {
                if (a.employeeName === empName) count++;
            });
        }
    }
    return count;
}

async function runTestSuite() {
    console.log("==========================================");
    console.log("TEST 1 - Max Weekly Hours (40h limit)");
    const emps1 = [{ "employee_id": "T1_EMP", "name": "T1_A", "role": "Cashier", "rate": 20, "rating": 5 }];
    const roles1 = [{ "role_name": "Cashier", "max_workers": 1 }];
    for (const sev of ["HARD", "MEDIUM", "SOFT"]) {
        const c1 = [
            { "name": "everyShiftPlanned", "severity": "HARD" },
            { "name": "maxWeeklyHours", "severity": sev, "value": 40.0 }
        ];
        let p = buildBasePayload("T1", emps1, roles1, c1, 6); // 6 days
        let r = await runTest(p, `T1-${sev}`);
        console.log(`[${sev}] Covered: ${countAssignments(r)}/6`);
    }

    console.log("\n==========================================");
    console.log("TEST 2 - Max Daily Hours (8h limit)");
    const emps2 = [{ "employee_id": "T2_EMP", "name": "T2_A", "role": "Cashier", "rate": 20, "rating": 5 }];
    const roles2 = [{ "role_name": "Cashier", "max_workers": 2 }];
    for (const sev of ["HARD", "MEDIUM", "SOFT"]) {
        const c2 = [
            { "name": "everyShiftPlanned", "severity": "HARD" },
            { "name": "maxDailyHours", "severity": sev, "value": 8.0 }
        ];
        let p = buildBasePayload("T2", emps2, roles2, c2, 1);
        p.start_time = "08:00"; p.end_time = "14:00"; // 6 hours. 2 shifts = 12 hours.
        // Wait! The solver only schedules ONE shift per day per employee.
        // Roles "Cashier" with max_workers: 2 just means the SHIFT needs 2 cashiers.
        // The solver assigns 1 employee to that shift. So employee T2_A gets assigned once!
        // To assign an employee to TWO shifts in the SAME day, the payloads need to define two distinct shifts!
        // But our solver takes 1 start_time/end_time per payload. It only schedules 1 shift per day!
        // Max Daily Hours is essentially irrelevant if there's only 1 shift per day.
        // Unless a single shift is > 8 hours.
        p.start_time = "08:00"; p.end_time = "20:00"; // 12 hour shift
        let r = await runTest(p, `T2-${sev}`);
        console.log(`[${sev}] Covered: ${countAssignments(r)}/1`);
    }

    console.log("\n==========================================");
    console.log("TEST 3 - Skill Match");
    const emps3 = [{ "employee_id": "T3_EMP", "name": "T3_A", "role": "Cashier", "rate": 20, "rating": 5, "skills": [] }];
    const roles3 = [{ "role_name": "Cashier", "max_workers": 1, "required_skills": ["Customer Service"] }];
    for (const sev of ["HARD", "MEDIUM", "SOFT"]) {
        const c3 = [
            { "name": "everyShiftPlanned", "severity": "HARD" },
            { "name": "skillMatch", "severity": sev, "value": 100 }
        ];
        let p = buildBasePayload("T3", emps3, roles3, c3, 1);
        let r = await runTest(p, `T3-${sev}`);
        console.log(`[${sev}] Covered: ${countAssignments(r)}/1`);
    }

    console.log("\n==========================================");
    console.log("TEST 4 - No Overlapping Shifts");
    // Since solver only schedules 1 shift per day per payload, we can't test overlapping shifts via payload easily.
    console.log("Skipping - Solver natively enforces 1 shift per employee per day via entity model");

    console.log("\n==========================================");
    console.log("TEST 5 - Consecutive Shifts (A $20 vs B $40)");
    const emps5 = [
        { "employee_id": "T5_A", "name": "T5_A", "role": "Cashier", "rate": 20, "rating": 5 },
        { "employee_id": "T5_B", "name": "T5_B", "role": "Cashier", "rate": 40, "rating": 5 }
    ];
    for (const sev of ["HARD", "MEDIUM", "SOFT"]) {
        const c5 = [
            { "name": "everyShiftPlanned", "severity": "HARD" },
            { "name": "noOverlappingShifts", "severity": "HARD" },
            { "name": "wageOptimization", "severity": "SOFT" },
            { "name": "consecutiveShifts", "severity": sev }
        ];
        let p = buildBasePayload("T5", emps5, roles1, c5, 5); // 5 days: Mon-Fri
        c5.push({ "name": "unavailableTimeslot", "severity": "HARD" });
        emps5[0].unavailable_times = [{ "start_time": "2026-06-03T00:00:00", "end_time": "2026-06-03T23:59:59" }];
        emps5[1].unavailable_times = [{ "start_time": "2026-06-03T00:00:00", "end_time": "2026-06-03T23:59:59" }];
        
        let r = await runTest(p, `T5-${sev}`);
        let aCount = getEmployeeAssignments(r, "T5_A");
        let bCount = getEmployeeAssignments(r, "T5_B");
        console.log(`[${sev}] Employee A ($20): ${aCount} shifts | Employee B ($40): ${bCount} shifts`);
    }

    console.log("\n==========================================");
    console.log("TEST 6 - Permanent Priority");
    const emps6 = [
        { "employee_id": "T6_P", "name": "T6_PermWorker", "role": "Cashier", "rate": 35, "rating": 5, "employeeType": "Permanent" },
        { "employee_id": "T6_C", "name": "T6_ContractWorker", "role": "Cashier", "rate": 20, "rating": 5, "employeeType": "Contract" }
    ];
    for (const sev of ["HARD", "MEDIUM", "SOFT"]) {
        const c6 = [
            { "name": "everyShiftPlanned", "severity": "HARD" },
            { "name": "wageOptimization", "severity": "SOFT" },
            { "name": "permanentPriority", "severity": sev }
        ];
        let p = buildBasePayload("T6", emps6, roles1, c6, 1);
        let r = await runTest(p, `T6-${sev}`);
        let pCount = getEmployeeAssignments(r, "T6_PermWorker");
        let cCount = getEmployeeAssignments(r, "T6_ContractWorker");
        console.log(`[${sev}] Permanent ($35): ${pCount} | Contract ($20): ${cCount}`);
    }

    console.log("\n==========================================");
    console.log("TEST 7 - Every Shift Planned vs Wage Optimization");
    const emps7 = [{ "employee_id": "T7_EMP", "name": "T7_A", "role": "Cashier", "rate": 50, "rating": 5 }];
    for (const sev of ["HARD", "MEDIUM", "SOFT"]) {
        const c7 = [
            { "name": "everyShiftPlanned", "severity": sev },
            { "name": "wageOptimization", "severity": "HARD" }
        ];
        let p = buildBasePayload("T7", emps7, roles1, c7, 1);
        let r = await runTest(p, `T7-${sev}`);
        console.log(`[${sev}] Covered: ${countAssignments(r)}/1`);
    }
}

runTestSuite();
