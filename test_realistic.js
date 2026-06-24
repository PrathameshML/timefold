const http = require('http');

const employees = [
  // MANAGERS (Need 1/day = 7 shifts. Capacity: 2 * 5 = 10 shifts)
  { employee_id: 'M1', name: 'Alice (MGR)', role: 'Manager', rating: 5, rate: 30, unit: 'hour', employeeType: 'Permanent', skills: ['Leadership', 'First Aid'] },
  { employee_id: 'M2', name: 'Bob (MGR)', role: 'Manager', rating: 3, rate: 20, unit: 'hour', employeeType: 'Contract', skills: ['Leadership'] },

  // COOKS (Need 4/day = 28 shifts. Capacity: 6 * 5 = 30 shifts)
  { employee_id: 'C1', name: 'Charlie (COOK)', role: 'Cook', rating: 4, rate: 18, unit: 'hour', employeeType: 'Permanent', skills: ['Grill', 'Food Safety'] },
  { employee_id: 'C2', name: 'Dave (COOK)', role: 'Cook', rating: 5, rate: 22, unit: 'hour', employeeType: 'Permanent', skills: ['Grill', 'Food Safety'] },
  { employee_id: 'C3', name: 'Eve (COOK)', role: 'Cook', rating: 2, rate: 15, unit: 'hour', employeeType: 'Contract', skills: ['Grill'] },
  { employee_id: 'C4', name: 'Frank (COOK)', role: 'Cook', rating: 3, rate: 16, unit: 'hour', employeeType: 'Contract', skills: ['Food Safety'] },
  { employee_id: 'C5', name: 'Grace (COOK)', role: 'Cook', rating: 4, rate: 17, unit: 'hour', employeeType: 'Permanent', skills: ['Grill', 'Food Safety'] },
  { employee_id: 'C6', name: 'Heidi (COOK)', role: 'Cook', rating: 1, rate: 14, unit: 'hour', employeeType: 'Contract', skills: ['Grill', 'Food Safety'] },

  // WAITERS (Need 8/day = 56 shifts. Capacity: 10 * 5 = 50 shifts) -> Understaffed by 6 shifts!
  { employee_id: 'W1', name: 'Ivan (WAITER)', role: 'Waiter', rating: 5, rate: 15, unit: 'hour', employeeType: 'Permanent', skills: ['Customer Service'] },
  { employee_id: 'W2', name: 'Judy (WAITER)', role: 'Waiter', rating: 4, rate: 14, unit: 'hour', employeeType: 'Permanent', skills: ['Customer Service'] },
  { employee_id: 'W3', name: 'Mallory (WAITER)', role: 'Waiter', rating: 3, rate: 13, unit: 'hour', employeeType: 'Contract', skills: ['Customer Service'] },
  { employee_id: 'W4', name: 'Niaj (WAITER)', role: 'Waiter', rating: 2, rate: 12, unit: 'hour', employeeType: 'Contract', skills: ['Customer Service'] },
  { employee_id: 'W5', name: 'Olivia (WAITER)', role: 'Waiter', rating: 4, rate: 14, unit: 'hour', employeeType: 'Contract', skills: [] },
  { employee_id: 'W6', name: 'Peggy (WAITER)', role: 'Waiter', rating: 5, rate: 16, unit: 'hour', employeeType: 'Permanent', skills: ['Customer Service'] },
  { employee_id: 'W7', name: 'Sybil (WAITER)', role: 'Waiter', rating: 3, rate: 13, unit: 'hour', employeeType: 'Contract', skills: ['Customer Service'] },
  { employee_id: 'W8', name: 'Trent (WAITER)', role: 'Waiter', rating: 1, rate: 10, unit: 'hour', employeeType: 'Contract', skills: ['Customer Service'] },
  { employee_id: 'W9', name: 'Victor (WAITER)', role: 'Waiter', rating: 4, rate: 14, unit: 'hour', employeeType: 'Permanent', skills: ['Customer Service'] },
  { employee_id: 'W10', name: 'Walter (WAITER)', role: 'Waiter', rating: 2, rate: 11, unit: 'hour', employeeType: 'Contract', skills: ['Customer Service'] }
];

const payload = {
    shift_name: "Realistic Restaurant Week",
    start_date: "2026-08-03", // Monday
    end_date: "2026-08-09",   // Sunday (7 days)
    start_time: "10:00",
    end_time: "18:00", // 8 hours
    prioritizePermanent: true,
    schedule_breaks: false,
    roles: [
        { role_name: "Manager", rating: 1, max_workers: 1, required_skills: ["Leadership", "First Aid"] },
        { role_name: "Cook", rating: 1, max_workers: 4, required_skills: ["Grill", "Food Safety"] },
        { role_name: "Waiter", rating: 1, max_workers: 8, required_skills: ["Customer Service"] }
    ],
    existing_users: employees,
    time_limit_seconds: 15,
    unimproved_time_limit_seconds: 5,
    active_constraints: [
        { id: 1, name: "skillMatch", severity: "HARD", isEnabled: true, parameterValue: 100.0 },
        { id: 4, name: "everyShiftPlanned", severity: "MEDIUM", isEnabled: true },
        { id: 5, name: "wageOptimization", severity: "SOFT", isEnabled: true },
        { id: 6, name: "maxDailyHours", severity: "MEDIUM", isEnabled: true, parameterValue: 8.0 },
        { id: 7, name: "maxWeeklyHours", severity: "MEDIUM", isEnabled: true, parameterValue: 40.0 },
        { id: 10, name: "consecutiveShifts", severity: "HARD", isEnabled: true },
        { id: 11, name: "permanentPriority", severity: "SOFT", isEnabled: true }
    ]
};

const options = {
    hostname: '127.0.0.1',
    port: 8083,
    path: '/shifts/assign-v2',
    method: 'POST',
    headers: {
        'Content-Type': 'application/json'
    }
};

const req = http.request(options, (res) => {
    let data = '';
    res.on('data', (chunk) => { data += chunk; });
    res.on('end', () => {
        try {
            const result = JSON.parse(data);
            console.log('=== REALISTIC SCENARIO RESULTS ===');
            console.log('Score:', result.score);
            console.log('Solve Status:', result.solverStatus);
            console.log('Total Assignments Made:', result.assignments.length);
            
            // Analyze assignments
            let unassigned = 91 - result.assignments.length;
            console.log('Unassigned Shifts (Medium Penalty expected):', unassigned);
            
            // Group by employee
            const byEmp = {};
            result.assignments.forEach(a => {
                if (!byEmp[a.employeeName]) byEmp[a.employeeName] = 0;
                byEmp[a.employeeName]++;
            });
            
            console.log('\n--- Employee Workload (Max 5 days) ---');
            Object.entries(byEmp).sort((a,b) => b[1] - a[1]).forEach(([name, count]) => {
                let warning = count > 5 ? ' ?? HARD CONSTRAINT VIOLATION (OT)' : '';
                console.log(`- ${name}: ${count} shifts${warning}`);
            });
            
        } catch(e) {
            console.log('Error parsing response:', e);
            console.log(data);
        }
    });
});

req.on('error', (e) => {
    console.error(`Problem with request: ${e.message}`);
});

req.write(JSON.stringify(payload));
req.end();
