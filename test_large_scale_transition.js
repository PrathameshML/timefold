const http = require('http');

const URL = 'http://localhost:8083/shifts/assign-v2';

function generatePayload(severity) {
    const employees = [];
    for (let i = 1; i <= 500; i++) {
        employees.push({
            "employee_id": `EMP${i}`,
            "name": `Worker ${i}`,
            "role": "Cashier",
            "rate": 15 + Math.floor(Math.random() * 20), // 15 to 34
            "rating": Math.floor(Math.random() * 5) + 1,
            "skills": []
        });
    }

    const roles = [{
        "role_name": "Cashier",
        "max_workers": 300 // 300 shifts per day * 7 days = 2100 shifts
    }];

    const constraints = [
        { "name": "everyShiftPlanned", "severity": "HARD" },
        { "name": "maxWeeklyHours", "severity": severity, "value": 40.0 },
        { "name": "consecutiveShifts", "severity": "HARD" },
        { "name": "wageOptimization", "severity": "SOFT" },
        { "name": "skillMatch", "severity": "SOFT", "value": 100 }
    ];

    let endDate = new Date('2026-06-01');
    endDate.setDate(endDate.getDate() + 6);

    return {
        "shift_name": "LargeScale",
        "start_date": "2026-06-01",
        "end_date": endDate.toISOString().split('T')[0],
        "start_time": "08:00",
        "end_time": "16:00",
        "roles": roles,
        "existing_users": employees,
        "active_constraints": constraints,
        "time_limit_seconds": 60,
        "unimproved_time_limit_seconds": 15
    };
}

function runTest(payload, testLabel) {
    return new Promise((resolve) => {
        const postData = JSON.stringify(payload);
        const req = http.request(URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(postData)
            }
        }, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    resolve(JSON.parse(data));
                } catch (e) {
                    resolve({ error: "Parse Error" });
                }
            });
        });
        req.on('error', (e) => resolve({ error: e.message }));
        req.write(postData);
        req.end();
    });
}

function analyze(res, severity) {
    let assignments = 0;
    let overtimeHours = 0;
    let otInstances = 0;

    if (res && res.assignments_by_date) {
        for (const date in res.assignments_by_date) {
            res.assignments_by_date[date].forEach(a => {
                assignments++;
                if (a.ot_hours > 0) {
                    overtimeHours += a.ot_hours;
                    otInstances++;
                }
            });
        }
    }

    console.log(`\n[${severity}] Results`);
    console.log(`Assignments: ${assignments} / 2100`);
    console.log(`Coverage: ${((assignments / 2100) * 100).toFixed(2)}%`);
    console.log(`Total Overtime Hours: ${overtimeHours}`);
    console.log(`Overtime Instances: ${otInstances}`);
    console.log(`Soft Score: ${res.solver_score || "N/A"}`);
}

async function main() {
    console.log("Starting Large Scale Transition Benchmark (500 EMP / 2100 Shifts)");
    const severities = ["HARD", "MEDIUM", "SOFT"];

    for (const sev of severities) {
        console.log(`\nRunning ${sev}...`);
        const payload = generatePayload(sev);
        const res = await runTest(payload, sev);
        analyze(res, sev);
    }
}

main();
