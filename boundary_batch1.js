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
        if (res.score_explanation) {
            const constraintLines = res.score_explanation.split('\n').filter(l => l.includes('constraint')).slice(0, 10);
            constraintLines.forEach(l => console.log(l));
        }
        return res;
    } catch (e) {
        console.log(`ERROR: ${e.message}`);
    }
}

async function runBatch1() {
    console.log("=== 1. Severity Matrix Test ===");
    // Scenario: 1 shift requires 2 workers. Only 1 worker available, and they are missing a skill.
    const sev_payload = {
        shift_name: "Morning", start_date: "2038-01-01", end_date: "2038-01-01", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 2, required_skills: ["grill", "prep"] }],
        existing_users: [{ employee_id: "sev_1", name: "MissingSkill", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["grill"] }]
    };

    const configs = [
        { name: "All HARD", cfg: [{constraintId: 1, severity: "HARD"}, {constraintId: 4, severity: "HARD"}] },
        { name: "All MEDIUM", cfg: [{constraintId: 1, severity: "MEDIUM"}, {constraintId: 4, severity: "MEDIUM"}] },
        { name: "All SOFT", cfg: [{constraintId: 1, severity: "SOFT"}, {constraintId: 4, severity: "SOFT"}] },
        { name: "Skill=SOFT, Coverage=MEDIUM", cfg: [{constraintId: 1, severity: "SOFT"}, {constraintId: 4, severity: "MEDIUM"}] }
    ];

    for (const c of configs) {
        const payload = { ...sev_payload, config: c.cfg };
        await runScenario(`Severity Matrix: ${c.name}`, payload);
    }

    console.log("\n=== 2. Constraint Conflict Test ===");
    // 4 evils. Require 1 worker.
    const conflict_payload = {
        shift_name: "Morning", start_date: "2038-02-01", end_date: "2038-02-01", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 1, required_skills: ["grill"] }],
        prioritize_permanent: true,
        existing_users: [
            { employee_id: "cc_A", name: "HighRating HighWage", role: "Cook", rate: 50.0, unit: "hour", rating: 5, employeeType: "Permanent", skills: ["grill"] },
            { employee_id: "cc_B", name: "LowRating LowWage", role: "Cook", rate: 10.0, unit: "hour", rating: 1, employeeType: "Permanent", skills: ["grill"] },
            { employee_id: "cc_C", name: "Missing Skill", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["prep"] },
            { employee_id: "cc_D", name: "Contractor", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Contractor", skills: ["grill"] }
        ]
    };
    await runScenario("Constraint Conflict Test", conflict_payload);

    console.log("\n=== 3. Random Chaos Test ===");
    const chaos_users = [];
    for(let i=1; i<=50; i++) {
        chaos_users.push({ 
            employee_id: `chaos_${i}`, name: `Chaos ${i}`, role: "Cook", 
            rate: Math.floor(Math.random()*40)+10, 
            unit: "hour", 
            rating: Math.floor(Math.random()*5)+1, 
            employeeType: Math.random() > 0.3 ? "Permanent" : "Contractor", 
            skills: Math.random() > 0.5 ? ["grill", "prep"] : ["grill"] 
        });
    }
    const chaos_roles = [];
    for(let i=1; i<=10; i++) {
        chaos_roles.push({ role_name: `Cook`, rating: Math.floor(Math.random()*3)+1, max_workers: 1, required_skills: ["grill", "prep"] });
    }
    // Note: V2 merges roles with the same name, so creating 10 "Cook" roles will actually just take the last one or cause undefined behavior. 
    // We should make max_workers: 10 for a single Cook role.
    const chaos_payload = {
        shift_name: "Morning", start_date: "2038-03-01", end_date: "2038-03-10", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 10, required_skills: ["grill", "prep"] }],
        prioritize_permanent: true,
        existing_users: chaos_users
    };
    await runScenario("Random Chaos Test (50 emps, 100 shifts)", chaos_payload);

    console.log("\n=== 4. Historical + New Schedule Conflict ===");
    // Week 1
    const hist_users = [
        { employee_id: "hist_1", name: "Worker 1", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["grill"] }
    ];
    const w1_payload = {
        shift_name: "Morning", start_date: "2038-04-01", end_date: "2038-04-07", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 1, required_skills: ["grill"] }],
        existing_users: hist_users
    };
    await runScenario("Historical Base (Week 1)", w1_payload);
    
    // Week 2 overlapping / same employee
    const w2_payload = {
        shift_name: "Evening", start_date: "2038-04-01", end_date: "2038-04-07", start_time: "16:00", end_time: "23:59",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 1, required_skills: ["grill"] }],
        existing_users: hist_users
    };
    await runScenario("Historical Conflict (Week 2 Evening)", w2_payload);
}

runBatch1();
