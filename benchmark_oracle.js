const http = require('http');

const URL = 'http://localhost:8083/shifts/assign-v2';

const employees = [
    { "employee_id": "OracleE1", "name": "Oracle Worker 1", "role": "Cashier", "rate": 15, "rating": 5, "employeeType": "Permanent", "skills": ["Cashier"] },
    { "employee_id": "OracleE2", "name": "Oracle Worker 2", "role": "Cashier", "rate": 20, "rating": 4, "employeeType": "Permanent", "skills": ["Cashier"] },
    { "employee_id": "OracleE3", "name": "Oracle Worker 3", "role": "Cashier", "rate": 25, "rating": 3, "employeeType": "Permanent", "skills": ["Cashier"] },
    { "employee_id": "OracleE4", "name": "Oracle Worker 4", "role": "Cashier", "rate": 30, "rating": 2, "employeeType": "Permanent", "skills": ["Cashier"] },
    { "employee_id": "OracleE5", "name": "Oracle Worker 5", "role": "Cashier", "rate": 35, "rating": 1, "employeeType": "Permanent", "skills": ["Cashier"] }
];

const payload = {
    "shift_name": "Oracle Shift",
    "start_date": "2026-09-01",
    "end_date": "2026-09-05", // 5 days = 5 shifts
    "start_time": "08:00",
    "end_time": "16:00",      // 8 hours per shift
    "roles": [{ "role_name": "Cashier", "max_workers": 1, "required_skills": ["Cashier"] }],
    "existing_users": employees,
    "active_constraints": [
        { "name": "everyShiftPlanned", "severity": "HARD" },
        { "name": "maxWeeklyHours", "severity": "HARD", "value": 8.0 }, // Forces each employee to only take 1 shift
        { "name": "wageOptimization", "severity": "SOFT" },
        { "name": "maximizeRating", "severity": "SOFT", "value": 100.0 }
    ],
    "time_limit_seconds": 5, // Tiny dataset, should solve instantly
    "unimproved_time_limit_seconds": 2,
    "schedule_breaks": false
};

async function runOracle() {
    console.log("Running Oracle Benchmark...");
    
    // Mathematically perfect score based on V2 constraints:
    // Average Wage = (15+20+25+30+35)/5 = 25
    // Wage Penalty = Sum(wage/25 * 1000) = (0.6 + 0.8 + 1.0 + 1.2 + 1.4) * 1000 = 5000 => -5000soft
    // Rating Reward = Sum(rating * 100) = (5+4+3+2+1) * 100 = 1500 => +1500soft
    // Total Soft = -3500
    // Total Hard = 0
    console.log("Expected Mathematically Perfect Score: 0hard/0medium/-3500soft");

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
                const result = JSON.parse(data);
                console.log(`\nTimefold Found Score: ${result.solver_score}`);
                console.log(`Shifts Assigned: ${result.new_assignments_made} / ${result.total_possible_assignments}`);
                if (result.solver_score === "0hard/0medium/-3500soft") {
                    console.log("✅ ORACLE BENCHMARK PASSED! Exact optimal score found.");
                } else {
                    console.log("❌ ORACLE BENCHMARK FAILED! Did not find the mathematically perfect score.");
                    console.log(result.score_explanation);
                }
            });
        });
        req.on('error', (e) => {
            console.error(e);
            resolve(null);
        });
        req.write(postData);
        req.end();
    });
}

runOracle();
