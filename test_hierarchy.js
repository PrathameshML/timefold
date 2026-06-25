const http = require('http');

const URL = 'http://localhost:8083/shifts/assign-v2';

function getPayload() {
    return {
        "shift_name": "Enterprise Shift",
        "start_date": "2026-09-01",
        "end_date": "2026-09-06", // 6 days
        "start_time": "08:00",
        "end_time": "16:00", // 8 hours per day = 48 hours total
        "roles": [{ "role_name": "Cashier", "max_workers": 1, "required_skills": ["Cashier"] }],
        "existing_users": [
            {
                "employee_id": "E1",
                "name": "Worker 1",
                "role": "Cashier",
                "rate": 15,
                "rating": 3,
                "employeeType": "Permanent",
                "skills": ["Cashier"]
            }
        ],
        "active_constraints": [
            { "name": "everyShiftPlanned", "severity": "HARD" },
            { "name": "maxWeeklyHours", "severity": "MEDIUM", "value": 40.0 }
        ],
        "time_limit_seconds": 10,
        "unimproved_time_limit_seconds": 0,
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
    console.log("Running Single Employee 6-Day Test...");
    const payload = getPayload();
    const res = await runTest(payload);
    
    if (res) {
        console.log(`\n📊 Final Score: ${res.solver_score}`);
        
        let totalAssigned = 0;
        if (res.assignments_by_date) {
            for (let date in res.assignments_by_date) {
                totalAssigned += res.assignments_by_date[date].length;
            }
        }
        
        console.log(`✅ Total Shifts Assigned: ${totalAssigned} / 6`);
        console.log(`⏱️ Solver Time (s): ${res.solver_time_seconds}`);
        console.log(res.score_explanation);
    }
}

main();
