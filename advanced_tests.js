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
        
        // Count assignment frequencies for Fairness Test
        if (name.includes("Fairness Test")) {
            const counts = {};
            for (const date in res.assignments_by_date) {
                for (const shift in res.assignments_by_date[date]) {
                    const emps = res.assignments_by_date[date][shift];
                    emps.forEach(e => counts[e.employee_id] = (counts[e.employee_id] || 0) + 1);
                }
            }
            console.log("Assignment Distribution:");
            console.log(Object.entries(counts).sort((a,b) => b[1] - a[1]).map(e => `${e[0]}: ${e[1]}`).join(', '));
        }

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
    // 1. Competing Candidates Test
    // 10 employees, same role. Varying ratings and wages.
    const ec1_users = [];
    for(let i=1; i<=10; i++) {
        // e1: $10, 1 star
        // e10: $100, 5 stars
        ec1_users.push({ employee_id: `c_${i}`, name: `Cand ${i}`, role: "Cook", rate: i*10.0, unit: "hour", rating: Math.ceil(i/2), employeeType: "Permanent", skills: ["grill"] });
    }
    const ec1_payload = {
        shift_name: "Morning", start_date: "2037-01-01", end_date: "2037-01-01", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 1, required_skills: ["grill"] }],
        existing_users: ec1_users
    };

    // 2. Skill Match Boundary Test
    // Role requires 3 skills.
    const ec2_users = [
        { employee_id: "s100", name: "100 Match", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["cooking", "food_safety", "inventory"] },
        { employee_id: "s66", name: "66 Match", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["cooking", "food_safety"] },
        { employee_id: "s33", name: "33 Match", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["cooking"] }
    ];
    // We will test asking for 3 workers so all 3 get evaluated.
    const ec2_payload = {
        shift_name: "Morning", start_date: "2037-01-02", end_date: "2037-01-02", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 3, required_skills: ["cooking", "food_safety", "inventory"] }],
        existing_users: ec2_users
    };

    // 3. Permanent vs Contractor
    const ec3_users = [
        { employee_id: "p_exp", name: "Expensive Perm", role: "Cook", rate: 12.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["grill"] },
        { employee_id: "c_cheap", name: "Cheap Cont", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Contractor", skills: ["grill"] }
    ];
    const ec3_payload = {
        shift_name: "Morning", start_date: "2037-01-03", end_date: "2037-01-03", start_time: "08:00", end_time: "16:00",
        prioritize_permanent: true,
        roles: [{ role_name: "Cook", rating: 1, max_workers: 1, required_skills: ["grill"] }],
        existing_users: ec3_users
    };

    // 4. Solver Fairness Test
    // 25 identical employees, 50 shifts (5 workers/day for 10 days).
    const ec4_users = [];
    for(let i=1; i<=25; i++) {
        ec4_users.push({ employee_id: `f_${i}`, name: `Fair ${i}`, role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["grill"] });
    }
    const ec4_payload = {
        shift_name: "Morning", start_date: "2037-02-01", end_date: "2037-02-10", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 5, required_skills: ["grill"] }],
        existing_users: ec4_users
    };

    await runScenario("1. Competing Candidates (10 employees, 1 shift)", ec1_payload);
    await runScenario("2. Skill Match Boundary (100%, 66%, 33%)", ec2_payload);
    await runScenario("3. Permanent vs Contractor ($12 Perm vs $10 Cont)", ec3_payload);
    await runScenario("4. Solver Fairness Test (25 emps, 50 shifts)", ec4_payload);
}

runTests();
