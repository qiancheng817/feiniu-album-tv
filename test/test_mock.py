#!/usr/bin/env python3
import json
import os
import urllib.request

HOST = os.environ.get("FNPHOTO_TEST_SERVER", "http://127.0.0.1:5666").rstrip("/")

tests = [
    ("/p/api/v1/photo/detail/1", "Photo Detail"),
    ("/p/api/v1/gallery/timeline", "Timeline"),
    ("/p/api/v1/album/list", "Album List"),
    ("/p/api/v1/photo/folder/list?desc=false&orderBy=2", "Folder List"),
    ("/p/api/v1/ai-person/list", "Person List"),
    ("/p/api/v1/explore/geos", "Geo List"),
    ("/p/api/v1/gallery/recent", "Recent"),
    ("/p/api/v1/photo/collect/list", "Collect List"),
]

for path, label in tests:
    try:
        req = urllib.request.Request(HOST + path)
        resp = urllib.request.urlopen(req)
        data = json.loads(resp.read())
        ok = data.get("code") == 0 or data.get("errno") == 0
        print(f"[{'OK' if ok else '??'}] {label}")
    except Exception as e:
        print(f"[ERR] {label}: {e}")

# Test stream endpoints
stream_tests = [
    "/p/api/v1/stream/p/t/1/o/test_uuid?size=m",
    "/p/api/v1/stream/p/t/1/o/test_uuid",
    "/p/api/v1/stream/face/101",
    "/p/api/v1/stream/v/1",
]
for path in stream_tests:
    try:
        req = urllib.request.Request(HOST + path)
        resp = urllib.request.urlopen(req)
        print(f"[OK] {path} => {resp.status} {resp.getheader('Content-Type')} ({len(resp.read())} bytes)")
    except Exception as e:
        print(f"[ERR] {path}: {e}")
