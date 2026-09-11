#!/usr/bin/env python3
"""
fnOS comprehensive mock server for testing the TV app.
Simulates all REST APIs, WebSocket login, and media streaming on a single port.
"""

import asyncio
import json
import base64
import io
import logging
import os
import ssl
import socket
import struct
import time
import random
import hashlib
import uuid as uuid_mod
import ipaddress
from datetime import datetime, timedelta, timezone

import imageio
import numpy as np

from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.backends import default_backend

from aiohttp import web, WSMsgType
from PIL import Image, ImageDraw, ImageFont

logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s: %(message)s')
log = logging.getLogger('fnos-mock')

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TEST_VIDEO_PATH = os.path.join(SCRIPT_DIR, "test_video.mp4")

RSA_KEY = rsa.generate_private_key(
    public_exponent=65537, key_size=2048, backend=default_backend()
)
RSA_PUBLIC_PEM = RSA_KEY.public_key().public_bytes(
    encoding=serialization.Encoding.PEM,
    format=serialization.PublicFormat.SubjectPublicKeyInfo
).decode('utf-8')
LOGIN_SI = "545460846793"
TOKEN_CACHE = {}
HEARTBEAT_CLIENTS = set()


def get_local_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


def rsa_decrypt(ciphertext_b64: str) -> bytes:
    return RSA_KEY.decrypt(base64.b64decode(ciphertext_b64), padding.PKCS1v15())


def aes_decrypt(ciphertext_b64: str, key: str, iv_b64: str) -> str:
    cipher = Cipher(algorithms.AES(key.encode('utf-8')),
                    modes.CBC(base64.b64decode(iv_b64)),
                    backend=default_backend())
    decryptor = cipher.decryptor()
    padded = decryptor.update(base64.b64decode(ciphertext_b64)) + decryptor.finalize()
    return padded[:-padded[-1]].decode('utf-8')


# ────────────────────────── Fake Data ──────────────────────────

RNG = random.Random(42)
PHOTO_COUNT = 80
ALBUM_COUNT = 6
PERSON_COUNT = 4
FACE_COUNT = PERSON_COUNT
NOW = datetime.now(timezone.utc)

PHOTOS = []
PHOTO_MAP = {}
ALBUMS = []
ALBUM_MAP = {}
PERSONS = []
PERSON_MAP = {}
TIMELINE_ITEMS = []


def _rng_ts(days_ago_min=1, days_ago_max=365):
    d = timedelta(days=RNG.randint(days_ago_min, days_ago_max),
                  hours=RNG.randint(0, 23), minutes=RNG.randint(0, 59))
    return int((NOW - d).timestamp() * 1000)


def _rng_uuid():
    return uuid_mod.uuid4().hex[:16]


PHOTO_NAMES = [
    "vacation_beach.jpg", "sunset_mountain.jpg", "family_dinner.png", "city_skyline.jpg",
    "birthday_party.jpg", "hiking_trail.jpg", "pet_dog.jpg", "garden_flowers.jpg",
    "new_year_fireworks.jpg", "wedding_ceremony.jpg", "road_trip.jpg", "snowy_mountain.jpg",
    "breakfast_table.jpg", "night_market.jpg", "park_bench.jpg", "ocean_sunset.jpg",
    "museum_visit.jpg", "camping_trip.jpg", "bicycle_ride.jpg", "coffee_shop.jpg",
    "lake_reflection.jpg", "treehouse.jpg", "street_art.jpg", "rainy_window.jpg",
    "autumn_leaves.jpg", "book_shelf.jpg", "concert_night.jpg", "barbecue_party.jpg",
    "kite_flying.jpg", "starry_sky.jpg", "waterfall.jpg", "temple_visit.jpg",
    "surfing.jpg", "mountain_biking.jpg", "paint_night.jpg", "wine_tasting.jpg",
    "farm_visit.jpg", "zoo_day.jpg", "arcade_games.jpg", "pottery_class.jpg",
    "cherry_blossom.jpg", "bridge_view.jpg", "lighthouse.jpg", "desert_dune.jpg",
    "hot_air_balloon.jpg", "horse_riding.jpg", "kayaking.jpg", "lighthouse_sunset.jpg",
    "market_stall.jpg", "national_park.jpg", "old_town.jpg", "opera_house.jpg",
    "paragliding.jpg", "rice_terrace.jpg", "scuba_diving.jpg", "skiing.jpg",
    "snowboarding.jpg", "sunset_cruise.jpg", "temple_bell.jpg", "vineyard.jpg",
    "volcano_view.jpg", "whale_watching.jpg", "yoga_sunrise.jpg", "zip_line.jpg",
    "art_gallery.jpg", "balcony_view.jpg", "canyon_hike.jpg", "dolphin_spot.jpg",
    "eclipse_view.jpg", "flower_field.jpg", "glacier_hike.jpg", "hot_spring.jpg",
    "ice_skating.jpg", "jazz_club.jpg", "kite_surfing.jpg", "lavender_field.jpg",
    "moon_rise.jpg", "northern_lights.jpg", "ocean_drive.jpg", "piano_recital.jpg",
]

