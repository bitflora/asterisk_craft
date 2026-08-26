#!/usr/bin/env python3
"""Convert a WAV from import/ into the Ogg Vorbis a Minecraft sound event needs.

Most of the archive clips in ``import/`` are already ``.oga`` — byte-identical Ogg that only
needs renaming into ``assets/asteriskcraft/sounds/mob/<unit>/`` (see docs/terran-humanoids.md).
A handful are ``.wav`` instead, and Minecraft's sound loader reads Ogg Vorbis only, so those
have to be transcoded. This is the whole transcode step, so a unit whose only clip is a WAV
doesn't need ffmpeg installed to be finished.

Uses ``soundfile`` (libsndfile), which is already what the texture tooling's environment has;
there is no ffmpeg on the dev machine this was written on.

    python tools/wav_to_ogg.py import/hkmissle.wav missile_turret attack

writes ``src/main/resources/assets/asteriskcraft/sounds/mob/missile_turret/missile_turret-attack.ogg``,
which is the path ``sounds.json`` names as ``asteriskcraft:mob/missile_turret/missile_turret-attack``.
"""

import sys
from pathlib import Path

import soundfile as sf

ROOT = Path(__file__).resolve().parent.parent
SOUND_DIR = ROOT / "src/main/resources/assets/asteriskcraft/sounds/mob"


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__)
        return 2
    source, unit, event = Path(argv[0]), argv[1], argv[2]
    if not source.exists():
        raise SystemExit(f"No such file: {source}")

    data, rate = sf.read(str(source), always_2d=False)
    out = SOUND_DIR / unit / f"{unit}-{event}.ogg"
    out.parent.mkdir(parents=True, exist_ok=True)
    sf.write(str(out), data, rate, format="OGG", subtype="VORBIS")

    print(f"{source} -> {out.relative_to(ROOT)}  ({len(data) / rate:.2f}s @ {rate} Hz)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
