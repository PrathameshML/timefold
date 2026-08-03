import json
import os

runs = [
    ("Old Global", "benchmark_result_old_run1.json"),
    ("Old Global", "benchmark_result_old_run2.json"),
    ("Old Global", "benchmark_result_old_run3.json"),
    ("V2 Slot-Based", "benchmark_result_v2_run1.json"),
    ("V2 Slot-Based", "benchmark_result_v2_run2.json"),
    ("V2 Slot-Based", "benchmark_result_v2_run3.json")
]

print("## Raw Per-Run Data")
print("| Model | Run | Feasible | Time (s) | Score |")
print("|---|---|---|---|---|")

for model, file in runs:
    if not os.path.exists(file):
        continue
    with open(file, 'r') as f:
        data = json.load(f)
        run_num = file[-6:-5]
        print(f"| {model} | {run_num} | {data.get('is_feasible')} | {data.get('solver_time_seconds'):.2f} | `{data.get('score')}` |")

print("\n## Convergence Curve (Score Events)")
for model, file in runs:
    if not os.path.exists(file):
        continue
    with open(file, 'r') as f:
        data = json.load(f)
        events = data.get('score_events', [])
        
        # Get score at roughly 0s, 10s, 20s, 30s, 60s
        targets = [0, 10000, 20000, 30000, 60000]
        results = {}
        for target in targets:
            best_score = "None"
            for e in events:
                if e['elapsed_ms'] <= target:
                    best_score = e['score']
                else:
                    break
            # If the solver finished before the target, use the last known score
            if not best_score and events:
                best_score = events[0]['score']
            if events and target > events[-1]['elapsed_ms']:
                best_score = events[-1]['score']
            results[target] = best_score
            
        run_num = file[-6:-5]
        print(f"**{model} - Run {run_num}**")
        print(f"- 0s (First Feasible): `{results[0]}`")
        print(f"- 10s: `{results[10000]}`")
        print(f"- 20s: `{results[20000]}`")
        print(f"- 30s: `{results[30000]}`")
        print(f"- Final ({data.get('solver_time_seconds'):.1f}s): `{data.get('score')}`\n")
