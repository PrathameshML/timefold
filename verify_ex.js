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
        
        const res = await response.json();
        console.log(`Score: ${res.solver_score}`);
        console.log(`Assignments Made: ${res.new_assignments_made}`);
        
        if (res.new_assignments_made > 0) {
            console.log("Assignments:");
            for (const date in res.assignments_by_date) {
                for (const emp of res.assignments_by_date[date]) {
                    const id = typeof emp === 'object' ? emp.employee_id : emp;
                    console.log(`  ${date}: ${id}`);
                }
            }
        }
        
        console.log("\nScore Explanation:");
        console.log(res.score_explanation.split('\n').slice(0, 20).join('\n'));
        return res;
    } catch (e) {
        console.log(`ERROR: ${e.message}`);
    }
}

async function verify() {
    console.log("Mathematical Proof: ex_4 vs ex_40");
    
    // We isolate the two workers to see exactly how they score.
    // Both are Permanent, Male, Cook, with ["grill"].
    // ex_4: $10, 5-Star
    // ex_40: $50, 1-Star
    const users = [
        { employee_id: "ex_4", name: "Worker 4", role: "Cook", rate: 10.0, unit: "hour", rating: 5, employeeType: "Permanent", skills: ["grill"] },
        { employee_id: "ex_40", name: "Worker 40", role: "Cook", rate: 50.0, unit: "hour", rating: 1, employeeType: "Permanent", skills: ["grill"] }
    ];
    
    // We use a date far in the future to avoid any pinned historical assignments from previous tests.
    const payload = {
        shift_name: "Morning", start_date: "2040-01-01", end_date: "2040-01-01", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 1, required_skills: ["grill"] }],
        existing_users: users
    };
    
    await runScenario("Head-to-Head: ex_4 vs ex_40", payload);
}

verify();
