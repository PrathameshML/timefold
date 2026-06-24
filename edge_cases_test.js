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
    // Edge Case 1: Max Weekly Hours (40h limit vs 84h requirement)
    // 1 Employee, 7 days of 12-hour shifts. 
    // The AI should stop assigning them after ~40 hours (3.3 shifts) because maxWeeklyHours is HARD.
    const ec1_payload = {
        shift_name: "Long Shift",
        start_date: "2036-06-01",
        end_date: "2036-06-07",
        start_time: "08:00",
        end_time: "20:00", // 12 hours
        prioritize_permanent: true,
        roles: [{ role_name: "Guard", rating: 1, max_workers: 1, required_skills: [] }],
        existing_users: [{ employee_id: "ec1_1", name: "Lone Guard", role: "Guard", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: [] }]
    };
    
    // Edge Case 2: Overlapping shifts in same payload (not possible via API since it only takes 1 shift time, 
    // but we can test Fatigue/Daily limits)
    // We'll test consecutive days instead.
    
    // Edge Case 3: Absolute maximum penalty generation.
    // 5 roles needed, no employees available.
    const ec3_payload = {
        shift_name: "Ghost Shift",
        start_date: "2036-07-01",
        end_date: "2036-07-01",
        start_time: "08:00",
        end_time: "16:00",
        prioritize_permanent: true,
        roles: [{ role_name: "Guard", rating: 1, max_workers: 5, required_skills: [] }],
        existing_users: []
    };

    await runScenario("Edge Case 1: Overtime & Max Weekly Hours (12hx7d)", ec1_payload);
    await runScenario("Edge Case 2: Total Understaffing (0 employees, 5 required)", ec3_payload);
}

runEdgeCases();
