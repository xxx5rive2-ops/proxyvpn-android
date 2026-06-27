#!/usr/bin/env python3
"""Post build log to GitHub Release for API access."""
import json, os, sys, urllib.request

log_file = '/tmp/build.log'
try:
    log = open(log_file).read()
except:
    log = 'No build log found'

lines = log.splitlines()

# Find Kotlin compiler error (most specific)
kotlin_errors = [i for i, l in enumerate(lines)
    if 'error:' in l.lower() and ('.kt:' in l or 'Unresolved' in l or 'None of the following' in l)]

if kotlin_errors:
    idx = kotlin_errors[0]
    snippet = '\n'.join(lines[max(0, idx - 2):idx + 30])
else:
    # Fall back to FAILURE section
    error_idx = next((i for i, l in enumerate(lines)
        if any(k in l for k in ['FAILURE:', 'BUILD FAILED', '> A failure'])), None)
    if error_idx is not None:
        snippet = '\n'.join(lines[max(0, error_idx - 5):error_idx + 60])
    else:
        snippet = '\n'.join(lines[-100:])

run_num = os.environ.get('RUN_NUM', '?')
repo = os.environ.get('REPO', '')
token = os.environ.get('GH_TOKEN', '')

tag = f'build-log-{run_num}'
body = f'## Build Log — Run #{run_num}\n\n```\n{snippet[:60000]}\n```'

payload = json.dumps({
    'tag_name': tag,
    'name': f'[LOG] Run #{run_num}',
    'body': body,
    'draft': True,
    'prerelease': True
}).encode()

req = urllib.request.Request(
    f'https://api.github.com/repos/{repo}/releases',
    data=payload,
    headers={
        'Authorization': f'token {token}',
        'Content-Type': 'application/json',
        'Accept': 'application/vnd.github+json'
    }
)
try:
    resp = urllib.request.urlopen(req)
    d = json.loads(resp.read())
    print(f'Release created: id={d.get("id")} tag={d.get("tag_name")}')
except Exception as e:
    print(f'Error: {e}', file=sys.stderr)
