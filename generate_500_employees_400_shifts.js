const fs = require("fs");

const START_DATE = new Date("2026-07-01T00:00:00Z");
const DAYS = 7;
const SHIFT_COUNT = 400;
const EMPLOYEE_COUNT = 500;

const roleSpecs = [
  { role_name: "Bartender", rating: 2, max_workers: 2, required_skills: ["Mixology"] },
  { role_name: "Cook", rating: 3, max_workers: 2, required_skills: ["Grill"] },
  { role_name: "Waiter", rating: 1, max_workers: 3, required_skills: ["Customer Service"] },
  { role_name: "Dishwasher", rating: 1, max_workers: 2, required_skills: ["Cleaning"] },
  { role_name: "Host", rating: 2, max_workers: 1, required_skills: ["Front Desk"] }
];

const roleSkills = {
  Bartender: ["Mixology"],
  Cook: ["Grill"],
  Waiter: ["Customer Service"],
  Dishwasher: ["Cleaning"],
  Host: ["Front Desk"]
};

const roleRates = {
  Bartender: 18,
  Cook: 24,
  Waiter: 14,
  Dishwasher: 12,
  Host: 15
};

function formatDate(date) {
  return date.toISOString().slice(0, 10);
}

function pad2(value) {
  return String(value).padStart(2, "0");
}

function timeFromMinutes(minutes) {
  const normalized = ((minutes % 1440) + 1440) % 1440;
  return `${pad2(Math.floor(normalized / 60))}:${pad2(normalized % 60)}`;
}

function addDays(date, days) {
  const next = new Date(date);
  next.setUTCDate(next.getUTCDate() + days);
  return next;
}

const employees = [];
for (let i = 1; i <= EMPLOYEE_COUNT; i++) {
  const spec = roleSpecs[(i - 1) % roleSpecs.length];
  const role = spec.role_name;
  const hasRequiredSkill = i % 10 !== 0;

  employees.push({
    employee_id: `emp_${pad2(Math.ceil(i / 100))}_${String(i).padStart(4, "0")}`,
    name: `${role} Employee ${i}`,
    role,
    rating: (i % 5) + 1,
    rate: roleRates[role] + (i % 6),
    unit: "hour",
    employeeType: i % 3 === 0 ? "Contract" : "Permanent",
    gender: i % 2 === 0 ? "Female" : "Male",
    skills: hasRequiredSkill ? roleSkills[role] : []
  });
}

const shifts = [];
for (let i = 0; i < SHIFT_COUNT; i++) {
  const dayIndex = i % DAYS;
  const date = formatDate(addDays(START_DATE, dayIndex));
  const slotInDay = Math.floor(i / DAYS);
  const startMinutes = 6 * 60 + (slotInDay % 18) * 60;
  const durationMinutes = 6 * 60;

  shifts.push({
    shift_name: `Week 1 Shift ${String(i + 1).padStart(3, "0")}`,
    start_date: date,
    end_date: date,
    start_time: timeFromMinutes(startMinutes),
    end_time: timeFromMinutes(startMinutes + durationMinutes),
    prioritizePermanent: true,
    schedule_breaks: true,
    roles: roleSpecs,
    existing_users: employees
  });
}

const request = { shifts };
fs.writeFileSync("request_500_employees_400_shifts.json", JSON.stringify(request, null, 2));

console.log("Created request_500_employees_400_shifts.json");
console.log(`Employees per shift: ${EMPLOYEE_COUNT}`);
console.log(`Total shifts: ${SHIFT_COUNT}`);
console.log("Endpoint: POST http://localhost:8083/shifts/batch-assign");
