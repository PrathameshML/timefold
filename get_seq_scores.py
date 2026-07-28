import json
import random
import urllib.request
import time
import copy

SHIFTS = [
    {"name": "S1", "start": "04:00", "end": "08:00"},
    {"name": "S2", "start": "08:00", "end": "12:00"},
    {"name": "S3", "start": "12:00", "end": "16:00"},
    {"name": "S4", "start": "16:00", "end": "20:00"},
    {"name": "S5", "start": "20:00", "end": "00:00"}
]
DATES = [f"2026-10-{str(d).zfill(2)}" for d in range(1, 8)]

random.seed(42)

employees = []
for i in range(1, 201):
    role = "Nurse" if i <= 150 else "Doctor"
    eligible_shift_names = random.sample([s["name"] for s in SHIFTS], random.choice([2, 3]))
    
    availability = {}
    for date in DATES:
        r = random.random()
        if r < 0.05:
            availability[date] = {"status": "UNAVAILABLE"}
        elif r < 0.15:
            shift_to_cover = next((s for s in SHIFTS if s["name"] in eligible_shift_names), None)
            if shift_to_cover:
                availability[date] = {"status": "PARTIAL", "from": shift_to_cover["start"], "to": shift_to_cover["end"]}
        else:
            availability[date] = {"status": "AVAILABLE"}

    emp = {
        "employee_id": f"EMP_{i}",
        "name": f"Employee {i}",
        "role": role,
        "employeeType": "Permanent",
        "gender": random.choice(["M", "F"]),
        "hourly_wage": random.uniform(25, 45) if role == "Nurse" else random.uniform(70, 100),
        "rating": random.randint(3, 5),
        "eligible_shifts": eligible_shift_names,
        "availability": availability
    }
    employees.append(emp)

shifts_payload = []
for s in SHIFTS:
    shift_users = [e for e in employees if s["name"] in e["eligible_shifts"]]
    
    shift_obj = {
        "shift_name": s["name"],
        "start_date": DATES[0],
        "end_date": DATES[-1],
        "start_time": s["start"],
        "end_time": s["end"],
        "roles": [
            {"role_name": "Nurse", "max_workers": 15},
            {"role_name": "Doctor", "max_workers": 5}
        ],
        "existing_users": shift_users
    }
    shifts_payload.append(shift_obj)

def clear_db():
    req = urllib.request.Request("http://localhost:8083/shifts/clear-all", method="DELETE")
    try:
        urllib.request.urlopen(req)
    except Exception as e:
        pass

clear_db()
print("Cleared DB. Running Sequential 5 shifts...")
scores = []
for s in shifts_payload:
    single_payload = copy.deepcopy(s)
    single_payload["optimization"] = "both"
    
    req = urllib.request.Request("http://localhost:8083/shifts/assign", method="POST")
    req.add_header("Content-Type", "application/json")
    resp = urllib.request.urlopen(req, data=json.dumps(single_payload).encode("utf-8"))
    res = json.loads(resp.read().decode("utf-8"))
    
    score = res.get("solver_score", "unknown")
    scores.append(f"{s['shift_name']}: {score}")

print("Sequential Scores:", scores)
