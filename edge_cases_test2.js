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
        console.log(`Score Explanation:`);
        
        if (res.score_explanation) {
            const constraintLines = res.score_explanation.split('\n').filter(l => l.includes('constraint')).slice(0, 20);
            constraintLines.forEach(l => console.log(l));
        }
    } catch (e) {
        console.log(`ERROR: ${e.message}`);
    }
}

async function runEdgeCases() {
    // Edge Case 4: Max Weekly Hours Exhaustion
    // 1 Employee, 7 days of 6-hour shifts. Total = 42 hours.
    // Daily limit is 8h (so they pass Daily limit).
    // Weekly limit is 40h (so 7th day fails Weekly limit).
    // The AI should assign 6 days (36h) and leave 1 day understaffed.
    const ec4_payload = {
        shift_name: "Medium Shift",
        start_date: "2036-08-01",
        end_date: "2036-08-07",
        start_time: "08:00",
        end_time: "14:00", // 6 hours
        prioritize_permanent: true,
        roles: [{ role_name: "Guard", rating: 1, max_workers: 1, required_skills: [] }],
        existing_users: [{ employee_id: "ec4_1", name: "Lone Guard", role: "Guard", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: [] }]
    };

    await runScenario("Edge Case 4: Max Weekly Hours (6hx7d = 42h)", ec4_payload);
}

runEdgeCases();
