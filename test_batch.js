const http = require('http');

const payload = {
    shifts: [
        {
            shift_name: "Morning Shift (Batch 1)",
            start_date: "2026-09-01",
            end_date: "2026-09-01",
            start_time: "08:00",
            end_time: "12:00",
            prioritizePermanent: true,
            schedule_breaks: false,
            roles: [
                {
                    role_name: "General",
                    rating: 1,
                    max_workers: 2,
                    required_skills: []
                }
            ],
            existing_users: [
                { employee_id: "e1", name: "Worker 1", role: "General", rating: 5, rate: 15.0, unit: "hour", employeeType: "Permanent", skills: [] },
                { employee_id: "e2", name: "Worker 2", role: "General", rating: 4, rate: 16.0, unit: "hour", employeeType: "Contract", skills: [] }
            ]
        },
        {
            shift_name: "Afternoon Shift (Batch 2)",
            start_date: "2026-09-01",
            end_date: "2026-09-01",
            start_time: "13:00",
            end_time: "17:00",
            prioritizePermanent: true,
            schedule_breaks: false,
            roles: [
                {
                    role_name: "General",
                    rating: 1,
                    max_workers: 1,
                    required_skills: []
                }
            ],
            existing_users: [
                { employee_id: "e3", name: "Worker 3", role: "General", rating: 5, rate: 18.0, unit: "hour", employeeType: "Permanent", skills: [] }
            ]
        }
    ]
};

console.log(`===============================================`);
console.log(`Running Batch Assignment API Benchmark`);
console.log(`Payload contains ${payload.shifts.length} separate shift requests`);
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
                console.log(`- ${shift.shift_name}: ${shift.status} (${shift.new_assignments_made} assignments)`);
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
