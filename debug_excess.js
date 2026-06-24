const fs = require('fs');

async function debugExcess() {
    console.log("\n=== 9. Excess Worker Test (DEBUG) ===");
    const excess_users = [];
    for(let i=1; i<=50; i++) {
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
    
    const response = await fetch("http://localhost:8083/shifts/assign-v2", {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(excess_payload)
    });
    
    const res = await response.json();
    console.log(`Score: ${res.solver_score}`);
    console.log(`Assignments Made: ${res.new_assignments_made}`);
    
    console.log("Assignments:");
    for (const date in res.assignments_by_date) {
        for (const empStr of res.assignments_by_date[date]) {
            console.log(`  ${date}: ${empStr}`);
        }
    }
    
    console.log("\nScore Explanation:");
    console.log(res.score_explanation);
}

debugExcess();
