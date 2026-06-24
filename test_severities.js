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

const basePayload = require('./test_payload.json');

function runTest(severity) {
    return new Promise((resolve) => {
        const payload = JSON.parse(JSON.stringify(basePayload));
        
        // Set competing constraints
        payload.active_constraints = [
            {
                name: "maxWeeklyHours",
                severity: severity,
                value: 4.0
            },
            {
                name: "everyShiftPlanned",
                severity: "MEDIUM"
            },
            {
                name: "wageOptimization",
                severity: "SOFT"
            }
        ];

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
                let employeesWithOvertime = 0;
                let totalOvertime = 0;
                
                const hoursByEmployee = {};
                
                if (result.assignments_by_date) {
                    for (const [date, dateAssignments] of Object.entries(result.assignments_by_date)) {
                        totalAssignments += dateAssignments.length;
                        dateAssignments.forEach(a => {
                            hoursByEmployee[a.employee_id] = (hoursByEmployee[a.employee_id] || 0) + a.shift_duration_hours;
                        });
                    }
                    
                    for (const [empId, hours] of Object.entries(hoursByEmployee)) {
                        if (hours > 4.0) { // Using 4.0 because that's our threshold for the test
                            totalOvertime += (hours - 4.0);
                            employeesWithOvertime++;
                        }
                    }
                }

                console.log(`\n=== Test: maxWeeklyHours = ${severity} ===`);
                console.log(`Assignments: ${totalAssignments}`);
                console.log(`Employees with OT: ${employeesWithOvertime}`);
                console.log(`Total OT Hours: ${totalOvertime}`);
                
                resolve();
            });
        });

        req.on('error', (error) => {
            console.error(`Error with ${severity} test:`, error);
            resolve();
        });

        req.write(JSON.stringify(payload));
        req.end();
    });
}

async function runAllTests() {
    console.log("Running maxWeeklyHours severity tests...");
    await runTest("HARD");
    await runTest("MEDIUM");
    await runTest("SOFT");
}

runAllTests();
