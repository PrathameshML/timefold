const http = require('http');

const payload = {
  "shift_name": "Evening Shift",
  "start_date": "2026-07-01",
  "end_date": "2026-07-01",
  "start_time": "16:00",
  "end_time": "23:00",
  "prioritizePermanent": true,
  "schedule_breaks": true,
  "roles": [
    { "role_name": "Bartender", "rating": 2, "max_workers": 2, "required_skills": ["Mixology"] },
    { "role_name": "Cook", "rating": 3, "max_workers": 2, "required_skills": ["Grill"] },
    { "role_name": "Waiter", "rating": 1, "max_workers": 3, "required_skills": ["Customer Service"] }
  ],
  "existing_users": [
    { "employee_id": "b1", "name": "Bartender Alice", "role": "Bartender", "rating": 5, "rate": 20.0, "unit": "hour", "employeeType": "Contract", "skills": ["Mixology"] },
    { "employee_id": "b2", "name": "Bartender Bob", "role": "Bartender", "rating": 3, "rate": 18.0, "unit": "hour", "employeeType": "Contract", "skills": ["Mixology"] },
    { "employee_id": "c1", "name": "Cook Frank", "role": "Cook", "rating": 4, "rate": 25.0, "unit": "hour", "employeeType": "Contract", "skills": ["Grill"] },
    { "employee_id": "w1", "name": "Waiter Kevin", "role": "Waiter", "rating": 5, "rate": 15.0, "unit": "hour", "employeeType": "Contract", "skills": ["Customer Service"] }
  ],
  "active_constraints": [
    { "name": "everyShiftPlanned", "severity": "MEDIUM" },
    { "name": "permanentPriority", "severity": "HARD" }
  ],
  "time_limit_seconds": 10
};

console.log(`Running Strict 10s Benchmark to prove HARD constraint behavior...`);

const options = {
    hostname: 'localhost',
    port: 8083,
    path: '/shifts/assign-v2',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
};

const req = http.request(options, (res) => {
    let data = '';
    res.on('data', (chunk) => data += chunk);
    res.on('end', () => {
        try {
            const result = JSON.parse(data);
            console.log(`Assignments Made: ${result.new_assignments_made}`);
            console.log(`Score: ${result.solver_score}`);
            console.log(result.score_explanation);
        } catch(e) {
            console.log(e);
        }
    });
});

req.write(JSON.stringify(payload));
req.end();
