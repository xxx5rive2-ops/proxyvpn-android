#!/usr/bin/env python3
"""Post build log errors to GitHub Release."""
import json, os, sys, urllib.request, re

log_file = '/tmp/build.log'
try:
    log = open(log_file, errors='replace').read()
except Exception as e:
    log = f'No build log: {e}'

lines = log.splitlines()

# Method 1: Kotlin compiler 'e:' lines (exact format)
# Pattern: "e: file.kt:line:col: error: message"
kotlin_errors = []
for i, line in enumerate(lines):
    # Remove timestamp prefix if present
    clean = re.sub(r'^\d{4}-\d{2}-\d{2}T[\d:.+]+\s+\[.*?\]\s+', '', line)
    if re.match(r'^e:\s', clean) or re.match(r'^.*\.kt:\d+:\d+:\s+error:', clean):
        ctx = lines[max(0,i-1):min(len(lines),i+5)]
        kotlin_errors.append('\n'.join(ctx))

if kotlin_errors:
    snippet = '\n---\n'.join(kotlin_errors[:20])
else:
    # Method 2: Look for Kotlin error reporter output
    reporter_errors = []
    for i, line in enumerate(lines):
        if 'KotlinCompileDaemonClient' in line and ('error:' in line.lower()):
            reporter_errors.extend(lines[max(0,i-1):i+5])
    
    if reporter_errors:
        snippet = '\n'.join(reporter_errors[:100])
    else:
        # Method 3: Text around FAILURE
        fail_idx = next((i for i, l in enumerate(lines) 
            if 'BUILD FAILED' in l or 'Compilation error' in l), None)
        if fail_idx:
            # Search backwards for any error context
            error_ctx = []
            for l in lines[max(0,fail_idx-200):fail_idx+20]:
                clean = re.sub(r'^\d{4}-\d{2}-\d{2}T[\d:.+]+\s+\[\w+\]\s+\[.*?\]\s+', '', l)
                if clean.strip() and 'DEBUG' not in l:
                    error_ctx.append(clean)
            snippet = '\n'.join(error_ctx[-80:])
        else:
            snippet = '\n'.join(
                re.sub(r'^\d{4}-\d{2}-\d{2}T[\d:.+]+\s+\[.*?\]\s+', '', l)
                for l in lines[-80:]
                if '[DEBUG]' not in l
            )

run_num = os.environ.get('RUN_NUM', '?')
repo    = os.environ.get('REPO', '')
token   = os.environ.get('GH_TOKEN', '')

body = f'## Run #{run_num} — engine-proxy:compileDebugKotlin\n\n```\n{snippet[:60000]}\n```'
payload = json.dumps({
    'tag_name'   : f'build-log-{run_num}',
    'name'       : f'[LOG] Run #{run_num}',
    'body'       : body,
    'draft'      : True,
    'prerelease' : True,
}).encode()

req = urllib.request.Request(
    f'https://api.github.com/repos/{repo}/releases',
    data=payload,
    headers={'Authorization': f'token {token}',
             'Content-Type': 'application/json',
             'Accept': 'application/vnd.github+json'})
try:
    resp = urllib.request.urlopen(req)
    d = json.loads(resp.read())
    print(f'Release: id={d.get("id")} tag={d.get("tag_name")}')
except Exception as e:
    print(f'Release error: {e}', file=sys.stderr)
    try: print(e.read().decode(), file=sys.stderr)
    except: pass
