#!/usr/bin/env python3
"""
济南路网 GeoJSON → Geohash5 .bin 二进制分片

输入: app/src/main/assets/jinan_roads_gcj02.geojson (GCJ-02, 56k 路段)
输出: scripts/roads_bin/ 目录，每个文件 = 一个 geohash-5 分片

.bin 格式 (little-endian):
  [magic:4B = 'RDB1']
  [edge_count:4B LE]
  [edges...]
    每条边:
      [road_class:1B]     0-9 (motorway=0, trunk=1, ...)
      [name_len:1B]
      [name:name_len bytes UTF-8]
      [point_count:2B LE]
      [points: point_count × 8B (lat:float32 LE, lng:float32 LE)]

文件命名: roads_bin/{geohash5}.bin
"""

import json
import struct
import os
import math

INPUT = "../app/src/main/assets/jinan_roads_gcj02.geojson"
OUTPUT_DIR = "roads_bin"

HIGHWAY_CLASS = {
    "motorway": 0, "motorway_link": 0,
    "trunk": 1, "trunk_link": 1,
    "primary": 2, "primary_link": 2,
    "secondary": 3, "secondary_link": 3,
    "tertiary": 4, "tertiary_link": 4,
    "residential": 5,
    "service": 6, "services": 6,
    "track": 7,
    "path": 8, "footway": 8, "steps": 8, "cycleway": 8, "pedestrian": 8,
}
DEFAULT_CLASS = 9  # unknown

BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"


def encode_geohash(lat, lng, precision=5):
    """标准 geohash 编码"""
    lat_min, lat_max = -90.0, 90.0
    lng_min, lng_max = -180.0, 180.0
    bits = 0
    bit_count = 0
    is_even = True
    result = []

    while len(result) < precision:
        if is_even:
            mid = (lng_min + lng_max) / 2
            if lng > mid:
                bits = (bits << 1) | 1
                lng_min = mid
            else:
                bits = (bits << 1)
                lng_max = mid
        else:
            mid = (lat_min + lat_max) / 2
            if lat > mid:
                bits = (bits << 1) | 1
                lat_min = mid
            else:
                bits = (bits << 1)
                lat_max = mid

        bit_count += 1
        is_even = not is_even

        if bit_count == 5:
            result.append(BASE32[bits])
            bits = 0
            bit_count = 0

    return "".join(result)


def midpoint(coords):
    """LineString 坐标中点"""
    if not coords:
        return (0, 0)
    avg_lat = sum(c[1] for c in coords) / len(coords)
    avg_lng = sum(c[0] for c in coords) / len(coords)
    return (avg_lat, avg_lng)


def write_edge(buf, road_class, name, coords):
    """写入一条路段到 buffer"""
    buf.extend(struct.pack("<B", road_class))
    name_bytes = name.encode("utf-8")[:255]
    buf.extend(struct.pack("<B", len(name_bytes)))
    buf.extend(name_bytes)
    buf.extend(struct.pack("<H", len(coords)))
    for lng, lat in coords:
        buf.extend(struct.pack("<ff", float(lat), float(lng)))


def main():
    print(f"Loading {INPUT}...")
    with open(INPUT, "r", encoding="utf-8") as f:
        data = json.load(f)

    features = data["features"]
    print(f"  {len(features)} features")

    # 按 geohash-5 分组
    bins = {}  # geohash5 → [(class, name, coords), ...]

    for feat in features:
        props = feat.get("properties", {})
        highway = props.get("highway", "")
        road_class = HIGHWAY_CLASS.get(highway, DEFAULT_CLASS)
        name = props.get("name", "") or ""

        coords = feat.get("geometry", {}).get("coordinates", [])
        if len(coords) < 2:
            continue

        # 用中点决定 geohash
        mid_lat, mid_lng = midpoint(coords)
        gh = encode_geohash(mid_lat, mid_lng, 5)

        if gh not in bins:
            bins[gh] = []
        bins[gh].append((road_class, name, coords))

    # 写 .bin 文件
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    total_size = 0

    for gh, edges in sorted(bins.items()):
        buf = bytearray()
        buf.extend(struct.pack("<4s", b"RDB1"))
        buf.extend(struct.pack("<I", len(edges)))

        for road_class, name, coords in edges:
            write_edge(buf, road_class, name, coords)

        path = os.path.join(OUTPUT_DIR, f"{gh}.bin")
        with open(path, "wb") as f:
            f.write(buf)

        file_size = len(buf)
        total_size += file_size
        print(f"  {gh}.bin: {len(edges)} edges, {file_size / 1024:.1f}KB")

    print(f"\nTotal: {len(bins)} files, {total_size / 1024 / 1024:.1f}MB")


if __name__ == "__main__":
    main()
