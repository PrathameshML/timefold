const http = require('http');
const fs = require('fs');

const URL = 'http://localhost:8083/shifts/assign-v2';

function mulberry32(a) {
    return function() {
      var t = a += 0x6D2B79F5;
      t = Math.imul(t ^ t >>> 15, t | 1);
      t ^= t + Math.imul(t ^ t >>> 7, t | 61);
      return ((t ^ t >>> 14) >>> 0) / 4294967296;
    }
}

function generateEmployees(count) {
    const employees = [];
    const skills = ["Cashier", "Stocking", "Customer Service"];
    const rand = mulberry32(12345);
    
    for (let i = 1; i <= count; i++) {
        const wage = 15 + Math.floor(rand() * 21);
        const rating = 3;
        const type = "Permanent";
        const skill = skills[Math.floor(rand() * skills.length)];
        employees.push({
            "employee_id": `E${i}`,
            "name": `Worker ${i}`,
            "role": "Cashier",
            "rate": wage,
            "rating": rating,
            "employeeType": type,
            "skills": [skill]
        });
    }
    return employees;
}

// Fisher-Yates shuffle using a fresh random so we get 20 different shuffles
function shuffleArray(array) {
    for (let i = array.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [array[i], array[j]] = [array[j], array[i]];
    }
    return array;
}

function getPayload(employees) {
    return {
        "shift_name": "Enterprise Shift",
        "start_date": "2026-09-01",
        "end_date": "2026-09-07",
        "start_time": "08:00",
        "end_time": "16:00",
        "roles": [{ "role_name": "Cashier", "max_workers": 300, "required_skills": ["Cashier"] }],
        "existing_users": employees,
        "active_constraints": [
            { "name": "everyShiftPlanned", "severity": "HARD" },
            { "name": "noOverlappingShifts", "severity": "HARD" },
            { "name": "maxDailyHours", "severity": "MEDIUM", "value": 8.0 },
            { "name": "maxWeeklyHours", "severity": "MEDIUM", "value": 40.0 },
            { "name": "unavailableTimeslot", "severity": "HARD" },
            { "name": "skillMatch", "severity": "SOFT" },
            { "name": "breakAfterHours", "severity": "HARD", "value": 4.0 },
            { "name": "overtimeThreshold", "severity": "SOFT", "value": 8.0 },
            { "name": "consecutiveShifts", "severity": "SOFT" },
            { "name": "permanentPriority", "severity": "SOFT" },
            { "name": "wageOptimization", "severity": "SOFT" },
            { "name": "maximizeRating", "severity": "SOFT", "value": 100.0 }
        ],
        "time_limit_seconds": 60,
        "unimproved_time_limit_seconds": 15,
        "schedule_breaks": true
    };
}

async function runTest(payload) {
    return new Promise((resolve) => {
        const postData = JSON.stringify(payload);
        const req = http.request(URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(postData)
            }
        }, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => resolve(JSON.parse(data)));
        });
        req.on('error', (e) => {
            console.error(e);
            resolve(null);
        });
        req.write(postData);
        req.end();
    });
}

function parseScore(scoreStr) {
    const match = scoreStr.match(/(-?\d+)hard\/(-?\d+)medium\/(-?\d+)soft/);
    if (match) {
        return { hard: parseInt(match[1]), medium: parseInt(match[2]), soft: parseInt(match[3]) };
    }
    return { hard: 0, medium: 0, soft: 0 };
}

async function runBenchmark() {
    console.log("Generating deterministic dataset (500 employees)...");
    const baseEmployees = generateEmployees(500);
    
    const runs = 20;
    const scores = [];
    
    let report = "| Run | Hard | Medium | Soft | Coverage | Avg Wage | Avg Rating |\n|---|---|---|---|---|---|---|\n";
    console.log(report.trim());

    for (let r = 1; r <= runs; r++) {
        console.log(`Running iteration ${r}/${runs}...`);
        // Deep copy and shuffle
        const shuffledEmployees = shuffleArray(JSON.parse(JSON.stringify(baseEmployees)));
        
        const payload = getPayload(shuffledEmployees);
        const start = Date.now();
        const res = await runTest(payload);
        const end = Date.now();
        
        if (res) {
            const score = parseScore(res.solver_score);
            let avgWage = "N/A";
            
            const exp = res.score_explanation || "";
            const wageMatch = exp.match(/(-?\d+)soft: constraint \(wageOptimization_SOFT\) has (\d+) matches/);
            if (wageMatch) {
                const totalWagePenalty = Math.abs(parseInt(wageMatch[1]));
                const totalAssigned = res.new_assignments_made;
                if (totalAssigned > 0) {
                    avgWage = (totalWagePenalty / totalAssigned).toFixed(2);
                }
            }

            const row = `| ${r} | ${score.hard} | ${score.medium} | ${score.soft} | ${res.new_assignments_made}/${res.total_possible_assignments} | $${avgWage}/hr | 3.0 |`;
            console.log(row);
            report += row + "\n";
            scores.push(score.soft);
        } else {
             console.log(`Failed for run ${r}`);
        }
    }
    
    const maxScore = Math.max(...scores);
    const minScore = Math.min(...scores);
    const avgScore = scores.reduce((a, b) => a + b, 0) / scores.length;
    const variance = scores.reduce((a, b) => a + Math.pow(b - avgScore, 2), 0) / scores.length;
    const stdDev = Math.sqrt(variance);
    
    report += `\n### Statistics\n`;
    report += `- **Best Score (Soft):** ${maxScore}\n`;
    report += `- **Worst Score (Soft):** ${minScore}\n`;
    report += `- **Average Score (Soft):** ${avgScore.toFixed(2)}\n`;
    report += `- **Standard Deviation:** ${stdDev.toFixed(2)}\n`;
    
    fs.writeFileSync('benchmark_seed_stability.md', report);
    console.log("Benchmark complete. Wrote benchmark_seed_stability.md");
}

runBenchmark();