CITY_COUNTRY = {
    "Beijing": "China", "Shanghai": "China", "Tokyo": "Japan", "Paris": "France",
    "New York": "USA", "London": "UK", "Sydney": "Australia", "Seoul": "South Korea",
    "Bangkok": "Thailand", "Dubai": "UAE", "Singapore": "Singapore", "Rome": "Italy",
    "Mumbai": "India", "Istanbul": "Turkey", "Hong Kong": "China", "Berlin": "Germany",
    "Barcelona": "Spain", "Amsterdam": "Netherlands",
}

PERSON_NAMES = ["Mom", "Dad", "Alice", "Bob", "Charlie", "Diana", "Eve", "Frank"]


def build_fake_data():
    global PHOTOS, PHOTO_MAP, ALBUMS, ALBUM_MAP, PERSONS, PERSON_MAP, TIMELINE_ITEMS

    # ── Photos ──
    for i in range(PHOTO_COUNT):
        pid = i + 1
        ts = _rng_ts()
        w = RNG.choice([1920, 2048, 2560, 3840, 4000])
        h = RNG.choice([1080, 1365, 1440, 2160, 2250])
        typ = "photo" if RNG.random() > 0.15 else "video"
        geo = None
        if RNG.random() > 0.5:
            city = RNG.choice(list(CITY_COUNTRY.keys()))
            country = CITY_COUNTRY[city]
            geo = f"{city}, {country}"
        photo_uuid = _rng_uuid()
        dt = datetime.fromtimestamp(ts / 1000, tz=timezone.utc)
        file_path = f"/photos/{dt.year}/{dt.month:02d}/{PHOTO_NAMES[i % len(PHOTO_NAMES)]}"

        photo = {
            "id": pid,
            "ownerId": 1,
            "dateTime": dt.strftime("%Y-%m-%d %H:%M:%S"),
            "photoDateTime": dt.strftime("%Y-%m-%d %H:%M:%S"),
            "fileType": "jpg" if typ == "image" else "mp4",
            "category": typ,
            "fileName": PHOTO_NAMES[i % len(PHOTO_NAMES)],
            "fileSize": RNG.randint(500_000, 8_000_000),
            "description": "",
            "isCollect": RNG.randint(0, 1),
            "model": RNG.choice(["iPhone 15 Pro", "Canon EOS R5", "Sony A7 IV", "Google Pixel 8", ""]),
            "make": RNG.choice(["Apple", "Canon", "Sony", "Google", ""]),
            "fNumber": f"f/{RNG.choice([1.4, 1.8, 2.0, 2.8, 4.0, 5.6, 8.0])}",
            "exposureTime": f"1/{RNG.choice([30, 60, 125, 250, 500, 1000])}",
            "isoSpeedRatings": str(RNG.choice([100, 200, 400, 800, 1600, 3200])),
            "focalLength": f"{RNG.choice([24, 35, 50, 85, 100, 135, 200])}mm",
            "exposureProgram": RNG.choice(["Manual", "Aperture Priority", "Program", "Automatic"]),
            "meteringMode": RNG.choice(["Evaluative", "Center-weighted", "Spot"]),
            "mp": f"{round(w * h / 1_000_000, 1)}MP",
            "filePath": file_path,
            "showFilePath": file_path,
            "height": h,
            "width": w,
            "geo": geo or "",
            "isLive": 0,
            "rotation": RNG.choice([0, 0, 0, 90, 180]),
            "isCanPreview": 1,
            "photoUUID": photo_uuid,
            "additional": {
                "thumbnail": {
                    "mUrl": f"/p/api/v1/stream/p/t/{pid}/o/{photo_uuid}?size=m",
                    "sUrl": f"/p/api/v1/stream/p/t/{pid}/o/{photo_uuid}?size=s",
                    "xsUrl": f"/p/api/v1/stream/p/t/{pid}/o/{photo_uuid}?size=xs",
                    "xxsUrl": f"/p/api/v1/stream/p/t/{pid}/o/{photo_uuid}?size=xxs",
                    "videoUrl": None,
                    "originalUrl": f"/p/api/v1/stream/p/t/{pid}/o/{photo_uuid}",
                },
                "tags": [],
            },
        }
        PHOTOS.append(photo)
        PHOTO_MAP[pid] = photo
        PHOTO_MAP[photo_uuid] = photo

    # ── Timeline ──
    date_groups = {}
    for p in PHOTOS:
        day_offset = (p["id"] * 3) % 90
        d2 = NOW - timedelta(days=day_offset)
        key = (d2.year, d2.month, d2.day)
        date_groups.setdefault(key, []).append(p)

    TIMELINE_ITEMS = []
    for (y, m, d), photos in sorted(date_groups.items()):
        previews = [ph["additional"]["thumbnail"]["xsUrl"] for ph in photos[:4]]
        TIMELINE_ITEMS.append({
            "year": y, "month": m, "day": d,
            "itemCount": len(photos),
            "previewThumbs": previews,
        })

    # ── Albums ──
    for i in range(ALBUM_COUNT):
        album_photos = RNG.sample(PHOTOS, RNG.randint(3, 12))
        aid = i + 1
        start_dt = min(p["dateTime"] for p in album_photos)
        end_dt = max(p["dateTime"] for p in album_photos)
        cover = RNG.choice(album_photos)
        album = {
            "albumId": aid,
            "albumName": RNG.choice([
                "Family Trip 2025", "Summer Vacation", "Birthday Party",
                "Hiking Adventures", "City Walks", "Food Diary",
                "Sunset Collection", "Random Moments",
            ]),
            "source": "local",
            "photoCount": len([p for p in album_photos if p["category"] == "photo"]),
            "videoCount": len([p for p in album_photos if p["category"] == "video"]),
            "posterUrl": cover["additional"]["thumbnail"]["mUrl"],
            "posterImgUrl": cover["additional"]["thumbnail"]["sUrl"],
            "startDateTime": start_dt,
            "endDateTime": end_dt,
            "shared": 0,
            "ownerId": 1,
        }
        ALBUMS.append(album)
        ALBUM_MAP[aid] = {"album": album, "photos": album_photos}

    # ── Persons ──
    for i in range(PERSON_COUNT):
        face_id = i + 100
        person_photos = RNG.sample(PHOTOS, RNG.randint(5, 20))
        pid = i + 1
        person = {
            "id": pid,
            "name": PERSON_NAMES[i],
            "faceId": face_id,
            "itemCount": len(person_photos),
            "birthday": f"{1980 + RNG.randint(0, 30)}-{RNG.randint(1, 12):02d}-{RNG.randint(1, 28):02d}",
            "isHide": False,
        }
        PERSONS.append(person)
        PERSON_MAP[pid] = {"person": person, "photos": person_photos, "faceId": face_id}

    # ── Geo items ──
    geo_groups = {}
    for p in PHOTOS:
        if p["geo"]:
            parts = p["geo"].split(", ")
            if len(parts) == 2:
                geo_groups.setdefault(parts[0], {"country": parts[1], "photos": []})["photos"].append(p)
    GEO_ITEMS = [
        {"country": v["country"], "city": k, "itemCount": len(v["photos"]),
         "posterUrl": v["photos"][0]["additional"]["thumbnail"]["mUrl"]}
        for k, v in geo_groups.items()
    ]

    return TIMELINE_ITEMS, GEO_ITEMS


