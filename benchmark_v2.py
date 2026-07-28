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
        "rate": random.uniform(25, 45) if role == "Nurse" else random.uniform(70, 100),
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

global_payload = {
    "optimization": "both",
    "shifts": shifts_payload
}

def clear_db():
    print("  [DEBUG] Clearing DB...")
    req = urllib.request.Request("http://localhost:8083/shifts/clear-all", method="DELETE")
    try:
        urllib.request.urlopen(req)
    except Exception as e:
        pass

def run_global():
    clear_db()
    req = urllib.request.Request("http://localhost:8083/shifts/assign-global", method="POST")
    req.add_header("Content-Type", "application/json")
    start = time.time()
    resp = urllib.request.urlopen(req, data=json.dumps(global_payload).encode("utf-8"))
    end = time.time()
    res = json.loads(resp.read().decode("utf-8"))
    
    score = res.get("score", "unknown")
    feasible = res.get("is_feasible", False)
    return {
        "wall_time": end - start,
        "solver_time": res.get("solver_time_seconds", 0),
        "assignments": res.get("new_assignments_made", 0),
        "score": score,
        "feasible": feasible
    }

def run_sequential():
    clear_db()
    total_wall_time = 0
    total_solver_time = 0
    total_assignments = 0
    
    # 5 real POST calls to the single-shift endpoint
    scores = []
    feasibles = []
    for s in shifts_payload:
        single_payload = copy.deepcopy(s)
        single_payload["optimization"] = "both"
        
        req = urllib.request.Request("http://localhost:8083/shifts/assign", method="POST")
        req.add_header("Content-Type", "application/json")
        start = time.time()
        try:
            resp = urllib.request.urlopen(req, data=json.dumps(single_payload).encode("utf-8"))
        except urllib.error.HTTPError as e:
            print("HTTP Error:", e.read().decode())
            raise
        end = time.time()
        
        total_wall_time += (end - start)
        res = json.loads(resp.read().decode("utf-8"))
        total_solver_time += res.get("solver_time_seconds", 0)
        total_assignments += res.get("new_assignments_made", 0)
        
        scores.append(res.get("score", "unknown"))
        feasibles.append(res.get("is_feasible", False))
        
    return {
        "wall_time": total_wall_time,
        "solver_time": total_solver_time,
        "assignments": total_assignments,
        "score": scores,
        "feasible": feasibles
    }

if __name__ == "__main__":
    print("Starting Benchmark (3 iterations)...")
    for i in range(1, 2):
        print(f"\n--- Iteration {i} ---")
        
        print("Running Sequential (5 calls to /shifts/assign)...")
        seq_res = run_sequential()
        print(f"Sequential -> Wall: {seq_res['wall_time']:.2f}s, Solver: {seq_res['solver_time']:.2f}s, Assigns: {seq_res['assignments']}")
        print(f"Sequential Scores: {seq_res['score']}")
        print(f"Sequential Feasible: {seq_res['feasible']}")
        
        print("Running Global...")
        global_res = run_global()
        print(f"Global     -> Wall: {global_res['wall_time']:.2f}s, Solver: {global_res['solver_time']:.2f}s, Assigns: {global_res['assignments']}")
        print(f"Global Score: {global_res['score']}")
        print(f"Global Feasible: {global_res['feasible']}")
        
        speedup = seq_res['wall_time'] / global_res['wall_time'] if global_res['wall_time'] > 0 else 0
        print(f"Speedup: {speedup:.2f}x")
