const http = require('http');

const options = {
    hostname: 'localhost',
    port: 8083,
    path: '/shifts/assign-v2',
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
    }
};

const basePayload = require('./dataset_500_convergence.json');

function runTest(multiplier) {
    return new Promise((resolve) => {
        const payload = JSON.parse(JSON.stringify(basePayload));
        
        // Define standard constraints, modifying maximizeRating multiplier
        payload.active_constraints = [
            { name: "skillMatch", severity: "HARD" },
            { name: "everyShiftPlanned", severity: "MEDIUM" },
            { name: "wageOptimization", severity: "SOFT" },
            { name: "maxDailyHours", severity: "HARD", value: 12.0 },
            { name: "maxWeeklyHours", severity: "MEDIUM", value: 40.0 },
            { name: "consecutiveShifts", severity: "SOFT" },
            { name: "permanentPriority", severity: "SOFT" },
            { name: "maximizeRating", severity: "SOFT", value: multiplier }
        ];
        
        payload.time_limit_seconds = 30; // 30 seconds per run to save time
        
        const req = http.request(options, (res) => {
            let data = '';
            res.on('data', (chunk) => { data += chunk; });
            res.on('end', () => {
                if (res.statusCode >= 400) {
                    console.error(`Error: HTTP ${res.statusCode} - ${data.substring(0, 200)}...`);
                    resolve();
                    return;
                }
                
                let result;
                try {
                    result = JSON.parse(data);
                } catch (e) {
                    console.error('Failed to parse JSON:', data.substring(0, 200));
                    resolve();
                    return;
                }
                
                let totalAssignments = 0;
                let totalRating = 0;
                let totalWage = 0;
                
                if (result.assignments_by_date) {
                    for (const [date, dateAssignments] of Object.entries(result.assignments_by_date)) {
                        totalAssignments += dateAssignments.length;
                        dateAssignments.forEach(a => {
                            totalRating += a.rating;
                            let shift_hours = a.regular_hours + (a.ot_hours || 0);
                            totalWage += (a.wage * shift_hours);
                        });
                    }
                }
                
                const avgRating = totalAssignments > 0 ? (totalRating / totalAssignments).toFixed(2) : 0;
                
                console.log(`| ${multiplier} | ${totalAssignments} | ${avgRating} | $${totalWage.toFixed(2)} |`);
                resolve();
            });
        });

        req.on('error', (error) => {
            console.error(`Error with ${multiplier} test:`, error);
            resolve();
        });

        req.write(JSON.stringify(payload));
        req.end();
    });
}

async function runAllTests() {
    console.log("Running maximizeRating multiplier benchmark...");
    console.log("| Multiplier | Coverage | Avg Rating | Total Wage |");
    console.log("| :--- | :--- | :--- | :--- |");
    await runTest(0);
    await runTest(50);
    await runTest(100);
    await runTest(200);
}

runAllTests();