TIMELINE_ITEMS, GEO_ITEMS = [], []
PERSONS_BY_ID = {}
PERSON_PHOTOS = {}


def init_data():
    global TIMELINE_ITEMS, GEO_ITEMS, PERSONS_BY_ID, PERSON_PHOTOS
    TIMELINE_ITEMS, GEO_ITEMS = build_fake_data()
    # Rebuild person lookup from globals
    for p in PERSONS:
        PERSONS_BY_ID[p["id"]] = p
    for pid, data in PERSON_MAP.items():
        PERSON_PHOTOS[pid] = data["photos"]


init_data()


# ──────────────── Placeholder Image Generation ────────────────

def _gradient_bytes(w, h, r1, g1, b1, r2, g2, b2):
    img = Image.new("RGB", (w, h))
    for y in range(h):
        r = int(r1 + (r2 - r1) * y / h)
        g = int(g1 + (g2 - g1) * y / h)
        b = int(b1 + (b2 - b1) * y / h)
        for x in range(w):
            img.putpixel((x, y), (r, g, b))
    return img


def _make_thumbnail(w, h, label="", size_label=""):
    r1, g1, b1 = RNG.randint(40, 200), RNG.randint(40, 200), RNG.randint(40, 200)
    r2, g2, b2 = RNG.randint(40, 200), RNG.randint(40, 200), RNG.randint(40, 200)
    img = _gradient_bytes(w, h, r1, g1, b1, r2, g2, b2)
    draw = ImageDraw.Draw(img)
    text = label or f"{w}x{h}"
    try:
        font = ImageFont.truetype("arial.ttf", max(12, min(w, h) // 8))
    except Exception:
        font = ImageFont.load_default()
    bbox = draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text(((w - tw) // 2, (h - th) // 2), text, fill=(255, 255, 255), font=font)
    if size_label:
        draw.text((4, 4), size_label, fill=(255, 255, 0), font=font)
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=70)
    return buf.getvalue()


def _make_face_image(face_id):
    colors = [(220, 180, 160), (200, 160, 140), (240, 200, 180), (180, 140, 120)]
    c = colors[face_id % len(colors)]
    img = Image.new("RGB", (200, 200), c)
    draw = ImageDraw.Draw(img)
    draw.ellipse([50, 30, 150, 130], outline=(100, 60, 40), width=3)
    draw.ellipse([75, 60, 90, 75], fill=(40, 40, 40))
    draw.ellipse([110, 60, 125, 75], fill=(40, 40, 40))
    draw.arc([70, 80, 130, 110], 0, 180, fill=(80, 40, 20), width=2)
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=80)
    return buf.getvalue()


SIZE_MAP = {"xxs": (120, 90), "xs": (240, 180), "s": (480, 360), "m": (960, 720)}
PLACEHOLDER_CACHE = {}


def get_placeholder(w, h, label="", size_label=""):
    key = (w, h, label)
    if key not in PLACEHOLDER_CACHE:
        PLACEHOLDER_CACHE[key] = _make_thumbnail(w, h, label, size_label)
    return PLACEHOLDER_CACHE[key]


# ────────────────── Response Helpers ──────────────────

def ok(data=None):
    return web.json_response({"errno": 0, "result": "succ", "data": data or {}})


def ok_v2(data=None):
    return web.json_response({"code": 0, "msg": "succ", "data": data or {}})


def make_list(data_list, total=None, has_next=False):
    return {"list": data_list, "total": total or len(data_list), "hasNext": has_next}


def extract_paging(request):
    page = int(request.query.get("page", 1))
    page_size = int(request.query.get("pageSize", 50))
    return page, page_size


def paginate(items, page, page_size):
    start = (page - 1) * page_size
    end = start + page_size
    return items[start:end], len(items)


def extract_auth_token(request):
    auth = request.headers.get("AuthX", request.headers.get("Authorization", ""))
    if auth.startswith("Bearer "):
        auth = auth[7:]
    return auth


# ──────────────── HTTP REST Handlers ────────────────

async def handle_album_list(request):
    data = {"list": [a for a in ALBUMS]}
    return ok_v2(data)


async def handle_album_photos(request):
    album_id = int(request.query.get("albumId", 0))
    page, page_size = extract_paging(request)
    adata = ALBUM_MAP.get(album_id, {})
    photos = adata.get("photos", [])
    paged, total = paginate(photos, page, page_size)
    return ok_v2(make_list(paged, total))


async def handle_folder_list(request):
    items = []
    for i in range(3):
        items.append({
            "folderId": i + 1,
            "folderPath": f"/mock_media/Folder{i+1}",
            "photoCount": RNG.randint(10, 100),
            "videoCount": RNG.randint(0, 20),
            "status": 1,
            "isDefault": i == 0,
            "hasWriteAccess": {"quotaCurr": 500_000_000_000, "quotaMax": 0, "hasWriteAccess": True},
        })
    return ok_v2({"list": items})


async def handle_sub_folder_list(request):
    path = request.query.get("path", "/")
    items = []
    for i in range(5):
        items.append({"name": f"Subfolder{i+1}", "path": f"{path}/Subfolder{i+1}"})
    return ok_v2(make_list(items, total=5))


async def handle_folder_file_list(request):
    path = request.query.get("path", "/")
    page, page_size = extract_paging(request)
    items = []
    for i, ph in enumerate(PHOTOS[:20]):
        items.append({
            "id": ph["id"], "ownerId": 1,
            "dateTime": ph["dateTime"], "photoDateTime": ph["photoDateTime"],
            "fileType": ph["fileType"], "category": ph["category"],
            "fileName": ph["fileName"], "fileSize": ph["fileSize"],
            "isCollect": ph["isCollect"], "mp": ph["mp"],
            "filePath": ph["filePath"], "height": ph["height"], "width": ph["width"],
            "flash": 0, "geo": ph["geo"], "isLive": 0, "mediaDuration": 0,
            "rotation": ph["rotation"], "isCanPreview": 1,
            "photoUUID": ph["photoUUID"], "fileHash": hashlib.md5(ph["fileName"].encode()).hexdigest(),
        })
    paged, total = paginate(items, page, page_size)
    return ok_v2(make_list(paged, len(items)))


async def handle_timeline(request):
    return ok_v2({"list": TIMELINE_ITEMS})


async def handle_gallery_list(request):
    page, page_size = extract_paging(request)
    start_time = request.query.get("startTime", "")
    end_time = request.query.get("endTime", "")
    photos = PHOTOS
    if start_time:
        st = int(start_time)
        photos = [p for p in photos if p["id"] * 86400000 + 1700000000000 >= st]
    if end_time:
        et = int(end_time)
        photos = [p for p in photos if p["id"] * 86400000 + 1700000000000 <= et]
    paged, total = paginate(photos, page, page_size)
    result = {"list": paged, "count": len(paged), "hasNext": (page * page_size) < total}
    return ok_v2(result)


async def handle_photo_detail(request):
    photo_id = request.match_info.get("id", "0")
    pid = int(photo_id)
    photo = PHOTO_MAP.get(pid)
    if not photo:
        return web.json_response({"code": 1, "msg": "not found"}, status=404)
    return ok_v2({"info": photo})


async def handle_toggle_collect(request):
    body = await request.json() if request.can_read_body else {}
    return ok_v2({"isCollect": body.get("type", 1)})


async def handle_geo_list(request):
    return ok_v2({"count": len(GEO_ITEMS), "hasNext": False, "list": GEO_ITEMS})


async def handle_search(request):
    keyword = request.query.get("keyword", "")
    page, page_size = extract_paging(request)
    matched = [p for p in PHOTOS if keyword.lower() in p["fileName"].lower()] if keyword else PHOTOS[:20]
    paged, total = paginate(matched, page, page_size)
    return ok_v2({"list": paged, "count": len(paged), "hasNext": (page * page_size) < total, "total": total})


async def handle_search_results(request):
    body = await request.json()
    keyword = body.get("keyword", "")
    matched = [p for p in PHOTOS if keyword.lower() in p["fileName"].lower()] if keyword else PHOTOS[:20]
    return ok_v2({"list": matched, "count": len(matched), "hasNext": False, "total": len(matched)})


async def handle_upload_path(request):
    return ok({"path": "/mock_media/upload/tmp", "expireTime": int((NOW + timedelta(hours=2)).timestamp())})


async def handle_support_types(request):
    return ok({"imageTypes": ["jpg", "jpeg", "png", "gif", "bmp", "webp", "heic"],
               "videoTypes": ["mp4", "mov", "avi", "mkv", "wmv"],
               "maxFileSize": 10_737_418_240})


async def handle_storage(request):
    return ok({"totalSpace": 8_000_000_000_000, "usedSpace": 2_500_000_000_000,
               "freeSpace": 5_500_000_000_000,
               "folders": [
                   {"id": "1", "name": "photo", "path": "/mock_media",
                    "size": 2_000_000_000_000, "fileCount": PHOTO_COUNT},
               ]})


async def handle_users_all(request):
    return ok({"users": [
        {"uid": "1", "username": "nas", "nickname": "NAS User",
         "avatar": "", "isAdmin": True, "createTime": 1600000000000},
    ]})


async def handle_user_info(request):
    return ok({"uid": "1", "username": "nas", "nickname": "NAS User",
               "avatar": "", "isAdmin": True, "createTime": 1600000000000})


async def handle_sys_info(request):
    return ok({"version": "0.8.28-2405", "model": "FN-NAS-4Bay",
               "serial": "FN24050001", "hostname": "fnPhoto-NAS", "uptime": 864000})


async def handle_photo_stats(request):
    img_count = len([p for p in PHOTOS if p["category"] == "photo"])
    vid_count = len([p for p in PHOTOS if p["category"] == "video"])
    return ok_v2({"id": 1, "photoCount": img_count, "videoCount": vid_count, "isAdmin": True, "nasUid": 1})


async def handle_app_version(request):
    return ok({"version": "1.0.0"})


async def handle_recent(request):
    recent = sorted(PHOTOS, key=lambda p: p["dateTime"], reverse=True)[:20]
    return ok_v2({"list": recent, "count": len(recent), "hasNext": False})


async def handle_explore_recent_timeline(request):
    recent_tl = sorted(TIMELINE_ITEMS, key=lambda t: (t["year"], t["month"], t["day"]), reverse=True)[:10]
    return ok_v2({"list": recent_tl})


async def handle_person_list(request):
    return ok_v2({"list": PERSONS})


async def handle_person_timeline(request):
    person_id = int(request.query.get("personId", 0))
    photos = PERSON_PHOTOS.get(person_id, [])
    tl = {}
    for p in photos:
        day_offset = (p["id"] * 3) % 90
        dt = NOW - timedelta(days=day_offset)
        key = (dt.year, dt.month, dt.day)
        tl.setdefault(key, 0)
        tl[key] += 1
    items = sorted([{"year": y, "month": m, "day": d, "itemCount": c}
                     for (y, m, d), c in tl.items()], key=lambda x: (x["year"], x["month"], x["day"]), reverse=True)
    return ok_v2({"list": items})


async def handle_person_photos(request):
    person_id = int(request.query.get("personId", 0))
    page, page_size = extract_paging(request)
    photos = PERSON_PHOTOS.get(person_id, [])
    paged, total = paginate(photos, page, page_size)
    return ok_v2(make_list(paged, total))


async def handle_collect_list(request):
    collected = [p for p in PHOTOS if p["isCollect"] == 1]
    return ok_v2(make_list(collected, len(collected)))


# ──────────────── Stream / Image Handlers ────────────────

async def handle_stream_original(request):
    photo_id = request.match_info.get("id", "0")
    photo_uuid = request.match_info.get("uuid", "")
    size = request.query.get("size", "")
    pid = int(photo_id)
    photo = PHOTO_MAP.get(pid)
    w, h = (4000, 2250)
    label = f"Photo {photo_id}"
    if photo:
        w, h = photo["width"], photo["height"]
        label = photo["fileName"]
    if size in SIZE_MAP:
        w, h = SIZE_MAP[size]
    data = get_placeholder(w, h, label, size)
    return web.Response(body=data, content_type="image/jpeg",
                        headers={"Cache-Control": "max-age=3600"})


def generate_test_video():
    if os.path.exists(TEST_VIDEO_PATH):
        return
    w, h, duration, fps = 640, 480, 3, 15
    frames = []
    for i in range(duration * fps):
        t = i / fps
        r = int(128 + 127 * np.sin(2 * np.pi * 0.5 * t))
        g = int(128 + 127 * np.sin(2 * np.pi * 0.3 * t + 2))
        b = int(128 + 127 * np.sin(2 * np.pi * 0.2 * t + 4))
        frame = np.zeros((h, w, 3), dtype=np.uint8)
        frame[:, :, 0] = r
        frame[:, :, 1] = g
        frame[:, :, 2] = b
        draw = ImageDraw.Draw(Image.fromarray(frame))
        text = f"Test Video {i // fps + 1}s"
        try:
            font = ImageFont.truetype("arial.ttf", 36)
        except Exception:
            font = ImageFont.load_default()
        bbox = draw.textbbox((0, 0), text, font=font)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        draw.text(((w - tw) // 2, (h - th) // 2), text, fill=(255, 255, 255), font=font)
        frames.append(np.array(Image.fromarray(frame)))
    imageio.mimsave(TEST_VIDEO_PATH, frames, fps=fps, codec="libx264",
                     pixelformat="yuv420p", ffmpeg_params=["-profile:v", "baseline", "-level", "3.0"])
    log.info(f"Generated test video: {TEST_VIDEO_PATH}")


async def handle_stream_video(request):
    photo_id = request.match_info.get("id", "0")
    with open(TEST_VIDEO_PATH, "rb") as f:
        data = f.read()
    return web.Response(body=data, content_type="video/mp4",
                        headers={"Cache-Control": "max-age=3600"})


async def handle_stream_thumbnail(request):
    photo_id = request.match_info.get("id", "0")
    size = request.query.get("size", "m")
    w, h = SIZE_MAP.get(size, (480, 360))
    photo = PHOTO_MAP.get(int(photo_id))
    label = photo["fileName"] if photo else f"Photo {photo_id}"
    data = get_placeholder(w, h, label, size)
    return web.Response(body=data, content_type="image/jpeg",
                        headers={"Cache-Control": "max-age=3600"})


async def handle_face_image(request):
    face_id = int(request.match_info.get("faceId", "0"))
    data = _make_face_image(face_id)
    return web.Response(body=data, content_type="image/jpeg",
                        headers={"Cache-Control": "max-age=3600"})


# ──────────────── Cloud Connect Mock ────────────────

async def handle_cloud_connect(request):
    return web.json_response({
        "addresses": [{"address": get_local_ip(), "type": "ipv4", "port": 5666}],
        "ver": "1.0", "checkSum": "abc123",
    })


# ──────────────── WebSocket Handlers ────────────────

async def handle_login_ws(request):
    ws = web.WebSocketResponse()
    await ws.prepare(request)
    remote = request.remote
    log.info(f"WS login connection from {remote}")
    async for msg in ws:
        if msg.type == WSMsgType.TEXT:
            try:
                data = json.loads(msg.data)
                req = data.get("req", "")
                if req == "ping":
                    await ws.send_json({"res": "pong"})
                elif req == "util.crypto.getRSAPub":
                    await ws.send_json({"pub": RSA_PUBLIC_PEM, "si": LOGIN_SI})
                    log.info("Sent RSA public key")
                elif req == "encrypted":
                    aes_key = rsa_decrypt(data["rsa"]).decode('utf-8')
                    login_str = aes_decrypt(data["aes"], aes_key, data["iv"])
                    login = json.loads(login_str)
                    log.info(f"Login: user={login.get('user')}, device={login.get('deviceName')}")
                    token = "test_token_" + login.get("user", "u")
                    secret = "test_" + "secret_12345678"
                    TOKEN_CACHE[token] = {"user": login.get("user"), "secret": secret, "backId": "0000000000000001"}
                    await ws.send_json({
                        "result": "succ", "token": token, "secret": secret,
                        "backId": "0000000000000001", "user": login.get("user", ""),
                        "deviceName": login.get("deviceName", ""), "deviceType": login.get("deviceType", ""),
                    })
                    log.info(f"Login OK for {login.get('user')}")
                else:
                    log.warning(f"Unknown WS req: {req}")
            except Exception as e:
                log.error(f"WS error: {e}")
                try:
                    await ws.send_json({"errno": 500, "result": str(e)})
                except Exception:
                    pass
        elif msg.type == WSMsgType.ERROR:
            log.error(f"WS error: {ws.exception()}")
    return ws


async def handle_authenticated_ws(request):
    ws = web.WebSocketResponse()
    await ws.prepare(request)
    token = request.query.get("token", "")
    qs_path = request.match_info.get("channel", "main")
    log.info(f"WS {qs_path} connection token={token[:20]}...")
    HEARTBEAT_CLIENTS.add(ws)
    try:
        async for msg in ws:
            if msg.type == WSMsgType.TEXT:
                try:
                    payload = json.loads(msg.data)
                    if payload.get("type") == "heartbeat":
                        await ws.send_json({"type": "heartbeat", "timestamp": int(time.time() * 1000)})
                except Exception:
                    pass
            elif msg.type == WSMsgType.ERROR:
                break
    finally:
        HEARTBEAT_CLIENTS.discard(ws)
    return ws


# ──────────────── Auth Interceptor ────────────────

@web.middleware
async def auth_middleware(request, handler):
    if request.path.startswith("/websocket") or request.path.startswith("/ws/"):
        return await handler(request)
    if request.path.startswith("/p/api/v1/stream/"):
        return await handler(request)
    return await handler(request)


# ──────────────── Server Setup ────────────────

def make_self_signed_cert(cert_file, key_file, ip_str):
    if os.path.exists(cert_file) and os.path.exists(key_file):
        return
    key = rsa.generate_private_key(
        public_exponent=65537, key_size=2048, backend=default_backend()
    )
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COUNTRY_NAME, "CN"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "fnPhoto Test"),
        x509.NameAttribute(NameOID.COMMON_NAME, ip_str),
    ])
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject).issuer_name(issuer)
        .public_key(key.public_key()).serial_number(1000)
        .not_valid_before(datetime.now(timezone.utc))
        .not_valid_after(datetime.now(timezone.utc).replace(year=datetime.now(timezone.utc).year + 10))
        .add_extension(
            x509.SubjectAlternativeName([x509.IPAddress(ipaddress.IPv4Address(ip_str))]),
            critical=False,
        )
        .sign(key, hashes.SHA256(), backend=default_backend())
    )
    with open(cert_file, "wb") as f:
        f.write(cert.public_bytes(serialization.Encoding.PEM))
    with open(key_file, "wb") as f:
        f.write(key.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.TraditionalOpenSSL,
            encryption_algorithm=serialization.NoEncryption()
        ))
    log.info("Self-signed cert generated")


