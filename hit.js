const fs = require('fs');

async function hitSolver() {
    const rawContent = fs.readFileSync('testing _req_res.txt', 'utf8');
    
    // Extract everything between request-> and responce->
    const match = rawContent.match(/request->\s*({[\s\S]*?})\s*responce->/);
    if (!match) {
        console.error("Could not find payload between request-> and responce->");
        return;
    }
    
    let payload;
    try {
        payload = JSON.parse(match[1]);
    } catch(e) {
        console.error("Failed to parse JSON", e);
        return;
    }

    // Disable consecutiveShifts for this test as the user requested
    payload.config = [{ constraintId: 10, severity: "SOFT" }];

    try {
        console.log("Sending payload to solver...");
        const response = await fetch('http://localhost:8083/shifts/assign-v2', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload)
        });
        
        const data = await response.json();
        console.log('Status Code:', response.status);
        if (data.error) {
            console.log('ERROR:', data.error);
        } else {
            console.log('Score:', data.solver_score);
            if (data.score_explanation) {
                console.log('Score Explanation:');
                console.log(data.score_explanation);
            }
        }
    } catch (e) {
        console.error(e);
    }
}

hitSolver();
