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
    // Edge Case 4b: Max Weekly Hours Exhaustion (Within 1 ISO Week)
    // Mon Aug 4 2036 to Sun Aug 10 2036.
    const ec4b_payload = {
        shift_name: "Medium Shift",
        start_date: "2036-08-04",
        end_date: "2036-08-10",
        start_time: "08:00",
        end_time: "14:00", // 6 hours
        prioritize_permanent: true,
        roles: [{ role_name: "Guard", rating: 1, max_workers: 1, required_skills: [] }],
        existing_users: [{ employee_id: "ec4_1", name: "Lone Guard", role: "Guard", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: [] }]
    };

    await runScenario("Edge Case 4b: Max Weekly Hours (Mon-Sun, 6hx7d = 42h)", ec4b_payload);
}

runEdgeCases();
