const fs = require('fs');

const payload = {
  "shift_name": "Morning",
  "start_date": "2026-02-01",
  "end_date": "2026-02-05",
  "start_time": "08:00",
  "end_time": "16:00",
  "prioritizePermanent": false,
  "schedule_breaks": false,
  "config": [
    // We strictly need everyShiftPlanned so it fills the shifts.
    // If we disable it, it might still fill them because of maximizeRating reward,
    // but let's leave it enabled to match the basic requirements.
    { "constraintId": 4, "severity": "MEDIUM" }
  ],
  "roles": [
    { "role_name": "CNC Operator", "rating": 3, "max_workers": 1 }
  ],
  "existing_users": [
    {
      "employee_id": "101",
      "name": "Rajesh Kumar",
      "rate": 15.0,
      "unit": "hour",
      "rating": 5,
      "role": "CNC Operator",
      "gender": "Male",
      "employeeType": "Permanent"
    },
    {
      "employee_id": "106",
      "name": "Suresh Verma",
      "rate": 14.0,
      "unit": "hour",
      "rating": 3,
      "role": "CNC Operator",
      "gender": "Male",
      "employeeType": "Permanent"
    }
  ]
};

fs.writeFileSync('test_rajesh_suresh.json', JSON.stringify(payload, null, 2));

async function runTest() {
    console.log("Sending tiny test to solver...");
    const response = await fetch('http://localhost:8083/shifts/assign-v2', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(payload)
    });
    const data = await response.json();
    
    console.log("Score:", data.solver_score);
    
    console.log(JSON.stringify(data, null, 2));
}

runTest();
