const http = require('http');

function request(options, data) {
    return new Promise((resolve, reject) => {
        const req = http.request(options, (res) => {
            let body = '';
            res.on('data', chunk => body += chunk);
            res.on('end', () => {
                try { resolve(JSON.parse(body)); } catch (e) { resolve(body); }
            });
        });
        req.on('error', reject);
        if (data) req.write(JSON.stringify(data));
        req.end();
    });
}

async function run() {
    console.log('Clearing DB...');
    await request({ hostname: 'localhost', port: 8083, path: '/employees/all', method: 'DELETE' });

    console.log('Creating an employee...');
    await request({ hostname: 'localhost', port: 8083, path: '/shifts/employee-profile', method: 'POST', headers: { 'Content-Type': 'application/json' } }, {
        employee_id: '101', name: 'Manual Pin Worker', role: 'Cook', hourly_wage: 15, rating: 5, category: 'Contract',
        max_weekly_hours: 40, max_daily_hours: 8
    });

    console.log('Manually assigning employee to 2026-03-01 Morning...');
    await request({ hostname: 'localhost', port: 8083, path: '/shifts/manual-assign', method: 'POST', headers: { 'Content-Type': 'application/json' } }, {
        date: '2026-03-01', shift: 'Morning',
        employees: [{ employee_id: '101', name: 'Manual Pin Worker' }]
    });

    console.log('Running auto-solver for 2026-03-01...');
    const solveReq = {
        shift_name: 'Morning',
        start_date: '2026-03-01',
        end_date: '2026-03-01',
        start_time: '08:00',
        end_time: '16:00',
        prioritizePermanent: false,
        schedule_breaks: false,
        roles: [{ role_name: 'Cook', rating: 3, max_workers: 1 }],
        existing_users: [{
            employee_id: '101', name: 'Manual Pin Worker', rate: 15.0, unit: 'hour',
            rating: 5, role: 'Cook', gender: 'Male', employeeType: 'Contract'
        }]
    };
    const res = await request({ hostname: 'localhost', port: 8083, path: '/shifts/assign-v2', method: 'POST', headers: { 'Content-Type': 'application/json' } }, solveReq);
    
    console.log('\nSolver Result (Should have 0 assignments in array because the shift is already filled by a pinned worker, and pinned workers are omitted from JSON response):');
    console.log(JSON.stringify(res, null, 2));
}

run();
