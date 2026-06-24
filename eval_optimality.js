const fs = require('fs');

function solve() {
    const text = fs.readFileSync('C:\\Users\\user\\Downloads\\timefold\\timefold\\testing _req_res.txt', 'utf-8');
    
    // The file contains two JSON objects. One for the request, one for the response.
    // They might be separated by anything.
    const firstBrace = text.indexOf('{');
    const secondBrace = text.indexOf('{', firstBrace + 1);
    const splitIdx = text.lastIndexOf('\n{');
    
    let requestJsonStr = text.substring(firstBrace, splitIdx).trim();
    let responseJsonStr = text.substring(splitIdx).trim();
    
    if (requestJsonStr.endsWith('responce->')) {
        requestJsonStr = requestJsonStr.substring(0, requestJsonStr.length - 10).trim();
    }
    
    let payload;
    try {
        payload = JSON.parse(requestJsonStr);
    } catch (e) {
        console.error("Failed to parse request JSON", e);
        return;
    }
    
    // FILTER TO FIRST 50 EMPLOYEES (IDs 101 to 150) since the user hasn't saved the file yet
    let users = payload.existing_users;
    users = users.filter(u => parseInt(u.employee_id) <= 150);
    
    const rolesReq = {
        "CNC Operator": { rating: 4, count: 8 },
        "Helper": { rating: 2, count: 12 },
        "Supervisor": { rating: 5, count: 2 }
    };
    
    let theoreticalMaxScore = 0;
    
    console.log(`=== OPTIMAL WORKER SELECTION (Pool Size: ${users.length}) ===`);
    for (const [roleName, req] of Object.entries(rolesReq)) {
        const validUsers = users.filter(u => u.role === roleName && u.rating >= req.rating);
        
        const usersWithScore = validUsers.map(u => {
            const reward = u.rating * 40;
            const wagePen = u.rate * 8;
            const permPen = u.employeeType === 'Permanent' ? 0 : 100;
            const net = reward - wagePen - permPen;
            return { ...u, netScore: net, reward, wagePen, permPen };
        });
        
        usersWithScore.sort((a, b) => b.netScore - a.netScore);
        
        const selected = usersWithScore.slice(0, req.count);
        
        console.log(`\nTop ${req.count} for ${roleName}:`);
        let roleScorePerShift = 0;
        selected.forEach((u, i) => {
            console.log(`${i+1}. [${u.employee_id}] ${u.name} (Net: ${u.netScore})`);
            roleScorePerShift += u.netScore;
        });
        
        const roleTotalFor5Days = roleScorePerShift * 5;
        theoreticalMaxScore += roleTotalFor5Days;
    }
    
    console.log(`\n=== RESULTS ===`);
    console.log(`Theoretical Optimal Soft Score (for 5 days): ${theoreticalMaxScore}`);
    
    // Hardcode the user's latest response score from their unsaved editor diff
    console.log(`Solver Actual Soft Score (From Unsaved Diff): 0hard/0medium/3868soft`);
    
    const difference = theoreticalMaxScore - 3868;
    console.log(`Difference: ${difference} soft points`);
    if (difference > 0) {
        console.log("\nCONCLUSION: The solver solution is SUB-OPTIMAL.");
    } else {
        console.log("\nCONCLUSION: The solver solution is PERFECTLY OPTIMAL.");
    }
}

solve();
