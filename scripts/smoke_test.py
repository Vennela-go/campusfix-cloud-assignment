#!/usr/bin/env python3
"""Small dependency-free integration test for CampusFix. Run: python3 scripts/smoke_test.py target/campusfix.jar"""
import json, subprocess, sys, time, urllib.request, urllib.error, os
jar = sys.argv[1] if len(sys.argv) > 1 else 'target/campusfix.jar'
port = int(os.environ.get('TEST_PORT', '18080'))
base = f'http://127.0.0.1:{port}'
env = os.environ.copy(); env['PORT'] = str(port)
proc = subprocess.Popen(['java', '-jar', jar], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
def call(path, method='GET', data=None):
    payload = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(base+path, data=payload, method=method, headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req, timeout=3) as r:
        return r.status, json.loads(r.read().decode())
try:
    for _ in range(40):
        try:
            status, health = call('/health')
            if status == 200: break
        except Exception: time.sleep(.25)
    else: raise RuntimeError('Application did not start')
    assert health['status'] == 'UP', health
    status, items = call('/api/issues'); assert status == 200 and len(items) >= 3, items
    status, created = call('/api/issues', 'POST', {'title':'Smoke test issue','category':'Other','description':'Created by automated test'})
    assert status == 201 and created['status'] == 'Open', created
    status, updated = call('/api/issues/'+str(created['id']), 'PUT', {'status':'Resolved'})
    assert status == 200 and updated['status'] == 'Resolved', updated
    status, deleted = call('/api/issues/'+str(created['id']), 'DELETE'); assert status == 200 and deleted['deleted'], deleted
    print('PASS: health, list, create, update, delete')
finally:
    proc.terminate()
    try: proc.wait(timeout=5)
    except subprocess.TimeoutExpired: proc.kill()
