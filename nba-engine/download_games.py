"""Opt-in download for personal research. Respect source terms and rate limits."""
import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen

URL = "https://huggingface.co/datasets/hamzas/nba-games/resolve/main/games.csv"

def download(destination):
    target = Path(destination)
    if target.exists():
        raise FileExistsError(f"Refusing to overwrite existing dataset: {target}")
    target.parent.mkdir(parents=True, exist_ok=True)
    req = Request(URL, headers={"User-Agent": "s2s-sigma-personal-research/0.1"})
    sha = hashlib.sha256()
    temp = target.with_suffix(target.suffix + ".part")
    try:
        with urlopen(req, timeout=60) as response, temp.open("wb") as out:
            if response.status != 200:
                raise RuntimeError(f"HTTP {response.status}")
            while chunk := response.read(1024 * 1024):
                out.write(chunk)
                sha.update(chunk)
        temp.rename(target)
    finally:
        temp.unlink(missing_ok=True)
    meta = {"source": URL, "retrieved_at_utc": datetime.now(timezone.utc).isoformat(),
            "sha256": sha.hexdigest(), "purpose": "personal research",
            "rights_review": "required before redistribution or commercialization"}
    target.with_suffix(".provenance.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
    return meta

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="data/games.csv")
    args = parser.parse_args()
    print(json.dumps(download(args.out), indent=2))
