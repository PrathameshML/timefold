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

async function runSmallBenchmark() {
    const empCount = 10;
    const shiftCount = 20; // 4 days * 5 workers per day
    
    console.log(`\n===============================================`);
    console.log(`Running Correctness Benchmark: ${empCount} employees / ${shiftCount} shifts`);
    console.log(`===============================================`);
    
    const users = [];
    // 2 perfect workers, 8 bad workers
    for(let i=1; i<=empCount; i++) {
        const isBest = i <= 2; // 2 perfect workers
        users.push({ 
            employee_id: `b_${i}`, name: `Worker ${i}`, role: "Cook", 
            rate: isBest ? 10.0 : 50.0, 
            unit: "hour", 
            rating: isBest ? 5 : 1, 
            employeeType: "Permanent", skills: ["grill", "fryer"] 
        });
    }
    
    const days = 4;
    const startDate = new Date("2050-03-01");
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
        
        const goodLimit = 2;
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
        
        console.log(`\nQuality Check (Mathematical Optimum: 8 Perfect, 12 Sub-optimal):`);
        console.log(`  Perfect Workers ($10/hr) Assigned: ${goodAssigned}`);
        console.log(`  Sub-optimal Workers ($50/hr) Assigned: ${badAssigned}`);
        
        if (goodAssigned === 8 && badAssigned === 12) {
            console.log("\n✅ RESULT: PERFECT MATHEMATICAL OPTIMUM REACHED!");
        } else {
            console.log(`\n❌ RESULT: FAILED TO REACH OPTIMUM (Gap: ${8 - goodAssigned})`);
        }
        
    } catch (e) {
        console.log(`ERROR: ${e.message}`);
    }
}

async function main() {
    await clearDb();
    await runSmallBenchmark();
}

main();
