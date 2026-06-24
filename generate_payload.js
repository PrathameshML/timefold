const fs = require('fs');

const roles = ["Bartender", "Cook", "Waiter", "Dishwasher", "Host"];
const employees = [];

let idCounter = 1;
for (const role of roles) {
    for (let i = 1; i <= 5; i++) {
        const isPermanent = i % 2 === 1; // Odd ones are permanent, even are contract
        const rating = Math.floor(Math.random() * 3) + 3; // 3 to 5 stars
        const rate = (Math.random() * 5 + 10).toFixed(2); // 10.00 to 15.00
        
        employees.push({
            employee_id: `e${idCounter}`,
            name: `${role} ${i}`,
            rate: parseFloat(rate),
            unit: "hour",
            rating: rating,
            role: role,
            gender: i % 2 === 0 ? "Female" : "Male",
            employeeType: isPermanent ? "Permanent" : "Contract"
        });
        idCounter++;
    }
}

const payload = {
  shift_name: "Evening",
  start_date: "2026-02-01",
  end_date: "2026-02-05",
  start_time: "16:00",
  end_time: "22:00",
  prioritizePermanent: true,
  schedule_breaks: true,
  roles: [
    { role_name: "Bartender", rating: 3, max_workers: 3 },
    { role_name: "Cook", rating: 4, max_workers: 2 },
    { role_name: "Waiter", rating: 3, max_workers: 4 },
    { role_name: "Dishwasher", rating: 2, max_workers: 2 },
    { role_name: "Host", rating: 4, max_workers: 1 }
  ],
  existing_users: employees
};

fs.writeFileSync('new_req_25.json', JSON.stringify(payload, null, 2));
console.log("Created new_req_25.json with 25 employees.");
