const fs = require('fs');

async function clearDb() {
    try {
        console.log("Clearing all employees and assignments from DB...");
        const response = await fetch("http://localhost:8083/employees/all", { method: 'DELETE' });
        if (response.ok) {
            console.log("DB Cleared successfully.");
        } else {
            console.log(`Failed to clear DB: ${response.status}`);
        }
    } catch (e) {
        console.log(`Error clearing DB: ${e.message}`);
    }
}

async function runBenchmark(empCount, shiftCount) {
    console.log(`\n===============================================`);
    console.log(`Running Benchmark: ${empCount} employees / ${shiftCount} shifts`);
    console.log(`===============================================`);
    
    const users = [];
    for(let i=1; i<=empCount; i++) {
        const isBest = i <= Math.max(1, Math.floor(empCount / 5)); // 20% are perfect
        users.push({ 
            employee_id: `b_${i}`, name: `Worker ${i}`, role: "Cook", 
            rate: isBest ? 10.0 : 50.0, 
            unit: "hour", 
            rating: isBest ? 5 : 1, 
            employeeType: "Permanent", skills: ["grill", "fryer"] 
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
        roles: [{ role_name: "Cook", rating: 1, max_workers: 5, required_skills: ["grill", "fryer"] }],
        existing_users: users
    };
    
    const startTime = Date.now();
    try {
        const response = await fetch("http://localhost:8083/shifts/assign-v2", {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        
        const endTime = Date.now();
        const durationStr = ((endTime - startTime) / 1000).toFixed(2);
        
        if (!response.ok) {
            const text = await response.text();
            console.log(`ERROR: ${response.status} ${response.statusText}\n${text}`);
            return;
        }
        
        const res = await response.json();
        console.log(`Time Taken: ${durationStr}s`);
        console.log(`Score: ${res.solver_score}`);
        console.log(`Assignments Made: ${res.new_assignments_made}`);
        
        console.log("\nConstraints:");
        if (res.score_explanation) {
            const constraintLines = res.score_explanation.split('\n').filter(l => l.includes('constraint')).slice(0, 15);
            constraintLines.forEach(l => console.log(l));
        }
        
        const goodLimit = Math.max(1, Math.floor(empCount / 5));
        let goodAssigned = 0;
        let badAssigned = 0;
        
        for (const date in res.assignments_by_date) {
            for (const emp of res.assignments_by_date[date]) {
                // Ensure id extraction is safe
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
        


        return {
            empCount,
            shiftCount,
            duration: durationStr,
            score: res.solver_score,
            assignments: res.new_assignments_made,
            goodAssigned,
            badAssigned
        };
    } catch (e) {
        console.log(`ERROR: ${e.message}`);
    }
}

async function main() {
    await clearDb();
    
    const results = [];
    
    console.log("\n--- JVM Warmup ---");
    await runBenchmark(10, 10);
    
    console.log("\n--- Starting Benchmarks ---");
    results.push(await runBenchmark(25, 50));
    results.push(await runBenchmark(50, 100));
    results.push(await runBenchmark(100, 200));
    results.push(await runBenchmark(250, 500));
    results.push(await runBenchmark(500, 1000));
    
    console.log(`\n===============================================`);
    console.log(`BENCHMARK SUMMARY`);
    console.log(`===============================================`);
    console.table(results);
    
    fs.writeFileSync('benchmark_results.json', JSON.stringify(results, null, 2));
}

main();
