const fs = require('fs');

const users = [];
for (let i = 1; i <= 5; i++) {
    users.push({ employee_id: `e${i}`, name: `Manager ${i}`, role: "Manager", rate: 50.0, unit: "hour", rating: 5, employeeType: "Permanent", skills: ["management"] });
}

for (let i = 6; i <= 15; i++) {
    const rating = (i % 4) + 2;
    const skills = i % 2 === 0 ? ["grill", "prep"] : ["grill"];
    const type = i % 3 === 0 ? "Contractor" : "Permanent";
    users.push({ employee_id: `e${i}`, name: `Cook ${i}`, role: "Cook", rate: 25.0, unit: "hour", rating: rating, employeeType: type, skills: skills });
}

for (let i = 16; i <= 25; i++) {
    const rating = (i % 5) + 1;
    const skills = i === 25 ? [] : ["service"];
    users.push({ employee_id: `e${i}`, name: `Waiter ${i}`, role: "Waiter", rate: 12.0, unit: "hour", rating: rating, employeeType: "Permanent", skills: skills });
}

const roles = [
    { role_name: "Manager", rating: 4, max_workers: 1, required_skills: ["management"] },
    { role_name: "Cook", rating: 3, max_workers: 3, required_skills: ["grill", "prep"] },
    { role_name: "Waiter", rating: 1, max_workers: 5, required_skills: ["service"] }
];

const payloadMorning = {
    shift_name: "Morning Shift",
    start_date: "2035-05-01",
    end_date: "2035-05-07",
    start_time: "08:00",
    end_time: "16:00",
    prioritize_permanent: true,
    roles: roles,
    existing_users: users
};

const payloadEvening = {
    shift_name: "Evening Shift",
    start_date: "2035-05-01",
    end_date: "2035-05-07",
    start_time: "16:00",
    end_time: "23:59",
    prioritize_permanent: true,
    roles: roles,
    existing_users: users
};

fs.writeFileSync('payload_morning.json', JSON.stringify(payloadMorning, null, 2));
fs.writeFileSync('payload_evening.json', JSON.stringify(payloadEvening, null, 2));

async function runScenario(name, payloadFile) {
    console.log(`\n===============================================`);
    console.log(`Running: ${name}`);
    console.log(`===============================================`);
    try {
        const data = fs.readFileSync(payloadFile, 'utf8');
        const response = await fetch("http://localhost:8083/shifts/assign-v2", {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: data
        });
        
        if (!response.ok) {
            const text = await response.text();
            console.log(`ERROR: ${response.status} ${response.statusText}\n${text}`);
            return;
        }
        
        const res = await response.json();
        console.log(`Score: ${res.solver_score}`);
        console.log(`Time: ${res.solver_time_seconds}s`);
        console.log(`Entities Planned: ${res.entities_planned}`);
        console.log(`Assignments Made: ${res.new_assignments_made}`);
        console.log(`Score Explanation:`);
        
        if (res.score_explanation) {
            const constraintLines = res.score_explanation.split('\n').filter(l => l.includes('constraint')).slice(0, 25);
            constraintLines.forEach(l => console.log(l));
        }
    } catch (e) {
        console.log(`ERROR: ${e.message}`);
    }
}

async function runAll() {
    await runScenario("Morning Shift (7 Days, 25 Employees)", "payload_morning.json");
    await runScenario("Evening Shift (7 Days, 25 Employees) - Stresses Overlap/Max Hours", "payload_evening.json");
}

runAll();
