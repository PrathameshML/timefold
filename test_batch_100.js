const http = require('http');

const empCount = 100;
const neededPerShift = 35; // 35 for Morning, 35 for Evening = 70 needed per day
const days = 7;

const employees = [];
for (let i = 1; i <= empCount; i++) {
    employees.push({
        employee_id: "e" + i,
        name: "Worker " + i,
        role: "General",
        rating: Math.floor(Math.random() * 5) + 1,
        rate: 10 + Math.floor(Math.random() * 20),
        unit: "hour",
        employeeType: Math.random() > 0.5 ? "Permanent" : "Contract",
        skills: []
    });
}

const payload = {
    shifts: [
        {
            shift_name: "Morning Shift (1 Week)",
            start_date: "2026-10-05", // Monday
            end_date: "2026-10-11",   // Sunday
            start_time: "08:00",
            end_time: "16:00",
            prioritizePermanent: true,
            schedule_breaks: false,
            roles: [
                {
                    role_name: "General",
                    rating: 1,
                    max_workers: neededPerShift,
                    required_skills: []
                }
            ],
            existing_users: employees,
            time_limit_seconds: 60,
            unimproved_time_limit_seconds: 15
        },
        {
            shift_name: "Evening Shift (1 Week)",
            start_date: "2026-10-05", // Monday
            end_date: "2026-10-11",   // Sunday
            start_time: "16:00",
            end_time: "00:00",
            prioritizePermanent: true,
            schedule_breaks: false,
            roles: [
                {
                    role_name: "General",
                    rating: 1,
                    max_workers: neededPerShift,
                    required_skills: []
                }
            ],
            existing_users: employees,
            time_limit_seconds: 60,
            unimproved_time_limit_seconds: 15
        }
    ]
};

console.log(`===============================================`);
console.log(`Running Batch Assignment API on 100 Employees`);
console.log(`Payload contains 2 separate shift requests spanning 1 week`);
console.log(`Each shift needs ${neededPerShift} workers per day (Total 70/day)`);
console.log(`===============================================`);

const options = {
    hostname: 'localhost',
    port: 8083,
    path: '/shifts/batch-assign',
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
            console.log("\n=== OVERALL STATISTICS ===");
            console.log(result.overall_statistics);

            console.log("\n=== INDIVIDUAL SHIFT RESULTS ===");
            result.shift_results.forEach(shift => {
                console.log(`- ${shift.shift_name}: ${shift.status}`);
                console.log(`  Assignments: ${shift.new_assignments_made} / ${shift.total_possible_assignments}`);
                console.log(`  Score: ${shift.solver_score}`);
            });
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
