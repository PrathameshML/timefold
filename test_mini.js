const http = require('http');

const employees = [
  { employee_id: 'C3', name: 'Eve (COOK)', role: 'Cook', rating: 2, rate: 15, unit: 'hour', employeeType: 'Contract', skills: ['Grill'] },
  { employee_id: 'C4', name: 'Frank (COOK)', role: 'Cook', rating: 3, rate: 16, unit: 'hour', employeeType: 'Contract', skills: ['Food Safety'] }
];

const payload = {
    shift_name: "Realistic Restaurant Week",
    start_date: "2026-08-03", // Monday
    end_date: "2026-08-03",   // Monday (1 day)
    start_time: "10:00",
    end_time: "18:00",
    prioritizePermanent: true,
    schedule_breaks: false,
    roles: [
        { role_name: "Cook", rating: 1, max_workers: 2, required_skills: ["Grill", "Food Safety"] }
    ],
    existing_users: employees,
    time_limit_seconds: 5,
    unimproved_time_limit_seconds: 2
};

const options = {
    hostname: '127.0.0.1',
    port: 8083,
    path: '/shifts/assign-v2',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
};

const req = http.request(options, (res) => {
    let data = '';
    res.on('data', (chunk) => { data += chunk; });
    res.on('end', () => {
        const result = JSON.parse(data);
        console.log('Score:', result.score);
        console.log('Assignments:', result.assignments.map(a => a.employeeName));
        console.log('Score Explanation:\n', result.score_explanation);
    });
});
req.write(JSON.stringify(payload));
req.end();
