const fs = require('fs');
const http = require('http');

const URL = 'http://localhost:8083/shifts/assign-v2';

const activeConstraints = [
    { "constraintId": 1, "constraintName": "skillMatch", "enabled": true, "severity": "HARD", "parameterValue": 50.0, "parameterName": "skillMatchPercentage" },
    { "constraintId": 2, "constraintName": "noOverlappingShifts", "enabled": true, "severity": "HARD" },
    { "constraintId": 3, "constraintName": "unavailableTimeslot", "enabled": true, "severity": "HARD" },
    { "constraintId": 4, "constraintName": "everyShiftPlanned", "enabled": true, "severity": "MEDIUM" },
    { "constraintId": 5, "constraintName": "wageOptimization", "enabled": true, "severity": "SOFT" },
    { "constraintId": 6, "constraintName": "maxDailyHours", "enabled": true, "severity": "MEDIUM", "parameterValue": 8.0, "parameterName": "maxHoursPerDay" },
    { "constraintId": 7, "constraintName": "maxWeeklyHours", "enabled": true, "severity": "MEDIUM", "parameterValue": 40.0, "parameterName": "maxHoursPerWeek" },
    { "constraintId": 8, "constraintName": "overtimeThreshold", "enabled": true, "severity": "SOFT", "parameterValue": 8.0, "parameterName": "otThresholdHours" },
    { "constraintId": 9, "constraintName": "breakAfterHours", "enabled": true, "severity": "HARD", "parameterValue": 4.0, "parameterName": "breakAfterHours", "parameterValue2": 30.0, "parameterName2": "breakDurationMinutes" },
    { "constraintId": 10, "constraintName": "consecutiveShifts", "enabled": true, "severity": "HARD" },
    { "constraintId": 11, "constraintName": "permanentPriority", "enabled": true, "severity": "SOFT" },
    { "constraintId": 12, "constraintName": "maximizeRating", "enabled": true, "severity": "SOFT", "parameterValue": 100.0, "parameterName": "ratingMultiplier" }
];

function generatePayload(numEmployees) {
    const employees = [];
    const roles = ["Cashier", "Manager", "Stock Clerk"];
    const shiftDates = ["2026-06-01", "2026-06-02", "2026-06-03", "2026-06-04", "2026-06-05", "2026-06-06", "2026-06-07"];
    const shifts = [
        { "name": "Morning Shift", "start_time": "08:00:00", "end_time": "16:00:00", "required_skills": ["Customer Service"] },
        { "name": "Evening Shift", "start_time": "16:00:00", "end_time": "00:00:00", "required_skills": [] }
    ];

    // Generate employees
    for (let i = 1; i <= numEmployees; i++) {
        const rating = Math.floor(Math.random() * 5) + 1;
        const wage = 10 + Math.floor(Math.random() * 40); // 10 to 50
        const role = roles[i % roles.length];
        const isPermanent = Math.random() > 0.5;
        const hasSkill = Math.random() > 0.5;

        employees.push({
            "employee_id": `EMP${i}`,
            "name": `Worker ${i}`,
            "role": role,
            "employeeType": isPermanent ? "Permanent" : "Contract",
            "gender": i % 2 === 0 ? "Male" : "Female",
            "rate": wage,
            "rating": rating,
            "skills": hasSkill ? ["Customer Service"] : [],
            "unavailable_times": []
        });
    }

    // Role limits per shift: enough to employ about 80% of the workforce
    // 80% of numEmployees / 2 shifts per day = workers per shift
    const workersPerShift = Math.max(1, Math.floor((numEmployees * 0.8) / 2));
    // assignShiftsV2 expects "name", "severity", "value"
    const mappedConstraints = activeConstraints.map(c => ({
        "name": c.constraintName,
        "severity": c.severity,
        "value": c.parameterValue
    }));

    const rolesArr = [];
    roles.forEach(r => {
        rolesArr.push({ "role_name": r, "max_workers": Math.ceil(workersPerShift / roles.length) });
    });

    return {
        "shift_name": "Weekly Schedule",
        "start_date": "2026-06-01",
        "end_date": "2026-06-07",
        "start_time": "08:00",
        "end_time": "16:00",
        "roles": rolesArr,
        "existing_users": employees,
        "active_constraints": mappedConstraints,
        "time_limit_seconds": numEmployees >= 300 ? 120 : (numEmployees >= 100 ? 60 : 30),
        "unimproved_time_limit_seconds": 20
    };
}

function runBenchmark(numEmployees) {
    return new Promise((resolve, reject) => {
        const payload = generatePayload(numEmployees);
        const postData = JSON.stringify(payload);

        console.log(`\n===========================================`);
        console.log(`Testing ${numEmployees} Employees`);
        console.log(`Sending request, timeout set to ${payload.time_limit_seconds}s...`);

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
                    console.log(`Status ${res.statusCode}: ${data.substring(0, 500)}`);
                    resolve();
                    return;
                }
                const result = JSON.parse(data);
                
                let totalAssignments = 0;
                let totalWage = 0;
                let totalRating = 0;

                if (result.assignments_by_date) {
                    for (const date in result.assignments_by_date) {
                        result.assignments_by_date[date].forEach(a => {
                            totalAssignments++;
                            totalWage += (a.wage * (a.regular_hours + (a.ot_hours || 0)));
                            totalRating += a.rating;
                        });
                    }
                }

                console.log(`✅ Complete!`);
                console.log(`- Solver Score: ${result.solver_score}`);
                console.log(`- Time Taken: ${result.solver_time_seconds}s`);
                console.log(`- Total Assignments: ${totalAssignments}`);
                if (totalAssignments > 0) {
                    console.log(`- Avg Rating: ${(totalRating / totalAssignments).toFixed(2)} / 5`);
                    console.log(`- Avg Hourly Wage: $${((totalWage / totalAssignments) / 8).toFixed(2)}`);
                }
                
                // Parse score: e.g., "0hard/-100medium/-1234soft"
                const scoreStr = result.solver_score || "";
                const hardMatch = scoreStr.match(/([-\d]+)hard/);
                const mediumMatch = scoreStr.match(/([-\d]+)medium/);
                
                let hardScore = hardMatch ? parseInt(hardMatch[1]) : 0;
                let mediumScore = mediumMatch ? parseInt(mediumMatch[1]) : 0;

                console.log(`- Constraints Status:`);
                if (hardScore === 0) {
                    console.log(`  [PASS] All HARD constraints satisfied!`);
                } else {
                    console.log(`  [FAIL] HARD constraints violated: ${hardScore}`);
                }

                if (mediumScore === 0) {
                    console.log(`  [PASS] All EVERY_SHIFT_PLANNED / MAX_HOURS satisfied!`);
                } else {
                    console.log(`  [WARN] Medium constraints violated (likely unassigned shifts due to max hours): ${mediumScore}`);
                }

                resolve();
            });
        });

        req.on('error', (e) => {
            console.error(`Request failed: ${e.message}`);
            resolve();
        });

        req.write(postData);
        req.end();
    });
}

async function main() {
    const scales = [50, 100, 200, 300, 500];
    for (const size of scales) {
        await runBenchmark(size);
    }
    console.log('\nAll benchmarks complete!');
}

main();
