#!/usr/bin/env bash
# ==============================================================================
#  Re-embed the deploy scripts into the spec's appendices, or check they match.
#
#  Appendix A of docs/healthconnect-marketplace.md IS deploy/deploy-dev.sh, and Appendix B IS
#  deploy/deploy-prod.sh — the same bytes in two places. That is a documentation decision worth
#  keeping (the spec stays readable standalone), but two copies drift unless something enforces it.
#
#  Usage:
#     ./sync-appendices.sh          # re-embed the scripts into the spec
#     ./sync-appendices.sh --check  # exit non-zero if they differ; changes nothing
#
#  Run --check before trusting the spec, and the bare form after editing either script.
# ==============================================================================
set -Eeuo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPEC="$DEPLOY_DIR/../docs/healthconnect-marketplace.md"
MODE="${1:-write}"

case "$MODE" in
  --check|-c) MODE="check" ;;
  write|"")   MODE="write" ;;
  -h|--help)  sed -n '2,16p' "$0"; exit 0 ;;
  *)          printf 'unknown option: %s (try --help)\n' "$1" >&2; exit 2 ;;
esac

python3 - "$SPEC" "$DEPLOY_DIR" "$MODE" <<'PY'
import re, sys, pathlib

spec_path, deploy_dir, mode = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), sys.argv[3]
spec = spec_path.read_text()

# (appendix letter, script filename, heading text)
APPENDICES = [
    ("A", "deploy-dev.sh",  "## Appendix A — `deploy/deploy-dev.sh`"),
    ("B", "deploy-prod.sh", "## Appendix B — `deploy/deploy-prod.sh`"),
]

failures, updated = [], []
for letter, filename, heading in APPENDICES:
    script = (deploy_dir / filename).read_text().rstrip("\n")
    # the fenced bash block that follows this appendix's heading
    pattern = re.compile(
        r"(?P<head>" + re.escape(heading) + r".*?\n```bash\n)(?P<body>.*?)(?P<tail>\n```)",
        re.S,
    )
    match = pattern.search(spec)
    if not match:
        failures.append(f"Appendix {letter}: heading or bash block not found in {spec_path.name}")
        continue
    if match.group("body") == script:
        print(f"  ok   Appendix {letter} matches {filename}")
        continue
    if mode == "check":
        failures.append(f"Appendix {letter} has drifted from {filename}")
        print(f"  FAIL Appendix {letter} has drifted from {filename}")
    else:
        spec = spec[: match.start("body")] + script + spec[match.end("body") :]
        updated.append(f"Appendix {letter} <- {filename}")
        print(f"  re-embedded Appendix {letter} from {filename}")

if mode == "check":
    sys.exit(1 if failures else 0)

if failures:
    print("\n".join(failures), file=sys.stderr)
    sys.exit(1)
if updated:
    spec_path.write_text(spec)
    print(f"\nUpdated {spec_path}")
else:
    print("\nNothing to do — both appendices already match.")
PY
