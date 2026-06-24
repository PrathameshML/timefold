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

async function runBenchmarkForTime(timeLimit) {
    console.log(`\n===============================================`);
    console.log(`Running Benchmark: TIME LIMIT = ${timeLimit} SECONDS`);
    console.log(`===============================================`);
    
    await request({ hostname: '127.0.0.1', port: 8083, path: '/employees/all', method: 'DELETE' });
    await request({ hostname: '127.0.0.1', port: 8083, path: '/shifts/clear-all', method: 'DELETE' });
    
    const empCount = 20;
    const shiftCount = 40;
    
    const users = [];
    for(let i=1; i<=empCount; i++) {
        const isBest = i <= Math.max(1, Math.floor(empCount / 5)); // 20% are perfect
        users.push({ 
            employee_id: `b_${i}`, name: `Worker ${i}`, role: "Cook", 
            rate: isBest ? 10.0 : 50.0, 
            unit: "hour", 
            rating: isBest ? 5 : 1, 
            employeeType: "Permanent"
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
        roles: [{ role_name: "Cook", rating: 1, max_workers: 5 }],
        existing_users: users,
        time_limit_seconds: timeLimit,
        unimproved_time_limit_seconds: timeLimit // Force it to run the full time
    };
    
    const startTime = Date.now();
    const res = await request({ hostname: '127.0.0.1', port: 8083, path: '/shifts/assign-v2', method: 'POST' }, payload);
    const duration = ((Date.now() - startTime) / 1000).toFixed(2);
    
    const goodLimit = Math.max(1, Math.floor(empCount / 5));
    let goodAssigned = 0;
    let badAssigned = 0;
    
    for (const date in res.assignments_by_date) {
        for (const emp of res.assignments_by_date[date]) {
            const id = (typeof emp === 'object') ? (emp.employeeId || emp.employee_id) : emp;
            if (!id) continue;
            
            const num = parseInt(id.split('_')[1]);
            if (num <= goodLimit) goodAssigned++;
            else badAssigned++;
        }
    }
    
    console.log(`Actual API Time: ${duration}s`);
    console.log(`Score: ${res.solver_score}`);
    console.log(`Perfect Workers: ${goodAssigned}`);
    console.log(`Sub-optimal Workers: ${badAssigned}`);
    
    return { time: timeLimit, actual: duration, score: res.solver_score, perfect: goodAssigned, sub: badAssigned };
}

async function runAll() {
    const times = [3, 6, 10, 15, 20, 30, 60];
    const results = [];
    
    for (const t of times) {
        const res = await runBenchmarkForTime(t);
        results.push(res);
    }
    
    console.log("\n\n=== FINAL SUMMARY ===");
    console.log("Time Limit | Actual | Perfect | Sub-optimal | Score");
    console.log("-----------------------------------------------------");
    results.forEach(r => {
        console.log(`${String(r.time).padStart(10)}s | ${String(r.actual).padStart(5)}s | ${String(r.perfect).padStart(7)} | ${String(r.sub).padStart(11)} | ${r.score}`);
    });
}

runAll().catch(console.error);
