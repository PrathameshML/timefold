const http = require('http');

const empCount = 500;
const neededPerDay = 300;

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
    shift_name: "V2 Full Constraint Benchmark",
    start_date: "2026-10-05", // Monday
    end_date: "2026-10-11",   // Sunday — single ISO week
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
    // No active_constraints — use all 11 DB defaults
    time_limit_seconds: 300,
    unimproved_time_limit_seconds: 60
};

console.log(`===============================================`);
console.log(`V2 Full Constraint Benchmark`);
console.log(`- ${empCount} Employees (${permanentCount} Perm, ${contractCount} Contract)`);
console.log(`- ${neededPerDay} needed/day × 7 days = ${neededPerDay * 7} total slots`);
console.log(`- Theoretical max: ${empCount} × 5 shifts = ${empCount * 5}`);
console.log(`- ALL 11 DB constraints enabled (no override)`);
console.log(`- Time limit: 5 minutes`);
console.log(`- Single ISO week (Mon-Sun)`);
console.log(`===============================================`);

const startTime = Date.now();

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
        const wallTime = ((Date.now() - startTime) / 1000).toFixed(1);
        try {
            const result = JSON.parse(data);
            console.log(`\n=== RESULTS ===`);
            console.log(`Wall clock time: ${wallTime}s`);
            console.log(`Solver time: ${result.solver_time_seconds}s`);
            console.log(`Score: ${result.solver_score}`);
            console.log(`Assignments: ${result.new_assignments_made} / ${result.total_possible_assignments}`);
            console.log(`Entities planned: ${result.entities_planned}`);

            // Count assignments per day
            if (result.assignments_by_date) {
                console.log(`\n=== PER-DAY BREAKDOWN ===`);
                let totalPerm = 0, totalContract = 0;
                for (const [date, assignments] of Object.entries(result.assignments_by_date)) {
                    const perm = assignments.filter(a => a.employeeType === 'Permanent').length;
                    const contract = assignments.filter(a => a.employeeType === 'Contract').length;
                    totalPerm += perm;
                    totalContract += contract;
                    console.log(`  ${date}: ${assignments.length} assigned (${perm} Perm, ${contract} Contract)`);
                }
                console.log(`  TOTAL: ${totalPerm} Permanent, ${totalContract} Contract`);
            }

            console.log(`\n=== ACTIVE CONSTRAINTS ===`);
            if (result.active_constraints) {
                result.active_constraints.forEach(c => {
                    console.log(`  ${c.name}: ${c.severity}${c.value ? ' (value=' + c.value + ')' : ''}`);
                });
            }

            console.log(`\n=== SCORE EXPLANATION ===`);
            console.log(result.score_explanation);
        } catch (e) {
            console.log("Error parsing response:", e.message);
            console.log("Raw (first 500 chars):", data.substring(0, 500));
        }
    });
});

req.on('error', (e) => {
    console.error(`Problem with request: ${e.message}`);
});

req.write(JSON.stringify(payload));
req.end();
