const fs = require('fs');

function extract() {
    const lines = fs.readFileSync('C:\\Users\\user\\.gemini\\antigravity-ide\\brain\\5f8ea5fc-d984-45c6-8f33-3b020053c9eb\\.system_generated\\logs\\transcript.jsonl', 'utf-8').trim().split('\n');
    let userMsg = null;
    
    // Iterate backwards to find the latest USER_INPUT
    for (let i = lines.length - 1; i >= 0; i--) {
        const entry = JSON.parse(lines[i]);
        if (entry.type === 'USER_INPUT' && entry.content.includes('"existing_users"')) {
            userMsg = entry.content;
            break;
        }
    }
    
    if (!userMsg) {
        console.log("Could not find the user message with existing_users");
        return;
    }
    
    // The user's message contains two JSON blocks separated by newlines
    // Let's find the closing bracket of the first JSON and the opening bracket of the second
    const match = userMsg.match(/\}\s*\{/);
    if (!match) {
        console.log("Could not find the boundary");
        return;
    }
    
    const boundaryIndex = match.index;
    const firstBrace = userMsg.indexOf('{');
    const inputStr = userMsg.substring(firstBrace, boundaryIndex + 1);
    
    // The output JSON is truncated but let's save what we have
    const outputStr = userMsg.substring(boundaryIndex + match[0].length - 1);
    
    fs.writeFileSync('eval_payload.json', inputStr);
    fs.writeFileSync('eval_response.txt', outputStr);
    console.log("Successfully extracted eval_payload.json");
}

extract();
