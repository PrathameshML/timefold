const http = require('http');

async function request(method, path, data) {
    return new Promise((resolve, reject) => {
        const req = http.request({
            hostname: 'localhost',
            port: 8083,
            path: path,
            method: method,
            headers: {
                'Content-Type': 'application/json'
            }
        }, res => {
            let body = '';
            res.on('data', d => body += d);
            res.on('end', () => {
                let data = body;
                try { data = JSON.parse(body || '{}'); } catch(e) {}
                resolve({ status: res.statusCode, data: data });
            });
        });
        req.on('error', reject);
        if (data) req.write(JSON.stringify(data));
        req.end();
    });
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function runTests() {
    console.log("=== Running Sanity Tests ===\n");

    try {
        // Test 1: Severity Test
        console.log("Test 1: Severity Test (maximizeRating)");
        
        let payloadA = {
            "shift_name": "Morning Shift",
            "start_date": "2025-05-01",
            "end_date": "2025-05-01",
            "start_time": "08:00",
            "end_time": "16:00",
            "employees": [
                { "id": "EmpA", "wage": 30, "rating": 3, "skills": ["java"], "position": "Developer" },
                { "id": "EmpB", "wage": 32, "rating": 5, "skills": ["java"], "position": "Developer" }
            ],
            "role_limits": [{ "role": "Developer", "max_workers": 1 }],
            "rating_requirements": [{ "role": "Developer", "ratings": [1,2,3,4,5] }],
            "required_skills": { "Developer": ["java"] }
        };

        // Enable maximizeRating SOFT
        await request('PUT', '/constraints', {
            constraints: [{ "constraintId": 12, "severity": "SOFT", "enabled": true, "parameterValue": 100.0 }]
        });
        await request('PUT', '/constraints', {
            constraints: [{ "constraintId": 5, "severity": "SOFT", "enabled": true }] // Wage Optimization
        });

        let res = await request('POST', '/shifts/assign-v2', payloadA);
        console.log("  SOFT Rating, $30/r3 vs $32/r5 -> Winner:", res.data.assignments && res.data.assignments[0] ? res.data.assignments[0].employeeId : "none");

        // Enable maximizeRating HARD
        await request('PUT', '/constraints', {
            constraints: [{ "constraintId": 12, "severity": "HARD", "enabled": true, "parameterValue": 100.0 }]
        });
        
        res = await request('POST', '/shifts/assign-v2', payloadA);
        console.log("  HARD Rating, $30/r3 vs $32/r5 -> Winner:", res.data.assignments && res.data.assignments[0] ? res.data.assignments[0].employeeId : "none");

        // Test 2: Wage Normalization Test
        console.log("\nTest 2: Wage Normalization Test");
        // Reset to SOFT rating
        await request('PUT', '/constraints', {
            constraints: [{ "constraintId": 12, "severity": "SOFT", "enabled": true, "parameterValue": 100.0 }]
        });
        
        let payloadB = JSON.parse(JSON.stringify(payloadA));
        payloadB.employees[1].wage = 100; // EmpB is now $100
        
        res = await request('POST', '/shifts/assign-v2', payloadB);
        console.log("  SOFT Rating, $30/r3 vs $100/r5 -> Winner:", res.data.assignments && res.data.assignments[0] ? res.data.assignments[0].employeeId : "none");


        // Test 3: Skill Match Test
        console.log("\nTest 3: Skill Match Test");
        let payloadC = JSON.parse(JSON.stringify(payloadA));
        payloadC.required_skills = { "Developer": ["java", "sql"] };
        payloadC.employees = [
            { "id": "EmpA", "wage": 30, "rating": 5, "skills": ["java", "sql"], "position": "Developer" },
            { "id": "EmpB", "wage": 30, "rating": 5, "skills": ["java"], "position": "Developer" }
        ];

        // Enable skill match SOFT
        await request('PUT', '/constraints', {
            constraints: [{ "constraintId": 1, "severity": "SOFT", "enabled": true, "parameterValue": 100.0 }]
        });
        
        res = await request('POST', '/shifts/assign-v2', payloadC);
        console.log("  Skills: EmpA[java,sql] vs EmpB[java] -> Winner:", res.data.assignments && res.data.assignments[0] ? res.data.assignments[0].employeeId : "none");

        // Test 4: Dynamic Constraint Disable Test
        console.log("\nTest 4: Dynamic Constraint Disable Test");
        // Disable maximizeRating
        await request('PUT', '/constraints', {
            constraints: [{ "constraintId": 12, "enabled": false }]
        });
        // Now wage is the only differentiator
        res = await request('POST', '/shifts/assign-v2', payloadA);
        console.log("  Disabled Rating, $30/r3 vs $32/r5 -> Winner:", res.data.assignments && res.data.assignments[0] ? res.data.assignments[0].employeeId : "none");

        // Test 5 & 6: Thread Cleanup & Ghost Employee
        console.log("\nTest 5 & 6: Thread Cleanup & Ghost Employee Test");
        let payloadD1 = JSON.parse(JSON.stringify(payloadA));
        payloadD1.required_skills = { "Developer": ["java"] };
        payloadD1.employees = [{ "id": "EmpJava", "wage": 30, "rating": 5, "skills": ["java"], "position": "Developer" }];
        
        let payloadD2 = JSON.parse(JSON.stringify(payloadA));
        payloadD2.required_skills = { "Developer": ["angular"] };
        payloadD2.employees = [{ "id": "EmpAngular", "wage": 30, "rating": 5, "skills": ["angular"], "position": "Developer" }];

        res = await request('POST', '/shifts/assign-v2', payloadD1);
        let res2 = await request('POST', '/shifts/assign-v2', payloadD2);
        
        console.log("  Schedule 1 (Java): Winner =", res.data.assignments && res.data.assignments[0] ? res.data.assignments[0].employeeId : "none");
        console.log("  Schedule 2 (Angular): Winner =", res2.data.assignments && res2.data.assignments[0] ? res2.data.assignments[0].employeeId : "none");
        console.log("  Schedule 2 Score =", res2.data.score);

        // Cleanup
        await request('PUT', '/constraints', {
            constraints: [{ "constraintId": 12, "enabled": true }] // re-enable
        });

    } catch(e) {
        console.error("Error:", e);
    }
}

runTests();