def build_app():
    app = web.Application(middlewares=[auth_middleware])

    # ── REST API routes ──
    # Albums
    app.router.add_get("/p/api/v1/album/list", handle_album_list)
    app.router.add_get("/p/api/v1/album/photos", handle_album_photos)
    # Folders
    app.router.add_get("/p/api/v1/photo/folder/list", handle_folder_list)
    app.router.add_get("/p/api/v1/folder_view/getFolderList", handle_sub_folder_list)
    app.router.add_get("/p/api/v1/folder_view/getFileList", handle_folder_file_list)
    # Gallery / Timeline
    app.router.add_get("/p/api/v1/gallery/timeline", handle_timeline)
    app.router.add_get("/p/api/v1/gallery/getList", handle_gallery_list)
    app.router.add_get("/p/api/v1/gallery/recent", handle_recent)
    app.router.add_get("/p/api/v1/explore/recent_timeline", handle_explore_recent_timeline)
    app.router.add_get("/p/api/v1/explore/geos", handle_geo_list)
    # Photo
    app.router.add_get("/p/api/v1/photo/detail/{id}", handle_photo_detail)
    app.router.add_post("/p/api/v1/photo/collect", handle_toggle_collect)
    app.router.add_get("/p/api/v1/photo/upload/path", handle_upload_path)
    app.router.add_get("/p/api/v1/photo/support/list", handle_support_types)
    app.router.add_get("/p/api/v1/photo/collect/list", handle_collect_list)
    # Search
    app.router.add_get("/p/api/v1/photo/search", handle_search)
    app.router.add_post("/p/api/v1/search/results", handle_search_results)
    # Server / User
    app.router.add_get("/p/api/v1/server/folder_manage", handle_storage)
    app.router.add_get("/p/api/v1/server/users_all", handle_users_all)
    app.router.add_get("/p/api/v1/server/sys_info", handle_sys_info)
    app.router.add_get("/p/api/v1/user/info", handle_user_info)
    app.router.add_get("/p/api/v1/user_photo/stat", handle_photo_stats)
    app.router.add_get("/p/api/v1/app/version", handle_app_version)
    # People
    app.router.add_get("/p/api/v1/ai-person/list", handle_person_list)
    app.router.add_get("/p/api/v1/ai-person/photoLibrary/timeLine", handle_person_timeline)
    app.router.add_get("/p/api/v1/ai-person/photoLibrary/list", handle_person_photos)
    # Stream / Media
    app.router.add_get("/p/api/v1/stream/p/t/{id}/o/{uuid}", handle_stream_original)
    app.router.add_get("/p/api/v1/stream/v/{id}", handle_stream_video)
    app.router.add_get("/p/api/v1/stream/face/{faceId}", handle_face_image)
    # Cloud connect
    app.router.add_post("/api/v1/fn/con", handle_cloud_connect)

    # ── WebSocket routes ──
    app.router.add_get("/websocket", handle_login_ws)
    app.router.add_get("/ws/{channel}", handle_authenticated_ws)

    return app


