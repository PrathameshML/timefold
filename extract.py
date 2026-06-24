import re

with open('src/main/java/com/scheduler/ShiftApp.java', 'r', encoding='utf-16') as f:
    content = f.read()

# Find the start of assignShiftsV2
start_idx = content.find('public Response assignShiftsV2')
end_idx = content.find('// ============ CONFIGURE AND RUN V2 SOLVER ============', start_idx)

code = content[start_idx:end_idx]

with open('extracted_parser.txt', 'w', encoding='utf-8') as f:
    f.write(code)
