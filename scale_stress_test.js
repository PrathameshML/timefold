const http = require('http');

const URL = 'http://localhost:8083/shifts/assign-v2';

function generateEmployees(count) {
    const employees = [];
    const skills = ["Cashier", "Stocking", "Customer Service"];
    
    for (let i = 1; i <= count; i++) {
        // Random wage between 15 and 35
        const wage = 15 + Math.floor(Math.random() * 21);
        
        // Random rating 1-5, biased slightly towards 3
        const rand = Math.random();
        let rating = 3;
        if (rand < 0.1) rating = 1;
        else if (rand < 0.25) rating = 2;
        else if (rand < 0.75) rating = 3;
        else if (rand < 0.9) rating = 4;
        else rating = 5;
        
        // Random type
        const type = Math.random() > 0.3 ? "Permanent" : "Temporary";
        
        // Random skill
        const skill = skills[Math.floor(Math.random() * skills.length)];

        employees.push({
            "employee_id": `E${i}`,
            "name": `Worker ${i}`,
            "role": "Cashier", // all are cashiers for this test
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
        "start_date": "2026-09-01", // Fresh month to avoid pinned collisions
        "end_date": "2026-09-07", // 7 days
        "start_time": "08:00",
        "end_time": "16:00", // 8 hour shift
        "roles": [{ 
            "role_name": "Cashier", 
            "max_workers": 300, // 300 workers needed per day
            "required_skills": ["Cashier"] // Must have cashier skill
        }],
        "existing_users": employees,
        "active_constraints": [
            { "name": "everyShiftPlanned", "severity": "HARD" },
            { "name": "noOverlappingShifts", "severity": "HARD" },
            { "name": "maxDailyHours", "severity": "MEDIUM", "value": 8.0 },
            { "name": "maxWeeklyHours", "severity": "MEDIUM", "value": 40.0 },
            { "name": "unavailableTimeslot", "severity": "HARD" },
            { "name": "skillMatch", "severity": "SOFT" },
            { "name": "breakAfterHours", "severity": "HARD", "value": 4.0 },
            { "name": "overtimeThreshold", "severity": "SOFT", "value": 8.0 },
            { "name": "consecutiveShifts", "severity": "SOFT" },
            { "name": "permanentPriority", "severity": "SOFT" },
            { "name": "wageOptimization", "severity": "SOFT" },
            { "name": "maximizeRating", "severity": "SOFT", "value": 100.0 }
        ],
        "time_limit_seconds": 60, // 60 seconds stress test
        "unimproved_time_limit_seconds": 15,
        "schedule_breaks": true
    };
}

async function runTest(payload) {
    return new Promise((resolve) => {
        const postData = JSON.stringify(payload);
        const startTime = Date.now();

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
                const duration = ((Date.now() - startTime) / 1000).toFixed(2);
                if (res.statusCode !== 200) {
                    console.log(`[Error ${res.statusCode}] Time: ${duration}s | Response: ${data}`);
                    resolve(null);
                    return;
                }
                const result = JSON.parse(data);
                console.log(`\n✅ Solved in ${duration}s`);
                resolve(result);
            });
        });

        req.on('error', (e) => {
            console.error(`Request failed: ${e.message}`);
            resolve(null);
        });

        req.write(postData);
        req.end();
    });
}

async function main() {
    console.log("==========================================");
    console.log("Enterprise Scale Stress Test");
    console.log("Scenario: 500 Employees, 300 shifts/day for 7 days (2100 total shifts)");
    console.log("Constraints: ALL 12 CONSTRAINTS ACTIVE");
    console.log("==========================================");

    const employees = generateEmployees(500);
    const payload = getPayload(employees);

    console.log(`Generated ${employees.length} employees.`);
    console.log(`Sending payload to V2 engine (allow up to 60s for solver)...`);
    
    const res = await runTest(payload);
    
    if (res) {
        console.log(`\n📊 Final Score: ${res.solver_score}`);
        
        let totalAssigned = 0;
        let totalWages = 0;
        let totalRatings = 0;
        
        for (let date in res.assignments_by_date) {
            const shifts = res.assignments_by_date[date];
            totalAssigned += shifts.length;
            
            shifts.forEach(s => {
                const emp = employees.find(e => e.employee_id === s.employeeId);
                if (emp) {
                    totalWages += emp.rate;
                    totalRatings += emp.rating;
                }
            });
        }
        
        console.log(`\n✅ Total Shifts Assigned: ${totalAssigned} / 2100`);
        if (totalAssigned > 0) {
            console.log(`💰 Average Wage: $${(totalWages / totalAssigned).toFixed(2)}/hr`);
            console.log(`⭐ Average Rating: ${(totalRatings / totalAssigned).toFixed(2)}`);
        }
        
        if (res.unassigned_shifts && res.unassigned_shifts.length > 0) {
            console.log(`\n❌ Unassigned Shifts: ${res.unassigned_shifts.length}`);
            console.log(`Reason sample: ${res.unassigned_shifts[0].reason}`);
        }
    }
}

main();
