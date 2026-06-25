const http = require('http');

const validShiftCost = {
  "shift_name": "Morning",
  "start_date": "2027-01-19",
  "end_date": "2027-01-19",
  "start_time": "06:00",
  "end_time": "14:00",
  "optimization": "cost",
  "roles": [{ "role_name": "Helper", "rating": 2, "max_workers": 2 }],
  "existing_users": [
    { "employee_id": "EMP-01", "name": "Cheap", "rate": 10.0, "unit": "hour", "rating": 2, "role": "Helper" },
    { "employee_id": "EMP-02", "name": "Expensive", "rate": 25.0, "unit": "hour", "rating": 5, "role": "Helper" }
  ]
};

const validShiftQuality = {
  "shift_name": "Evening",
  "start_date": "2027-01-19",
  "end_date": "2027-01-19",
  "start_time": "14:00",
  "end_time": "22:00",
  "optimization": "quality",
  "roles": [{ "role_name": "Helper", "rating": 2, "max_workers": 2 }],
  "existing_users": [
    { "employee_id": "EMP-01", "name": "Cheap", "rate": 10.0, "unit": "hour", "rating": 2, "role": "Helper" },
    { "employee_id": "EMP-02", "name": "Expensive", "rate": 25.0, "unit": "hour", "rating": 5, "role": "Helper" }
  ]
};

const invalidShift = {
  "shift_name": "Night",
  "start_date": "2026-01-19",
  // Missing end_date, start_time, end_time, etc.
  "optimization": "cost"
};

function sendRequest(path, payload, testName) {
  return new Promise((resolve) => {
    const data = JSON.stringify(payload);
    const req = http.request({
      hostname: 'localhost',
      port: 8083,
      path: path,
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Content-Length': data.length }
    }, (res) => {
      let body = '';
      res.on('data', d => body += d);
      res.on('end', () => {
        console.log(`\n--- Test: ${testName} ---`);
        console.log(`Status: ${res.statusCode}`);
        try {
          const json = JSON.parse(body);
          if (res.statusCode === 200 && json.shift_results) {
             console.log("Overall Stats:", json.overall_statistics);
             json.shift_results.forEach(sr => {
                 console.log(`Shift ${sr.shift_index} (${sr.shift_name}) Score: ${sr.solver_score}`);
             });
          } else if (res.statusCode === 400) {
             console.log("Validation Error:", json);
          } else {
             console.log("Response Keys:", Object.keys(json));
             if (json.solver_score) console.log("Score:", json.solver_score);
             console.log("Assignments Made:", json.new_assignments_made);
             if (json.debug_log) console.log("Debug Log:", json.debug_log);
             if (json.score_explanation) console.log("Explanation:", json.score_explanation.substring(0, 500));
          }
        } catch (e) {
          console.log("Raw Response:", body.substring(0, 200));
        }
        resolve();
      });
    });
    
    req.on('error', (e) => {
      console.error(`Request error in ${testName}:`, e.message);
      resolve();
    });
    
    req.write(data);
    req.end();
  });
}

async function runTests() {
  console.log("Starting Regression Tests...");
  
  await sendRequest('/shifts/assign-v2', validShiftCost, "1. Single /assign-v2 (Should return 200)");
  
  await sendRequest('/shifts/batch-assign-v2', { shifts: [validShiftCost, validShiftQuality] }, "2. Batch with 2 valid shifts (cost vs quality)");
  
  await sendRequest('/shifts/batch-assign-v2', { shifts: [validShiftCost, invalidShift] }, "3. Batch with 1 invalid shift (Should return 400 for whole batch)");

  console.log("\nTests completed.");
}

runTests();
