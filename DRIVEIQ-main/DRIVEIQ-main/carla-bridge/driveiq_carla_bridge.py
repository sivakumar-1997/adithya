#!/usr/bin/env python3
"""
DriveIQ <-> CARLA Simulator Bridge Adapter (Volkswagen / BMW OEM Demo Ready)
Streams live CARLA ego-vehicle telemetry to the DriveIQ Android/Web dashboard over HTTP POST or WebSocket.

Usage:
  1) Start DriveIQ app -> CARLA & Web tab -> Enable "Local Web & CARLA Ingest Server"
  2) Run on your CARLA workstation:
       python3 driveiq_carla_bridge.py --target http://<ANDROID_IP>:8765/api/carla/telemetry --carla-host localhost --carla-port 2000
"""

import argparse
import json
import math
import time
import urllib.request

try:
    import carla  # Official CARLA Python API
except ImportError:
    carla = None


def push_frame(url: str, payload: dict) -> None:
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=1.0) as resp:
        resp.read()


def main():
    parser = argparse.ArgumentParser(description="DriveIQ CARLA Telemetry Bridge")
    parser.add_argument("--target", required=True, help="DriveIQ ingest URL e.g. http://192.168.1.42:8765/api/carla/telemetry")
    parser.add_argument("--carla-host", default="localhost")
    parser.add_argument("--carla-port", type=int, default=2000)
    parser.add_argument("--speed-limit", type=int, default=80)
    args = parser.parse_args()

    if carla is None:
        raise RuntimeError("CARLA Python package not installed. Install carla>=0.9.14 on the simulator workstation.")

    client = carla.Client(args.carla_host, args.carla_port)
    client.set_timeout(5.0)
    world = client.get_world()

    ego = None
    for actor in world.get_actors().filter("vehicle.*"):
        if actor.attributes.get("role_name") in ("hero", "ego_vehicle"):
            ego = actor
            break
    if ego is None:
        vehicles = list(world.get_actors().filter("vehicle.*"))
        if not vehicles:
            raise RuntimeError("No ego vehicle found in CARLA world.")
        ego = vehicles[0]

    print(f"Streaming CARLA vehicle {ego.type_id} -> {args.target}")
    while True:
        vel = ego.get_velocity()
        acc = ego.get_acceleration()
        ctrl = ego.get_control()
        speed_ms = math.sqrt(vel.x**2 + vel.y**2 + vel.z**2)
        speed_kmh = speed_ms * 3.6
        steer_deg = ctrl.steer * 45.0
        gear = f"D{max(1, ctrl.gear)}" if ctrl.gear > 0 else ("R" if ctrl.gear < 0 else "N")
        rpm = int(850 + (speed_kmh / 200.0) * 6200 + ctrl.throttle * 900)

        frame = {
            "speed_kmh": round(speed_kmh, 2),
            "rpm": rpm,
            "throttle": round(ctrl.throttle, 3),
            "brake": round(ctrl.brake, 3),
            "steer_angle": round(steer_deg, 2),
            "accel_x": round(acc.x, 3),
            "accel_y": round(acc.y, 3),
            "gear": gear,
            "speed_limit_kmh": args.speed_limit,
            "source": f"CARLA ({ego.type_id})",
            "timestamp_ms": int(time.time() * 1000),
        }
        try:
            push_frame(args.target, frame)
        except Exception as exc:
            print(f"Push error: {exc}")
        time.sleep(0.05)


if __name__ == "__main__":
    main()
