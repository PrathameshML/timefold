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

async function clearDb() {
    console.log("Clearing all employees and shifts...");
    await request({ hostname: '127.0.0.1', port: 8083, path: '/employees/all', method: 'DELETE' });
    await request({ hostname: '127.0.0.1', port: 8083, path: '/shifts/clear-all', method: 'DELETE' });
}

async function runRajeshVsSuresh() {
    console.log("\n--- Running Rajesh vs Suresh (with 50% Skill Match) ---");
    await clearDb();

    const payload = {
        shift_name: "Morning",
        start_date: "2026-03-01",
        end_date: "2026-03-05", // 5 shifts
        start_time: "08:00",
        end_time: "16:00",
        prioritizePermanent: false,
        schedule_breaks: false,
        active_constraints: [
            { name: "skillMatch", severity: "HARD", value: 50.0 }
        ],
        roles: [
            { role_name: "Cook", rating: 3, max_workers: 1, required_skills: ["Skill A", "Skill B"] }
        ],
        existing_users: [
            {
                employee_id: "101", name: "Rajesh Kumar", rate: 15.0, unit: "hour",
                rating: 5, role: "Cook", gender: "Male", employeeType: "Contract",
                skills: ["Skill A"] // 50% match - perfectly meets the 50% threshold
            },
            {
                employee_id: "102", name: "Suresh Verma", rate: 50.0, unit: "hour",
                rating: 1, role: "Cook", gender: "Male", employeeType: "Contract",
                skills: [] // 0% match - fails the 50% threshold, gets penalized!
            }
        ]
    };

    const res = await request({ hostname: '127.0.0.1', port: 8083, path: '/shifts/assign-v2', method: 'POST' }, payload);
    
    let rajeshCount = 0;
    let sureshCount = 0;
    for (const date in res.assignments_by_date) {
        for (const emp of res.assignments_by_date[date]) {
            if (emp.employeeName === 'Rajesh Kumar') rajeshCount++;
            if (emp.employeeName === 'Suresh Verma') sureshCount++;
        }
    }
    
    console.log(`Score: ${res.solver_score}`);
    console.log(`Rajesh assignments: ${rajeshCount}`);
    console.log(`Suresh assignments: ${sureshCount}`);
    if (rajeshCount === 5 && sureshCount === 0) {
        console.log("✅ Rajesh vs Suresh PASS");
    } else {
        console.log("❌ Rajesh vs Suresh FAIL");
    }
}

async function runBenchmark(empCount, shiftCount) {
    console.log(`\n--- Running Benchmark: ${empCount} employees / ${shiftCount} shifts (with 50% Skill Match) ---`);
    await clearDb();
    
    const users = [];
    for(let i=1; i<=empCount; i++) {
        const isBest = i <= Math.max(1, Math.floor(empCount / 5)); // 20% are perfect
        
        // Give perfect workers just enough skills (50% = 1 skill out of 2)
        // Give sub-optimal workers no skills (0% = heavily penalized)
        const userSkills = isBest ? ["Skill A"] : [];
        
        users.push({ 
            employee_id: `b_${i}`, name: `Worker ${i}`, role: "Cook", 
            rate: isBest ? 10.0 : 50.0, 
            unit: "hour", 
            rating: isBest ? 5 : 1, 
            employeeType: "Permanent", 
            skills: userSkills 
        });
    }
    
    const days = shiftCount / 5; // 5 workers per shift
    const startDate = new Date("2050-01-01");
    const endDate = new Date(startDate);
    endDate.setDate(startDate.getDate() + days - 1);
    
    const payload = {
        shift_name: "Morning", 
        start_date: startDate.toISOString().split('T')[0], 
        end_date: endDate.toISOString().split('T')[0], 
        start_time: "08:00", 
        end_time: "16:00",
        active_constraints: [
            { name: "skillMatch", severity: "HARD", value: 50.0 }
        ],
        roles: [{ role_name: "Cook", rating: 1, max_workers: 5, required_skills: ["Skill A", "Skill B"] }],
        existing_users: users
    };
    
    const startTime = Date.now();
    const res = await request({ hostname: '127.0.0.1', port: 8083, path: '/shifts/assign-v2', method: 'POST' }, payload);
    const duration = ((Date.now() - startTime) / 1000).toFixed(2);
    
    const goodLimit = Math.max(1, Math.floor(empCount / 5));
    let goodAssigned = 0;
    let badAssigned = 0;
    
    for (const date in res.assignments_by_date) {
        for (const emp of res.assignments_by_date[date]) {
            const id = emp.employeeId || emp.employee_id;
            if (!id) continue;
            
            const num = parseInt(id.split('_')[1]);
            if (num <= goodLimit) goodAssigned++;
            else badAssigned++;
        }
    }
    
    console.log(`Time: ${duration}s`);
    console.log(`Score: ${res.solver_score}`);
    console.log(`Perfect Workers Assigned: ${goodAssigned}`);
    console.log(`Sub-optimal Workers Assigned: ${badAssigned}`);
}

async function runAll() {
    await runRajeshVsSuresh();
    await runBenchmark(50, 100);
    await runBenchmark(100, 200);
    console.log("\nAll tests completed.");
}

runAll().catch(console.error);
