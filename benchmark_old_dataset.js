const http = require('http');

async function request(options, data) {
    return new Promise((resolve, reject) => {
        const req = http.request(options, (res) => {
            let body = '';
            res.on('data', chunk => body += chunk);
            res.on('end', () => {
                try { resolve(JSON.parse(body)); } catch (e) { resolve(body); }
            });
        });
        req.on('error', reject);
        if (data) {
            req.setHeader('Content-Type', 'application/json');
            req.write(JSON.stringify(data));
        }
        req.end();
    });
}

async function runBenchmark(empCount, shiftCount) {
    console.log(`\n===============================================`);
    console.log(`Running ORIGINAL Benchmark: ${empCount} employees / ${shiftCount} shifts`);
    console.log(`(NO SKILLS - Apples to Apples with Old Baseline)`);
    console.log(`===============================================`);
    
    console.log("Clearing DB...");
    await request({ hostname: '127.0.0.1', port: 8083, path: '/employees/all', method: 'DELETE' });
    await request({ hostname: '127.0.0.1', port: 8083, path: '/shifts/clear-all', method: 'DELETE' });
    
    const users = [];
    for(let i=1; i<=empCount; i++) {
        const isBest = i <= Math.max(1, Math.floor(empCount / 5)); // 20% are perfect ($10/hr)
        users.push({ 
            employee_id: `b_${i}`, name: `Worker ${i}`, role: "Cook", 
            rate: isBest ? 10.0 : 50.0, 
            unit: "hour", 
            rating: isBest ? 5 : 1, 
            employeeType: "Permanent"
            // NO SKILLS ASSIGNED
        });
    }
    
    const days = shiftCount / 5;
    const startDate = new Date("2050-01-01");
    const endDate = new Date(startDate);
    endDate.setDate(startDate.getDate() + days - 1);
    
    const payload = {
        shift_name: "Morning", 
        start_date: startDate.toISOString().split('T')[0], 
        end_date: endDate.toISOString().split('T')[0], 
        start_time: "08:00", 
        end_time: "16:00",
        roles: [{ role_name: "Cook", rating: 1, max_workers: 5 }], // NO REQUIRED SKILLS
        existing_users: users
    };
    
    const startTime = Date.now();
    const res = await request({ hostname: '127.0.0.1', port: 8083, path: '/shifts/assign-v2', method: 'POST' }, payload);
    const durationStr = ((Date.now() - startTime) / 1000).toFixed(2);
    
    console.log(`Time Taken: ${durationStr}s`);
    console.log(`Score: ${res.solver_score}`);
    
    const goodLimit = Math.max(1, Math.floor(empCount / 5));
    let goodAssigned = 0;
    let badAssigned = 0;
    
    for (const date in res.assignments_by_date) {
        for (const emp of res.assignments_by_date[date]) {
            const id = (typeof emp === 'object') ? (emp.employeeId || emp.employee_id) : emp;
            if (!id) continue;
            
            const parts = id.split('_');
            if (parts.length > 1) {
                const num = parseInt(parts[1]);
                if (num <= goodLimit) goodAssigned++;
                else badAssigned++;
            }
        }
    }
    
    console.log(`\nQuality Check:`);
    console.log(`  Perfect Workers ($10/hr) Assigned: ${goodAssigned}`);
    console.log(`  Sub-optimal Workers ($50/hr) Assigned: ${badAssigned}`);
}

async function runMany() {
    for (let i = 1; i <= 1; i++) {
        console.log(`\n--- RUN ${i} ---`);
        await runBenchmark(100, 200);
    }
}

runMany().catch(console.error);
