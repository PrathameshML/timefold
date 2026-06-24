const http = require('http');

const empCount = 500;
const neededPerDay = 300;

// Deterministic employee generation (so we can compare across time limits)
const employees = [];
let permanentCount = 0;
let contractCount = 0;
for (let i = 1; i <= empCount; i++) {
    // Deterministic: odd = Permanent, even = Contract
    const isPermanent = i % 2 === 1;
    if (isPermanent) permanentCount++;
    else contractCount++;

    // Deterministic wage: cycles 10-29 based on employee index
    const wage = 10 + (i % 20);
    // Deterministic rating: cycles 1-5
    const rating = (i % 5) + 1;

    employees.push({
        employee_id: "e" + i,
        name: "Worker " + i,
        role: "General",
        rating: rating,
        rate: wage,
        unit: "hour",
        employeeType: isPermanent ? "Permanent" : "Contract",
        skills: []
    });
}

// Get time limit from command line args (default 300 = 5 min)
const timeLimitSeconds = parseInt(process.argv[2]) || 300;
const unimprovedLimit = Math.max(30, Math.floor(timeLimitSeconds / 5));

const payload = {
    shift_name: "Soft Score Convergence Test",
    start_date: "2026-10-05",
    end_date: "2026-10-11",
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
    time_limit_seconds: timeLimitSeconds,
    unimproved_time_limit_seconds: unimprovedLimit
};

console.log(`===============================================`);
console.log(`Soft Score Convergence Test`);
console.log(`- ${empCount} Employees (${permanentCount} Perm, ${contractCount} Contract)`);
console.log(`- ${neededPerDay}/day × 7 = ${neededPerDay * 7} slots`);
console.log(`- Time limit: ${timeLimitSeconds}s (${(timeLimitSeconds/60).toFixed(0)} min)`);
console.log(`- Unimproved limit: ${unimprovedLimit}s`);
console.log(`- DETERMINISTIC employees (same across runs)`);
console.log(`===============================================`);

const startTime = Date.now();

const options = {
    hostname: 'localhost',
    port: 8083,
    path: '/shifts/assign-v2',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
};

require('fs').writeFileSync('dataset_500_convergence.json', JSON.stringify(payload, null, 2)); const req = http.request(options, (res) => {
    let data = '';
    res.on('data', (chunk) => data += chunk);
    res.on('end', () => {
        const wallTime = ((Date.now() - startTime) / 1000).toFixed(1);
        try {
            const result = JSON.parse(data);
            console.log(`\n=== RESULTS (${timeLimitSeconds}s limit) ===`);
            console.log(`Wall time: ${wallTime}s | Solver time: ${result.solver_time_seconds}s`);
            console.log(`Score: ${result.solver_score}`);
            console.log(`Assignments: ${result.new_assignments_made}`);

            // Extract individual soft scores
            const explanation = result.score_explanation;
            const wageMatch = explanation.match(/-(\d+)soft: constraint \(wageOptimization\)/);
            const permMatch = explanation.match(/-(\d+)soft: constraint \(permanentPriority\)/);
            const ratingMatch = explanation.match(/(\d+)soft: constraint \(maximizeRating\)/);

            console.log(`\n=== SOFT SCORE BREAKDOWN ===`);
            console.log(`  Wage penalty:      -${wageMatch ? wageMatch[1] : '?'}`);
            console.log(`  Permanent penalty: -${permMatch ? permMatch[1] : '?'}`);
            console.log(`  Rating reward:     +${ratingMatch ? ratingMatch[1] : '?'}`);

            // Per-day counts
            if (result.assignments_by_date) {
                console.log(`\n=== PER-DAY ===`);
                for (const [date, assignments] of Object.entries(result.assignments_by_date)) {
                    console.log(`  ${date}: ${assignments.length} assigned`);
                }
            }
        } catch (e) {
            console.log("Parse error:", e.message);
        }
    });
});

req.on('error', (e) => console.error(`Error: ${e.message}`));
req.write(JSON.stringify(payload));
req.end();

