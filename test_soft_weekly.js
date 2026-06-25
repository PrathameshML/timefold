const http = require('http');

const URL = 'http://localhost:8083/shifts/assign-v2';

function generateEmployees(count) {
    const employees = [];
    const skills = ["Cashier", "Stocking", "Customer Service"];
    for (let i = 1; i <= count; i++) {
        const wage = 15 + Math.floor(Math.random() * 21);
        const rating = 3;
        const type = "Permanent";
        const skill = skills[Math.floor(Math.random() * skills.length)];
        employees.push({
            "employee_id": `E${i}`,
            "name": `Worker ${i}`,
            "role": "Cashier",
            "rate": wage,
            "rating": rating,
            "employeeType": type,
            "skills": [skill]
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
            { "name": "everyShiftPlanned", "severity": "HARD" },
            { "name": "noOverlappingShifts", "severity": "HARD" },
            { "name": "maxDailyHours", "severity": "MEDIUM", "value": 8.0 },
            { "name": "maxWeeklyHours", "severity": "SOFT", "value": 40.0 }, // CHANGED TO SOFT!
            { "name": "unavailableTimeslot", "severity": "HARD" },
            { "name": "skillMatch", "severity": "SOFT" },
            { "name": "breakAfterHours", "severity": "HARD", "value": 4.0 },
            { "name": "overtimeThreshold", "severity": "SOFT", "value": 8.0 },
            { "name": "consecutiveShifts", "severity": "SOFT" },
            { "name": "permanentPriority", "severity": "SOFT" },
            { "name": "wageOptimization", "severity": "SOFT" },
            { "name": "maximizeRating", "severity": "SOFT", "value": 100.0 }
        ],
        "time_limit_seconds": 60,
        "unimproved_time_limit_seconds": 15,
        "schedule_breaks": true
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
    console.log("Running Stress Test with maxWeeklyHours = SOFT...");
    const employees = generateEmployees(500);
    const payload = getPayload(employees);
    const res = await runTest(payload);
    
    if (res) {
        console.log(`\n📊 Final Score: ${res.solver_score}`);
        
        let totalAssigned = 0;
        for (let date in res.assignments_by_date) {
            totalAssigned += res.assignments_by_date[date].length;
        }
        
        console.log(`✅ Total Shifts Assigned: ${totalAssigned} / 2100`);
    }
}

main();
