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
            const counts = {};
            for (const date in res.assignments_by_date) {
                for (const empStr of res.assignments_by_date[date]) {
                    // res.assignments_by_date[date] is an array of strings in V2 output format
                    counts[empStr] = (counts[empStr] || 0) + 1;
                }
            }
            
            const sortedCounts = Object.entries(counts).sort((a,b) => b[1] - a[1]);
            console.log("\nDistribution:");
            for (const [emp, count] of sortedCounts) {
                console.log(`  ${emp}: ${count} shifts`);
            }
        }
        
        if (res.score_explanation) {
            console.log("\nScore Explanation:");
            const constraintLines = res.score_explanation.split('\n').filter(l => l.includes('constraint')).slice(0, 15);
            constraintLines.forEach(l => console.log(l));
        }
        return res;
    } catch (e) {
        console.log(`ERROR: ${e.message}`);
    }
}

async function runBatch4() {
    console.log("\n=== 11. True Fairness Distribution Test ===");
    const fair_users = [];
    for(let i=1; i<=25; i++) {
        fair_users.push({ 
            employee_id: `idnt_${i}`, name: `Worker ${i}`, role: "Cook", 
            rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["grill"] 
        });
    }
    const fair_payload = {
        shift_name: "Morning", 
        start_date: "2038-11-01", // Monday
        end_date: "2038-11-20",   // 20 days (3 weeks)
        start_time: "08:00", 
        end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 5, required_skills: ["grill"] }], 
        existing_users: fair_users
    };
    await runScenario("25 Identical Employees (100 Shifts)", fair_payload);


    console.log("\n=== 12. Multiple Soft Hierarchy Test ===");
    const hier_users = [
        { employee_id: "h_100_exp", name: "100% Skill, $50, 5-Star", role: "Cook", rate: 50.0, unit: "hour", rating: 5, employeeType: "Permanent", skills: ["grill"] },
        { employee_id: "h_75_chp", name: "0% Skill, $10, 5-Star", role: "Cook", rate: 10.0, unit: "hour", rating: 5, employeeType: "Permanent", skills: [] },
        { employee_id: "h_chp_low", name: "0% Skill, $10, 1-Star", role: "Cook", rate: 10.0, unit: "hour", rating: 1, employeeType: "Permanent", skills: [] },
        { employee_id: "h_exp_low", name: "0% Skill, $50, 1-Star", role: "Cook", rate: 50.0, unit: "hour", rating: 1, employeeType: "Permanent", skills: [] }
    ];
    const hier_payload = {
        shift_name: "Morning", start_date: "2038-12-01", end_date: "2038-12-01", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 1, required_skills: ["grill"] }],
        existing_users: hier_users,
        config: [
            { constraintId: 1, severity: "SOFT" }, // skillMatch = SOFT
            { constraintId: 4, severity: "SOFT" }  // everyShiftPlanned = SOFT
        ]
    };
    await runScenario("SkillMatch=SOFT, EveryShift=SOFT", hier_payload);
}

runBatch4();
