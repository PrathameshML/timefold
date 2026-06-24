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
            console.log(`Workers Assigned: ${Object.values(res.assignments_by_date)[0].map(e => e.employee_id).join(", ")}`);
        }
        if (res.score_explanation) {
            const constraintLines = res.score_explanation.split('\n').filter(l => l.includes('constraint')).slice(0, 10);
            constraintLines.forEach(l => console.log(l));
        }
        return res;
    } catch (e) {
        console.log(`ERROR: ${e.message}`);
    }
}

async function runBatch2() {
    console.log("\n=== 5. Constraint Toggle Regression ===");
    // Test toggle of everyShiftPlanned
    const toggle_payload = {
        shift_name: "Morning", start_date: "2038-05-01", end_date: "2038-05-01", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 1, required_skills: ["grill"] }],
        existing_users: [{ employee_id: "tog_1", name: "Worker", role: "Cook", rate: 1000.0, unit: "hour", rating: 1, employeeType: "Permanent", skills: ["grill"] }]
    }; // Very high wage, terrible rating.

    const severities = ["HARD", "MEDIUM", "SOFT"];
    for (const sev of severities) {
        await runScenario(`everyShiftPlanned = ${sev}`, { ...toggle_payload, config: [{constraintId: 4, severity: sev}] });
    }

    console.log("\n=== 6. Wage vs Rating Matrix ===");
    const matrix_payload = {
        shift_name: "Morning", start_date: "2038-06-01", end_date: "2038-06-01", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 1, required_skills: ["grill"] }],
        existing_users: [
            { employee_id: "wm_1", name: "R1_$10", role: "Cook", rate: 10.0, unit: "hour", rating: 1, employeeType: "Permanent", skills: ["grill"] },
            { employee_id: "wm_2", name: "R2_$20", role: "Cook", rate: 20.0, unit: "hour", rating: 2, employeeType: "Permanent", skills: ["grill"] },
            { employee_id: "wm_3", name: "R3_$30", role: "Cook", rate: 30.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["grill"] },
            { employee_id: "wm_4", name: "R4_$40", role: "Cook", rate: 40.0, unit: "hour", rating: 4, employeeType: "Permanent", skills: ["grill"] },
            { employee_id: "wm_5", name: "R5_$50", role: "Cook", rate: 50.0, unit: "hour", rating: 5, employeeType: "Permanent", skills: ["grill"] }
        ]
    };
    await runScenario("Wage vs Rating Matrix Selection", matrix_payload);

    console.log("\n=== 7. Skill Match Threshold Test ===");
    const threshold_payload = {
        shift_name: "Morning", start_date: "2038-07-01", end_date: "2038-07-01", start_time: "08:00", end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 5, required_skills: ["s1", "s2", "s3", "s4"] }],
        existing_users: [
            { employee_id: "st_100", name: "100%", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["s1", "s2", "s3", "s4"] },
            { employee_id: "st_75", name: "75%", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["s1", "s2", "s3"] },
            { employee_id: "st_50", name: "50%", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["s1", "s2"] },
            { employee_id: "st_25", name: "25%", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: ["s1"] },
            { employee_id: "st_0", name: "0%", role: "Cook", rate: 10.0, unit: "hour", rating: 3, employeeType: "Permanent", skills: [] }
        ]
    };

    for (const sev of severities) {
        await runScenario(`Skill Match = ${sev}`, { ...threshold_payload, config: [{constraintId: 1, severity: sev}] });
    }
}

runBatch2();
