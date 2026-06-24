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
        
        if (name.includes("Fairness Test")) {
            const counts = {};
            for (const date in res.assignments_by_date) {
                const emps = res.assignments_by_date[date];
                emps.forEach(e => counts[e.employee_id] = (counts[e.employee_id] || 0) + 1);
            }
            console.log("Assignment Distribution:");
            console.log(Object.entries(counts).sort((a,b) => b[1] - a[1]).map(e => `${e[0]}: ${e[1]}`).join(', '));
        }

        console.log(`Score Explanation:`);
        if (res.score_explanation) {
            const constraintLines = res.score_explanation.split('\n').filter(l => l.includes('constraint')).slice(0, 5);
            constraintLines.forEach(l => console.log(l));
        }
    } catch (e) {
        console.log(`ERROR: ${e.message}`);
    }
}

async function runTests() {
    const ec4_users = [];
    for(let i=1; i<=25; i++) {
        ec4_users.push({ employee_id: `f_${i}`, name: `Fair ${i}`, role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["grill"] });
    }
    const ec4_payload = {
        shift_name: "Morning", start_date: "2037-02-01", end_date: "2037-02-10", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 5, required_skills: ["grill"] }],
        existing_users: ec4_users
    };
    await runScenario("4. Solver Fairness Test (25 emps, 50 shifts)", ec4_payload);
}
runTests();
