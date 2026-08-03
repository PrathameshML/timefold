import json
import random

random.seed(42)

roles = ["Developer", "QA", "Designer", "DevOps", "Manager"]
categories = ["Permanent", "Contract"]
genders = ["Male", "Female"]
first_names = ["Alex", "Jordan", "Sam", "Casey", "Morgan", "Riley", "Taylor", "Quinn", "Drew", "Blake",
               "Avery", "Cameron", "Dakota", "Emery", "Finley", "Harper", "Hayden", "Jamie", "Kendall", "Logan"]
last_names = ["Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez",
              "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson", "Martin"]

# Generate 130 employees (26 per role)
employees = []
for i in range(1, 131):
    role = roles[i % len(roles)]
    emp = {
        "employee_id": f"EMP{i:04d}",
        "name": f"{random.choice(first_names)} {random.choice(last_names)}",
        "role": role,
        "employeeType": random.choice(categories),
        "gender": random.choice(genders),
        "rate": round(random.uniform(15, 45), 2),
        "unit": "hour",
        "rating": random.randint(3, 5)
    }
    employees.append(emp)

# 4 shifts, same 140 total slots/day -> actually 122 slots/day * 7 days = 854 slots
shifts = [
    {
        "shift_name": "Morning Shift",
        "start_date": "2026-08-04",
        "end_date": "2026-08-10",
        "start_time": "06:00",
        "end_time": "14:00",
        "roles": [
            {"role_name": "Developer", "max_workers": 10, "rating": 3},
            {"role_name": "QA", "max_workers": 8, "rating": 3},
            {"role_name": "Designer", "max_workers": 7, "rating": 3},
            {"role_name": "DevOps", "max_workers": 5, "rating": 3},
            {"role_name": "Manager", "max_workers": 5, "rating": 3}
        ],
        "existing_users": employees
    },
    {
        "shift_name": "Afternoon Shift",
        "start_date": "2026-08-04",
        "end_date": "2026-08-10",
        "start_time": "14:00",
        "end_time": "22:00",
        "roles": [
            {"role_name": "Developer", "max_workers": 10, "rating": 3},
            {"role_name": "QA", "max_workers": 8, "rating": 3},
            {"role_name": "Designer", "max_workers": 7, "rating": 3},
            {"role_name": "DevOps", "max_workers": 5, "rating": 3},
            {"role_name": "Manager", "max_workers": 5, "rating": 3}
        ],
        "existing_users": employees
    },
    {
        "shift_name": "Evening Shift",
        "start_date": "2026-08-04",
        "end_date": "2026-08-10",
        "start_time": "18:00",
        "end_time": "02:00",
        "roles": [
            {"role_name": "Developer", "max_workers": 8, "rating": 3},
            {"role_name": "QA", "max_workers": 6, "rating": 3},
            {"role_name": "Designer", "max_workers": 5, "rating": 3},
            {"role_name": "DevOps", "max_workers": 4, "rating": 3},
            {"role_name": "Manager", "max_workers": 3, "rating": 3}
        ],
        "existing_users": employees
    },
    {
        "shift_name": "Night Shift",
        "start_date": "2026-08-04",
        "end_date": "2026-08-10",
        "start_time": "22:00",
        "end_time": "06:00",
        "roles": [
            {"role_name": "Developer", "max_workers": 8, "rating": 3},
            {"role_name": "QA", "max_workers": 6, "rating": 3},
            {"role_name": "Designer", "max_workers": 5, "rating": 3},
            {"role_name": "DevOps", "max_workers": 4, "rating": 3},
            {"role_name": "Manager", "max_workers": 3, "rating": 3}
        ],
        "existing_users": employees
    }
]

# Low slack: 130 employees * 7 days = 910 total capacity
# Total slots: 854. Very little room for maneuvering.

payload = {
    "shifts": shifts,
    "time_limit_seconds": 60,
    "unimproved_time_limit_seconds": 20
}

with open("c:/Users/user/Downloads/timefold/timefold/benchmark_payload_low_slack.json", "w") as f:
    json.dump(payload, f, indent=2)

print("Payload written to benchmark_payload_low_slack.json")
