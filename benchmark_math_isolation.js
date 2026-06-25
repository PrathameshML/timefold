const http = require('http');

const URL = 'http://localhost:8083/shifts/assign-v2';

// We disable ALL constraints except the ones we need to test the pure math
function getActiveConstraints(multiplier = 100) {
    return [
        { "name": "everyShiftPlanned", "severity": "HARD" }, // Force it to pick someone
        { "name": "noOverlappingShifts", "severity": "HARD" },
        { "name": "wageOptimization", "severity": "SOFT" },
        { "name": "maximizeRating", "severity": "SOFT", "value": multiplier }
    ];
}

function generatePayload(employees, multiplier = 100, numShifts = 1) {
    const rolesArr = [{ "role_name": "Cashier", "max_workers": numShifts }];
    
    // We make a dummy schedule of just 1 day with 1 shift (requiring numShifts workers)
    return {
        "shift_name": "Math Test Shift",
        "start_date": "2026-06-01",
        "end_date": "2026-06-01",
        "start_time": "08:00",
        "end_time": "16:00",
        "roles": rolesArr,
        "existing_users": employees,
        "active_constraints": getActiveConstraints(multiplier),
        "time_limit_seconds": 3, // very fast since dataset is tiny
        "unimproved_time_limit_seconds": 1
    };
}

function runTest(payload, testName) {
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
                if (res.statusCode !== 200) {
                    console.log(`[${testName}] Error ${res.statusCode}: ${data}`);
                    resolve(null);
                    return;
                }
                const result = JSON.parse(data);
                resolve(result);
            });
        });

        req.on('error', (e) => {
            console.error(`[${testName}] Request failed: ${e.message}`);
            resolve(null);
        });

        req.write(postData);
        req.end();
    });
}

async function main() {
    console.log("==========================================");
    console.log("TEST 1 - Rating Wins Small Cost Difference");
    console.log("==========================================");
    const emps1 = [
        { "employee_id": "A", "name": "A", "role": "Cashier", "rate": 30, "rating": 3 },
        { "employee_id": "B", "name": "B", "role": "Cashier", "rate": 32, "rating": 5 }
    ];
    let res1 = await runTest(generatePayload(emps1), "Test 1");
    if (res1 && res1.assignments_by_date) {
        const assigned = res1.assignments_by_date["2026-06-01"][0].employeeName;
        console.log(`Worker ${assigned} was picked! (Expected: B)`);
    }

    console.log("\n==========================================");
    console.log("TEST 2 - Wage Wins Large Cost Difference");
    console.log("==========================================");
    const emps2 = [
        { "employee_id": "A", "name": "A", "role": "Cashier", "rate": 30, "rating": 3 },
        { "employee_id": "B", "name": "B", "role": "Cashier", "rate": 50, "rating": 5 }
    ];
    let res2 = await runTest(generatePayload(emps2), "Test 2");
    if (res2 && res2.assignments_by_date) {
        const assigned = res2.assignments_by_date["2026-06-01"][0].employeeName;
        console.log(`Worker ${assigned} was picked! (Expected: A)`);
    }

    console.log("\n==========================================");
    console.log("TEST 3 - Find Crossover Point");
    console.log("==========================================");
    const wages = [31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 45, 50];
    for (const w of wages) {
        const emps3 = [
            { "employee_id": "A", "name": "A", "role": "Cashier", "rate": 30, "rating": 3 },
            { "employee_id": "B", "name": "B", "role": "Cashier", "rate": w, "rating": 5 }
        ];
        let res3 = await runTest(generatePayload(emps3), `Test 3 (${w})`);
        if (res3 && res3.assignments_by_date) {
            const assigned = res3.assignments_by_date["2026-06-01"][0].employeeName;
            console.log(`B Wage $${w} -> Picked Worker ${assigned}`);
        }
    }

    console.log("\n==========================================");
    console.log("TEST 4 - Rating Multiplier Curve");
    console.log("==========================================");
    // Create a pool of 20 workers with random wages and ratings
    // We want 10 shifts filled so it has to pick the top 50%
    const emps4 = [];
    for(let i=1; i<=20; i++) {
        emps4.push({
            "employee_id": `W${i}`,
            "name": `Worker ${i}`,
            "role": "Cashier",
            "rate": 10 + (i * 2), // 12 to 50
            "rating": Math.floor(Math.random() * 5) + 1 // 1 to 5
        });
    }

    const multipliers = [0, 25, 50, 100, 200, 500, 1000];
    console.log("| Multiplier | Avg Rating | Avg Wage | Total Soft Score |");
    console.log("| :--- | :--- | :--- | :--- |");
    
    // We will use 5s time limit since it's a tiny dataset (20 workers, 10 shifts)
    for (const m of multipliers) {
        let payload = generatePayload(emps4, m, 10);
        payload.time_limit_seconds = 5;
        payload.unimproved_time_limit_seconds = 2;
        
        let res5 = await runTest(payload, `Test 5 (M=${m})`);
        if (res5 && res5.assignments_by_date) {
            let totalW = 0;
            let totalR = 0;
            const shifts = res5.assignments_by_date["2026-06-01"] || [];
            shifts.forEach(s => {
                totalW += s.wage;
                totalR += s.rating;
            });
            const avgR = (totalR / shifts.length).toFixed(2);
            const avgW = (totalW / shifts.length).toFixed(2);
            const softScore = res5.solver_score ? res5.solver_score : "N/A";
            console.log(`| ${m} | ${avgR} | $${avgW} | ${softScore} |`);
        }
    }
}

main();
