const fs = require('fs');
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

async function runProductionTest() {
    console.log("Reading production payload from testing _req_res.txt...");
    const text = fs.readFileSync('testing _req_res.txt', 'utf-8');
    const startIdx = text.indexOf('{');
    const endIdx = text.indexOf('responce->');
    let jsonStr = text.substring(startIdx, endIdx).trim();
    
    let payload;
    try {
        payload = JSON.parse(jsonStr);
    } catch(e) {
        console.error("Failed to parse JSON:", e);
        return;
    }
    
    console.log("Clearing DB...");
    await request({ hostname: '127.0.0.1', port: 8083, path: '/employees/all', method: 'DELETE' });
    await request({ hostname: '127.0.0.1', port: 8083, path: '/shifts/clear-all', method: 'DELETE' });
    
    // Add some required skills to the payload roles to make it more "production-like"
    payload.roles.find(r => r.role_name === 'CNC Operator').required_skills = ["CNC Programming", "Safety Cert"];
    payload.roles.find(r => r.role_name === 'Helper').required_skills = ["Manual Lifting"];
    payload.roles.find(r => r.role_name === 'Supervisor').required_skills = ["Team Management"];
    
    // Assign skills randomly based on role
    payload.existing_users.forEach((emp, index) => {
        if (emp.role === 'CNC Operator') {
            emp.skills = (index % 2 === 0) ? ["CNC Programming", "Safety Cert"] : ["CNC Programming"];
        } else if (emp.role === 'Helper') {
            emp.skills = (index % 3 === 0) ? [] : ["Manual Lifting"];
        } else if (emp.role === 'Supervisor') {
            emp.skills = ["Team Management", "First Aid"];
        }
    });

    console.log("Sending V2 production payload...");
    const startTime = Date.now();
    const res = await request({ hostname: '127.0.0.1', port: 8083, path: '/shifts/assign-v2', method: 'POST' }, payload);
    const duration = ((Date.now() - startTime) / 1000).toFixed(2);
    
    console.log(`\nTime Taken: ${duration}s`);
    console.log(`Score: ${res.solver_score}`);
    console.log(`Assignments Made: ${res.new_assignments_made}`);
    
    let perfectCount = 0;
    let totalAssigned = 0;
    let permanentCount = 0;
    let highRatingCount = 0;
    
    for (const date in res.assignments_by_date) {
        for (const emp of res.assignments_by_date[date]) {
            totalAssigned++;
            if (emp.employeeType === 'Permanent') permanentCount++;
            if (emp.rating >= 4) highRatingCount++;
        }
    }
    
    console.log(`Total Assigned: ${totalAssigned}`);
    console.log(`Permanent Workers Assigned: ${permanentCount} (${((permanentCount/totalAssigned)*100).toFixed(1)}%)`);
    console.log(`Highly Rated (4-5) Assigned: ${highRatingCount} (${((highRatingCount/totalAssigned)*100).toFixed(1)}%)`);
}

runProductionTest().catch(console.error);
