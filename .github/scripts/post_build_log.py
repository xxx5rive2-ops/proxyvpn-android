#!/usr/bin/env python3
"""Post build log to GitHub Release for API access."""
import json, os, sys, urllib.request, re

log_file = '/tmp/build.log'
try:
    log = open(log_file).read()
except Exception as e:
    log = f'No build log found: {e}'

lines = log.splitlines()

# Strategy 1: Find Kotlin compiler 'e:' error lines (most specific)
kotlin_err_lines = [(i, l) for i, l in enumerate(lines)
    if re.match(r'^e:\s+.+\.kt:', l) or 'error:' in l]

if kotlin_err_lines:
    first_idx = kotlin_err_lines[0][0]
    snippet_lines = []
    for idx, line in kotlin_err_lines[:20]:
        ctx_start = max(0, idx - 1)
        ctx_end = min(len(lines), idx + 5)
        snippet_lines.extend(lines[ctx_start:ctx_end])
        snippet_lines.append('---')
    snippet = '\n'.join(snippet_lines)
else:
    # Strategy 2: FAILURE section
    fail_idx = next((i for i, l in enumerate(lines)
        if 'BUILD FAILED' in l or 'FAILURE: Build' in l or '> A failure occurred' in l), None)
    if fail_idx is not None:
        snippet = '\n'.join(lines[max(0, fail_idx - 10): fail_idx + 80])
    else:
        snippet = '\n'.join(lines[-120:])

run_num = os.environ.get('RUN_NUM', '?')
repo = os.environ.get('REPO', '')
token = os.environ.get('GH_TOKEN', '')

tag = f'build-log-{run_num}'
body = f'## Build Log — Run #{run_num}\n\n### Kotlin errors:\n```\n{snippet[:60000]}\n```'

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
    print(f'Error creating release: {e}', file=sys.stderr)