async def main():
    local_ip = get_local_ip()
    script_dir = os.path.dirname(os.path.abspath(__file__))
    cert = os.path.join(script_dir, "test_cert.pem")
    key = os.path.join(script_dir, "test_key.pem")
    make_self_signed_cert(cert, key, local_ip)
    generate_test_video()

    app = build_app()

    # Plain HTTP/WS server on 5666
    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, "0.0.0.0", 5666)
    await site.start()
    log.info(f"WS/HTTP : ws://{local_ip}:5666/websocket  http://{local_ip}:5666/p/api/v1/...")

    # TLS server on 9001
    ssl_ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ssl_ctx.load_cert_chain(cert, key)
    ssl_ctx.check_hostname = False
    ssl_ctx.verify_mode = ssl.CERT_NONE
    site_tls = web.TCPSite(runner, "0.0.0.0", 9001, ssl_context=ssl_ctx)
    await site_tls.start()
    log.info(f"WSS/HTTPS: wss://{local_ip}:9001/websocket  https://{local_ip}:9001/p/api/v1/...")

    print("=" * 66)
    print("  Mock fnOS Server Ready!")
    print(f"  WS  → ws://{local_ip}:5666/websocket?type=main")
    print(f"  WSS → wss://{local_ip}:9001/websocket?type=main")
    print(f"  API → http://{local_ip}:5666/p/api/v1/...")
    print(f"  Any username/password accepted. Login always succeeds.")
    print(f"  {PHOTO_COUNT} fake photos, {ALBUM_COUNT} albums, {PERSON_COUNT} persons")
    print("=" * 66)

    try:
        await asyncio.Future()
    except KeyboardInterrupt:
        pass
    finally:
        await runner.cleanup()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nShutting down...")
