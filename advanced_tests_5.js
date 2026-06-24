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
            const constraintLines = res.score_explanation.split('\n').filter(l => l.includes('constraint')).slice(0, 15);
            constraintLines.forEach(l => console.log(l));
        }
    } catch (e) {
        console.log(`ERROR: ${e.message}`);
    }
}

async function runTests() {
    // 5. Constraint Severity Test
    // Same as Skill Match Boundary Test, but we inject SOFT severity override!
    // Role requires 3 skills.
    const ec5_users = [
        { employee_id: "s100_soft", name: "100 Match", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["cooking", "food_safety", "inventory"] },
        { employee_id: "s66_soft", name: "66 Match", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["cooking", "food_safety"] },
        { employee_id: "s33_soft", name: "33 Match", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["cooking"] }
    ];
    const ec5_payload = {
        shift_name: "Morning", start_date: "2037-03-01", end_date: "2037-03-01", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 3, required_skills: ["cooking", "food_safety", "inventory"] }],
        existing_users: ec5_users,
        config: [
            { constraintId: 1, severity: "SOFT" } // skillMatch = SOFT
        ]
    };

    await runScenario("5. Dynamic Severity Test (skillMatch = SOFT)", ec5_payload);
}

runTests();
