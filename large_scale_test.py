import json
import urllib.request
import urllib.error

users = []
for i in range(1, 6):
    users.append({ "employee_id": f"e{i}", "name": f"Manager {i}", "role": "Manager", "rate": 50.0, "unit": "hour", "rating": 5, "employeeType": "Permanent", "skills": ["management"] })

for i in range(6, 16):
    rating = (i % 4) + 2
    skills = ["grill", "prep"] if i % 2 == 0 else ["grill"]
    etype = "Contractor" if i % 3 == 0 else "Permanent"
    users.append({ "employee_id": f"e{i}", "name": f"Cook {i}", "role": "Cook", "rate": 25.0, "unit": "hour", "rating": rating, "employeeType": etype, "skills": skills })

for i in range(16, 26):
    rating = (i % 5) + 1
    skills = [] if i == 25 else ["service"]
    users.append({ "employee_id": f"e{i}", "name": f"Waiter {i}", "role": "Waiter", "rate": 12.0, "unit": "hour", "rating": rating, "employeeType": "Permanent", "skills": skills })

roles = [
    { "role_name": "Manager", "rating": 4, "max_workers": 1, "required_skills": ["management"] },
    { "role_name": "Cook", "rating": 3, "max_workers": 3, "required_skills": ["grill", "prep"] },
    { "role_name": "Waiter", "rating": 1, "max_workers": 5, "required_skills": ["service"] }
]

def run_scenario(name, payload):
    print(f"\n===============================================")
    print(f"Running: {name}")
    print(f"===============================================")
    req = urllib.request.Request("http://localhost:8083/shifts/assign-v2", data=json.dumps(payload).encode('utf-8'), headers={'Content-Type': 'application/json'})
    try:
        response = urllib.request.urlopen(req)
        res_data = json.loads(response.read().decode('utf-8'))
        print(f"Score: {res_data.get('solver_score')}")
        print(f"Time: {res_data.get('solver_time_seconds')}s")
        print(f"Entities Planned: {res_data.get('entities_planned')}")
        print("Score Explanation:")
        lines = res_data.get('score_explanation', '').split('\n')
        constraint_lines = [l for l in lines if 'constraint' in l][:20]
        for l in constraint_lines:
            print(l)
    except urllib.error.HTTPError as e:
        print(f"ERROR: {e.code} {e.reason}")
        print(e.read().decode('utf-8'))
    except Exception as e:
        print(f"ERROR: {str(e)}")

payload_morning = {
    "shift_name": "Morning Shift",
    "start_date": "2035-05-01",
    "end_date": "2035-05-07",
    "start_time": "08:00",
    "end_time": "16:00",
    "prioritize_permanent": True,
    "roles": roles,
    "existing_users": users
}

payload_evening = {
    "shift_name": "Evening Shift",
    "start_date": "2035-05-01",
    "end_date": "2035-05-07",
    "start_time": "16:00",
    "end_time": "23:59",
    "prioritize_permanent": True,
    "roles": roles,
    "existing_users": users
}

with open('payload_morning.json', 'w') as f:
    json.dump(payload_morning, f, indent=2)

with open('payload_evening.json', 'w') as f:
    json.dump(payload_evening, f, indent=2)

run_scenario("Morning Shift (7 Days, 25 Employees)", payload_morning)
run_scenario("Evening Shift (7 Days, 25 Employees)", payload_evening)
