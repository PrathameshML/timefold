const http = require('http');

const empCount = 500;
const neededPerDay = 400; // Total 400 workers needed per day
const days = 7;
const shiftCount = neededPerDay * days; // 2800 slots

const employees = [];
let permanentCount = 0;
let contractCount = 0;
for (let i = 1; i <= empCount; i++) {
    const isPermanent = Math.random() > 0.5;
    if (isPermanent) permanentCount++;
    else contractCount++;
    
    employees.push({
        employee_id: "e" + i,
        name: "Worker " + i,
        role: "General",
        rating: Math.floor(Math.random() * 5) + 1,
        rate: 10 + Math.floor(Math.random() * 20),
        unit: "hour",
        employeeType: isPermanent ? "Permanent" : "Contract",
        skills: []
    });
}

const payload = {
    shift_name: "Massive Week Shift",
    // 2026-08-03 is Monday, 2026-08-09 is Sunday. 
    start_date: "2026-08-03",
    end_date: "2026-08-09",
    start_time: "09:00",
    end_time: "17:00",
    prioritizePermanent: true,
    schedule_breaks: false,
    roles: [
        {
            role_name: "General",
            rating: 1,
            max_workers: neededPerDay,
            required_skills: []
        }
    ],
    existing_users: employees,
    active_constraints: [
        { name: "skillMatch", severity: "HARD", value: 100.0 },
        { name: "noOverlappingShifts", severity: "HARD" },
        { name: "unavailableTimeslot", severity: "HARD" },
        { name: "everyShiftPlanned", severity: "MEDIUM" },
        { name: "wageOptimization", severity: "SOFT" },
        { name: "maxDailyHours", severity: "HARD", value: 8.0 },
        { name: "maxWeeklyHours", severity: "HARD", value: 40.0 },
        { name: "overtimeThreshold", severity: "SOFT", value: 8.0 },
        { name: "breakAfterHours", severity: "HARD", value: 4.0 },
        { name: "consecutiveShifts", severity: "SOFT" },
        { name: "permanentPriority", severity: "SOFT" }
    ],
    time_limit_seconds: 900,
    unimproved_time_limit_seconds: 120
};

console.log(`===============================================`);
console.log(`Running Optimization Intelligence Benchmark:`);
console.log(`- 500 Employees (${permanentCount} Perm, ${contractCount} Contract), 2800 Slots, 15 Min Limit`);
console.log(`- ALL 11 Constraints Enabled`);
console.log(`- permanentPriority explicitly set to SOFT`);
console.log(`- Single ISO Week Boundary (Monday to Sunday)`);
console.log(`===============================================`);

const options = {
    hostname: 'localhost',
    port: 8083,
    path: '/shifts/assign-v2',
    method: 'POST',
    headers: {
        'Content-Type': 'application/json'
    }
};

const req = http.request(options, (res) => {
    let data = '';
    res.on('data', (chunk) => data += chunk);
    res.on('end', () => {
        try {
            const result = JSON.parse(data);
            console.log(`Actual API Time: ${result.solver_time_seconds}s`);
            console.log(`Score: ${result.solver_score}`);
            console.log(`Assignments Made: ${result.new_assignments_made} / ${result.total_possible_assignments}`);
            console.log(`Permanent Priority Matches: ${
                result.score_explanation.split('\n').find(line => line.includes('permanentPriority'))?.trim()
            }`);
            
            console.log("\n=== FULL SCORE EXPLANATION ===");
            console.log(result.score_explanation);
        } catch (e) {
            console.log("Error parsing response:", e);
        }
    });
});

req.on('error', (e) => {
    console.error(`Problem with request: ${e.message}`);
});

req.write(JSON.stringify(payload));
req.end();
