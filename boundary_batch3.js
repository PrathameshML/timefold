const fs = require('fs');

async function runScenario(name, payload) {
    console.log(`\n===============================================`);
    console.log(`Running: ${name}`);
    console.log(`===============================================`);
    try {
        const response = await fetch("http://localhost:8083/shifts/assign-v2", {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        
        if (!response.ok) {
            const text = await response.text();
            console.log(`ERROR: ${response.status} ${response.statusText}\n${text}`);
            return;
        }
        
        const res = await response.json();
        console.log(`Score: ${res.solver_score}`);
        console.log(`Assignments Made: ${res.new_assignments_made}`);
        
        if (res.new_assignments_made > 0) {
            let assignedIds = [];
            for (const date in res.assignments_by_date) {
                // array of employee ID strings in V2
                assignedIds = assignedIds.concat(res.assignments_by_date[date]);
            }
            // Unique assigned IDs
            assignedIds = [...new Set(assignedIds)];
            console.log(`Unique Workers Assigned: ${assignedIds.join(", ")}`);
        }
        
        if (res.score_explanation) {
            const constraintLines = res.score_explanation.split('\n').filter(l => l.includes('constraint')).slice(0, 10);
            constraintLines.forEach(l => console.log(l));
        }
        return res;
    } catch (e) {
        console.log(`ERROR: ${e.message}`);
    }
}

async function runBatch3() {
    console.log("\n=== 8. Worker Shortage Test ===");
    // Need 20, have 5.
    const short_users = [];
    for(let i=1; i<=5; i++) {
        short_users.push({ employee_id: `short_${i}`, name: `Worker ${i}`, role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["grill"] });
    }
    const short_payload = {
        shift_name: "Morning", start_date: "2038-08-01", end_date: "2038-08-01", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 20, required_skills: ["grill"] }],
        existing_users: short_users
    };
    await runScenario("Worker Shortage Test (5 for 20)", short_payload);

    console.log("\n=== 9. Excess Worker Test ===");
    // Need 5, have 50.
    const excess_users = [];
    for(let i=1; i<=50; i++) {
        // First 5 are 5-star, $10 (best)
        // Others are 1-star, $50 (worst)
        const isBest = i <= 5;
        excess_users.push({ 
            employee_id: `ex_${i}`, name: `Worker ${i}`, role: "Cook", 
            rate: isBest ? 10.0 : 50.0, 
            unit: "hour", 
            rating: isBest ? 5 : 1, 
            employeeType: "Permanent", skills: ["grill"] 
        });
    }
    const excess_payload = {
        shift_name: "Morning", start_date: "2038-09-01", end_date: "2038-09-01", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 5, required_skills: ["grill"] }],
        existing_users: excess_users
    };
    await runScenario("Excess Worker Test (50 for 5)", excess_payload);

    console.log("\n=== 10. Multi-Day Fatigue Test ===");
    // 14 days, 1 worker.
    // 14 * 8 = 112 hours.
    const fatigue_users = [
        { employee_id: "fatigue_1", name: "Tired Worker", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["grill"] }
    ];
    // Dates chosen: Monday 2038-10-04 to Sunday 2038-10-17.
    // ISO Week 40 and 41.
    const fatigue_payload = {
        shift_name: "Morning", start_date: "2038-10-04", end_date: "2038-10-17", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 1, required_skills: ["grill"] }],
        existing_users: fatigue_users
    };
    await runScenario("14-Day Fatigue Test", fatigue_payload);

}

runBatch3();
