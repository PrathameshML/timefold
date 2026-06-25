const http = require('http');

const URL = 'http://localhost:8083/shifts/assign-v2';

const payload = {
    "shift_name": "Test Shift",
    "start_date": "2026-07-05",
    "end_date": "2026-07-05",
    "start_time": "09:00",
    "end_time": "17:00",
    "roles": [{ "role_name": "Cashier", "max_workers": 7, "required_skills": ["Cashier"] }],
    "existing_users": [
        { "employee_id": "BugE11", "name": "Slacker Karen", "role": "Cashier", "rate": 15, "rating": 2, "employeeType": "Permanent", "skills": ["Cashier"] },
        { "employee_id": "BugE13", "name": "Slacker Mia", "role": "Cashier", "rate": 15, "rating": 2, "employeeType": "Contract", "skills": ["Cashier"] },
        { "employee_id": "BugE14", "name": "Slacker Noah", "role": "Cashier", "rate": 15, "rating": 2, "employeeType": "Contract", "skills": ["Cashier"] },
        { "employee_id": "BugE1", "name": "Perfect Alice", "role": "Cashier", "rate": 15, "rating": 5, "employeeType": "Permanent", "skills": ["Cashier"] },
        { "employee_id": "BugE2", "name": "Perfect Bob", "role": "Cashier", "rate": 15, "rating": 5, "employeeType": "Permanent", "skills": ["Cashier"] },
        { "employee_id": "BugE5", "name": "Perfect Eve", "role": "Cashier", "rate": 15, "rating": 5, "employeeType": "Permanent", "skills": ["Cashier"] },
        { "employee_id": "BugE9", "name": "Cheap Star Ivy", "role": "Cashier", "rate": 15, "rating": 5, "employeeType": "Contract", "skills": ["Cashier"] }
    ],
    "active_constraints": [
        { "name": "everyShiftPlanned", "severity": "HARD" },
        { "name": "noOverlappingShifts", "severity": "HARD" },
        { "name": "wageOptimization", "severity": "SOFT" },
        { "name": "maximizeRating", "severity": "SOFT", "value": 100.0 }
    ],
    "time_limit_seconds": 2,
    "schedule_breaks": false
};

async function runTest() {
    console.log("Submitting First Request...");
    let res1 = await submitRequest(payload);
    console.log(`First Request Score: ${res1.solver_score}`);
    console.log(`Assigned: ${res1.new_assignments_made}`);

    console.log("\nSubmitting Second Request (Exact Same Payload)...");
    let res2 = await submitRequest(payload);
    console.log(`Second Request Score: ${res2.solver_score}`);
    console.log(`Assigned: ${res2.new_assignments_made}`);
}

function submitRequest(dataObj) {
    return new Promise((resolve) => {
        const postData = JSON.stringify(dataObj);
        const req = http.request(URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(postData)
            }
        }, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => resolve(JSON.parse(data)));
        });
        req.on('error', (e) => resolve({error: e}));
        req.write(postData);
        req.end();
    });
}

runTest();
