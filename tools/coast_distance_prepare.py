#!/usr/bin/env python3
"""
Build the offline coast-distance grid shipped as an app asset.

Input : ne_110m_land.geojson  (Natural Earth public-domain land polygons;
        downloaded from the nvkelso/natural-earth-vector GitHub mirror).
Output: coast_distance_india.bin - a byte grid over the India bbox where each
        cell holds the APPROXIMATE distance to the coastline in whole km
        (0 = at the coast / sea side, 255 = >=255 km inland / unmeasured cap).

Method: 1) rasterise land vs sea at 0.25 deg on the bbox
        2) multi-source BFS from every sea-land boundary cell over the land
           mask gives boundary distance; cells inside sea stay 0;
        3) cells far from any boundary get distance to the bbox edge? No:
           sea is known, land distance is measured TO the coastline. A cell
           is coastline-adjacent if it differs in land/sea from a neighbour.

The grid is deterministic: same input -> same bytes. The app reads it
nearest-neighbour and uses `distance_km <= threshold` as a REAL coast-
proximity input for the terrain suitability engine - never a guess.

Usage:  python tools/coast_distance_prepare.py <path-to-geojson>
"""
import json
import math
import struct
import sys
from collections import deque
from pathlib import Path

import numpy as np

OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "emdat" / ".." / "geo" / "coast_distance_india.bin"
LON_MIN, LON_MAX = 67.0, 98.0
LAT_MIN, LAT_MAX = 6.0, 37.5
STEP_DEG = 0.25
CAP_KM = 255


def load_polygons(path: Path):
    data = json.loads(path.read_text(encoding="utf-8"))
    polys = []
    for feat in data["features"]:
        geom = feat["geometry"]
        rings = geom["coordinates"] if geom["type"] == "Polygon" else [
            r for poly in geom["coordinates"] for r in [poly[0]]
        ]
        for ring in rings:
            polys.append(np.asarray(ring, dtype=np.float64))
    return polys


def point_in_ring(x, y, ring):
    """Vectorised ray-cast test for one ring against arrays of points."""
    inside = np.zeros(x.shape, dtype=bool)
    n = len(ring)
    j = n - 1
    for i in range(n):
        xi, yi = ring[i]
        xj, yj = ring[j]
        cond = ((yi > y) != (yj > y)) & (x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi)
        inside ^= cond
        j = i
    return inside


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 1
    geojson = Path(sys.argv[1])
    polys = load_polygons(geojson)

    lats = np.arange(LAT_MIN, LAT_MAX + 1e-9, STEP_DEG)
    lons = np.arange(LON_MIN, LON_MAX + 1e-9, STEP_DEG)
    lon_grid, lat_grid = np.meshgrid(lons, lats)
    flat_lon, flat_lat = lon_grid.ravel(), lat_grid.ravel()

    land = np.zeros(flat_lon.shape, dtype=bool)
    for ring in polys:
        # cheap bbox reject
        if ring[:, 1].max() < LAT_MIN or ring[:, 1].min() > LAT_MAX or \
           ring[:, 0].max() < LON_MIN or ring[:, 0].min() > LON_MAX:
            continue
        land |= point_in_ring(flat_lon, flat_lat, ring)
    land = land.reshape(len(lats), len(lons))

    ny, nx = land.shape
    km_per_deg_lat = 110.574
    deg_cells = []  # (row, col, dx_km, dy_km)
    for dr, dc in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
        dy = abs(dr) * km_per_deg_lat * STEP_DEG
        dx = abs(dc) * nx * 0  # placeholder, compute per-row below
        deg_cells.append((dr, dc))

    # BFS distance from coastline (boundary between land and sea). Because the
    # per-cell step length depends on the row (lon km/deg shrinks with |lat|),
    # plain BFS is not exact; Dijkstra over a small frontier is, and the grid
    # is only 83x125 cells.
    import heapq
    dist = np.full((ny, nx), np.inf, dtype=np.float64)
    heap = []
    for r in range(ny):
        for c in range(nx):
            is_land = land[r, c]
            coastal = False
            for dr, dc in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                rr, cc = r + dr, c + dc
                if 0 <= rr < ny and 0 <= cc < nx:
                    if land[rr, cc] != is_land:
                        coastal = True
                        break
                else:
                    # bbox edge: treat out-of-bbox as sea so coastal strips
                    # touching the border are detected
                    coastal = True
                    break
            if coastal:
                dist[r, c] = 0.0
                heapq.heappush(heap, (0.0, r, c))

    km_per_deg_lat = 110.574
    while heap:
        d, r, c = heapq.heappop(heap)
        if d > dist[r, c] + 1e-9:
            continue
        for dr, dc in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            rr, cc = r + dr, c + dc
            if not (0 <= rr < ny and 0 <= cc < nx):
                continue
            if land[rr, cc] != land[r, c]:
                continue
            km_lon = 110.574 * math.cos(math.radians(lats[rr])) * STEP_DEG
            step_km = math.hypot(abs(dc) * km_lon, abs(dr) * km_per_deg_lat * STEP_DEG)
            nd = d + step_km
            if nd < dist[rr, cc] - 1e-9:
                dist[rr, cc] = nd
                heapq.heappush(heap, (nd, rr, cc))

    # Sea cells stay 0 (they are at/below the coastline by definition);
    # unmeasured inland cells cap at 255.
    dist[~land] = 0.0
    grid = np.clip(np.rint(dist), 0, CAP_KM).astype(np.uint8)

    header = struct.pack(
        "<4f2H",
        float(LON_MIN), float(LAT_MIN), float(LON_MAX), float(LAT_MAX),
        nx, ny
    )
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(header + grid.tobytes(order="C"))
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes) land fraction={land.mean():.3f}")
    # sanity spot checks
    def at(lat, lon):
        r = int(round((lat - LAT_MIN) / STEP_DEG))
        c = int(round((lon - LON_MIN) / STEP_DEG))
        return int(grid[r, c]), bool(land[r, c])
    print("Chennai coast 13.06,80.27 ->", at(13.06, 80.27))
    print("Madurai inland 9.92,78.12 ->", at(9.92, 78.12))
    print("Bay of Bengal 15.0,85.0 ->", at(15.0, 85.0))
    print("Delhi 28.6,77.2 ->", at(28.6, 77.2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
