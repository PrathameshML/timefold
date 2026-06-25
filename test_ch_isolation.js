const http = require('http');

const URL = 'http://localhost:8083/shifts/assign-v2';

function generateEmployees(count) {
    const employees = [];
    for (let i = 1; i <= count; i++) {
        employees.push({
            "employee_id": `E${i}`,
            "name": `Worker ${i}`,
            "role": "Cashier",
            "rate": 15,
            "rating": 3,
            "employeeType": "Permanent",
            "skills": ["Cashier"]
        });
    }
    return employees;
}

function getPayload(employees) {
    return {
        "shift_name": "Enterprise Shift",
        "start_date": "2026-09-01",
        "end_date": "2026-09-07",
        "start_time": "08:00",
        "end_time": "16:00",
        "roles": [{ "role_name": "Cashier", "max_workers": 300, "required_skills": ["Cashier"] }],
        "existing_users": employees,
        "active_constraints": [
            { "name": "everyShiftPlanned", "severity": "HARD" }
        ], // ONLY ONE CONSTRAINT to test CH behavior
        "time_limit_seconds": 60,
        "unimproved_time_limit_seconds": 0, // KILL LOCAL SEARCH INSTANTLY
        "schedule_breaks": false
    };
}

async function runTest(payload) {
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
                resolve(JSON.parse(data));
            });
        });
        req.write(postData);
        req.end();
    });
}

async function main() {
    console.log("Running Construction Heuristic Isolation Test...");
    const employees = generateEmployees(500);
    const payload = getPayload(employees);
    const res = await runTest(payload);
    
    if (res) {
        console.log(`\n📊 Final Score: ${res.solver_score}`);
        let totalAssigned = 0;
        if (res.assignments_by_date) {
            for (let date in res.assignments_by_date) {
                totalAssigned += res.assignments_by_date[date].length;
            }
        }
        console.log(`✅ Total Shifts Assigned: ${totalAssigned} / 2100`);
        console.log(`⏱️ Solver Time (s): ${res.solver_time_seconds}`);
        console.log(res.score_explanation);
    }
}

main();
