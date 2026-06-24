const payload = {
  "shift_name": "Morning",
  "start_date": "2026-01-19",
  "end_date": "2026-01-23",
  "start_time": "06:00",
  "end_time": "14:00",
  "prioritizePermanent": true,
  "schedule_breaks": true,
  
  "roles": [
    { 
      "role_name": "CNC Operator", 
      "rating": 4, 
      "max_workers": 2,
      "required_skills": ["CNC Programming", "Safety Cert Level 2"]
    },
    { 
      "role_name": "Helper", 
      "rating": 2, 
      "max_workers": 3,
      "required_skills": ["Manual Lifting"]
    },
    { 
      "role_name": "Supervisor", 
      "rating": 5, 
      "max_workers": 1,
      "required_skills": ["First Aid", "Team Management"]
    }
  ],
  
  "existing_users": [
    { 
      "employee_id": "101", 
      "name": "Rajesh Kumar", 
      "role": "CNC Operator", 
      "rating": 5, 
      "rate": 15.0, 
      "unit": "hour", 
      "gender": "Male", 
      "employeeType": "Permanent",
      "skills": ["CNC Programming", "Safety Cert Level 2", "Basic Maintenance"]
    },
    { 
      "employee_id": "102", 
      "name": "Priya Sharma", 
      "role": "Helper", 
      "rating": 3, 
      "rate": 8.0, 
      "unit": "hour", 
      "gender": "Female", 
      "employeeType": "Permanent",
      "skills": ["Manual Lifting", "Forklift Operation"]
    },
    { 
      "employee_id": "103", 
      "name": "Amit Patel", 
      "role": "CNC Operator", 
      "rating": 4, 
      "rate": 16.0, 
      "unit": "hour", 
      "gender": "Male", 
      "employeeType": "Contract",
      "skills": ["CNC Programming"] 
    },
    { 
      "employee_id": "105", 
      "name": "Vikram Singh", 
      "role": "Supervisor", 
      "rating": 5, 
      "rate": 25.0, 
      "unit": "hour", 
      "gender": "Male", 
      "employeeType": "Permanent",
      "skills": ["First Aid", "Team Management", "Conflict Resolution"]
    }
  ]
};

async function testSkills() {
    console.log("Clearing all employees and shifts...");
    await fetch("http://127.0.0.1:8083/employees/all", { method: 'DELETE' });
    await fetch("http://127.0.0.1:8083/shifts/clear-all", { method: 'DELETE' });
    
    console.log("Sending V2 solve payload with skills...");
    const res = await fetch("http://127.0.0.1:8083/shifts/assign-v2", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    });

    const data = await res.json();
    console.log(`Solver Score: ${data.solver_score}`);
    console.log(`Active Constraints:`);
    console.log(JSON.stringify(data.active_constraints.filter(c => c.name === 'skillMatch'), null, 2));
    
    console.log(`\nAssignments for Jan 19:`);
    const dayAssignments = data.assignments_by_date["2026-01-19"];
    
    if (dayAssignments) {
        dayAssignments.forEach(a => {
            console.log(`- ${a.employeeName} (${a.role})`);
            console.log(`  Skills: ${a.skills.join(', ') || 'none'}`);
        });
    } else {
        console.log("No assignments generated.");
    }
}

testSkills().catch(console.error);
