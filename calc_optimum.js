const fs = require('fs');
const text = fs.readFileSync('C:\\Users\\user\\Downloads\\timefold\\timefold\\testing _req_res.txt', 'utf-8');
const firstBrace = text.indexOf('{');
const splitIdx = text.lastIndexOf('\n{');
let requestJsonStr = text.substring(firstBrace, splitIdx).trim();
if (requestJsonStr.endsWith('responce->')) {
    requestJsonStr = requestJsonStr.substring(0, requestJsonStr.length - 10).trim();
}
let payload = JSON.parse(requestJsonStr);
let users = payload.existing_users.filter(u => parseInt(u.employee_id) <= 150);

let slots = [];
users.forEach(u => {
    const net = u.rating * 40 - u.rate * 8 - (u.employeeType === 'Permanent' ? 0 : 100);
    // 5 slots per worker
    for (let i = 0; i < 5; i++) {
        slots.push({ id: u.employee_id, role: u.role, net });
    }
});

// Sort slots by net score DESC
slots.sort((a, b) => b.net - a.net);

// Role limits per week
const required = {
    'CNC Operator': 8 * 5, // 40
    'Helper': 12 * 5,      // 60
    'Supervisor': 2 * 5    // 10
};

let optimalScore = 0;
let assigned = 0;

for (const role in required) {
    const roleSlots = slots.filter(s => s.role === role);
    for (let i = 0; i < required[role]; i++) {
        optimalScore += roleSlots[i].net;
        assigned++;
    }
}

console.log("Optimal Score:", optimalScore);
console.log("Assigned Slots:", assigned);
