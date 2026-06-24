const http = require('http');

const URL = 'http://localhost:8083/shifts/assign-v2';

const activeConstraints = [
    { "name": "skillMatch", "severity": "HARD", "value": 50.0 },
    { "name": "noOverlappingShifts", "severity": "HARD" },
    { "name": "unavailableTimeslot", "severity": "HARD" },
    { "name": "everyShiftPlanned", "severity": "MEDIUM" },
    { "name": "wageOptimization", "severity": "SOFT" },
    { "name": "maxDailyHours", "severity": "MEDIUM", "value": 8.0 },
    { "name": "maxWeeklyHours", "severity": "MEDIUM", "value": 40.0 },
    { "name": "overtimeThreshold", "severity": "SOFT", "value": 8.0 },
    { "name": "breakAfterHours", "severity": "HARD", "value": 4.0 },
    { "name": "consecutiveShifts", "severity": "HARD" },
    { "name": "permanentPriority", "severity": "SOFT" }
];

function generatePayload(multiplier) {
    const numEmployees = 500;
    const employees = [];
    const roles = ["Cashier", "Manager", "Stock Clerk"];

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

    // Target 300 assignments per day across 7 days = 2100 required assignments.
    // We achieve this via role limits.
    const rolesArr = [];
    roles.forEach(r => {
        rolesArr.push({ "role_name": r, "max_workers": 100 }); // 100 per role * 3 roles = 300 workers per shift
    });

    // Add maximizeRating with the specific multiplier
    const constraints = [...activeConstraints, { "name": "maximizeRating", "severity": "SOFT", "value": multiplier }];

    return {
        "shift_name": "Weekly Schedule",
        "start_date": "2026-06-01",
        "end_date": "2026-06-07",
        "start_time": "08:00",
        "end_time": "16:00",
        "roles": rolesArr,
        "existing_users": employees,
        "active_constraints": constraints,
        "time_limit_seconds": 60,
        "unimproved_time_limit_seconds": 20
    };
}

function runBenchmark(multiplier) {
    return new Promise((resolve) => {
        const payload = generatePayload(multiplier);
        const postData = JSON.stringify(payload);

        console.log(`\nStarting Test [Multiplier: ${multiplier}]...`);

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
                if (res.statusCode !== 200 && res.statusCode !== 409) {
                    console.log(`Error Status ${res.statusCode}: ${data.substring(0, 100)}...`);
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

                const avgRating = totalAssignments > 0 ? (totalRating / totalAssignments).toFixed(2) : "0.00";
                const avgWage = totalAssignments > 0 ? ((totalWage / totalAssignments) / 8).toFixed(2) : "0.00";
                
                console.log(`| ${multiplier} | ${totalAssignments} / 2100 | ${avgRating} / 5 | $${avgWage} | ${result.solver_score || "N/A"} |`);
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
    console.log("Running Maximize Rating Curve Benchmark (2100 Required Assignments / 500 Employees)");
    console.log("| Multiplier | Coverage | Avg Rating | Avg Hourly Wage | Solver Score |");
    console.log("| :--- | :--- | :--- | :--- | :--- |");
    
    const multipliers = [0, 25, 50, 100, 200];
    for (const m of multipliers) {
        await runBenchmark(m);
    }
    console.log('\nAll tests complete!');
}

main();
