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
        "end_date": "2026-09-02",
        "start_time": "08:00",
        "end_time": "16:00",
        "roles": [{ "role_name": "Cashier", "max_workers": 2, "required_skills": ["Cashier"] }],
        "existing_users": employees,
        "active_constraints": [
            { "name": "everyShiftPlanned", "severity": "HARD" },
            { "name": "maxWeeklyHours", "severity": "MEDIUM", "value": 40.0 },
        ],
        "time_limit_seconds": 10,
        "unimproved_time_limit_seconds": 5,
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
    const employees = generateEmployees(5);
    const payload = getPayload(employees);
    const res = await runTest(payload);
    
    if (res) {
        console.log(`\n📊 Final Score: ${res.solver_score}`);
        console.log(`⏱️ Solver Time (s): ${res.solver_time_seconds}`);
        console.log(`📦 Entities Planned: ${res.entities_planned}`);
        console.log(res.score_explanation);
    }
}

main();
