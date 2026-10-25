#!/usr/bin/env python3
"""Build the bundled generic-foods database for Tare.

Reads USDA FoodData Central "SR Legacy" (April 2018 release, public domain / CC0 1.0)
plus the hand-curated overrides in ``curated.csv`` and writes
``app/src/main/assets/generic_foods.tsv``.

Usage (from the repository root)::

    python tools/generic-foods/build.py            # downloads raw data if missing, then builds
    python tools/generic-foods/build.py --review   # also writes raw/curated_review.tsv

The raw USDA download lives in ``tools/generic-foods/raw/`` (gitignored). If it is
missing, the script downloads and extracts the official zip from fdc.nal.usda.gov.
Only the Python 3 standard library is used.

Output: UTF-8, LF line endings, tab-separated, header row, no quoting::

    id  name  aliases  category  kcal  protein  carbs  fat  fiber  portions  popularity

Nutrients are per 100 g. ``portions`` is ``label=grams`` pairs joined by ``;``.
Rows are sorted by popularity (desc), then name.
"""
from __future__ import annotations

import argparse
import csv
import re
import shutil
import sys
import urllib.request
import zipfile
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
RAW_DIR = SCRIPT_DIR / "raw"
ZIP_NAME = "FoodData_Central_sr_legacy_food_csv_2018-04.zip"
ZIP_URL = "https://fdc.nal.usda.gov/fdc-datasets/" + ZIP_NAME
DATA_DIR = RAW_DIR / "FoodData_Central_sr_legacy_food_csv_2018-04"
CURATED_PATH = SCRIPT_DIR / "curated.csv"
OUTPUT_PATH = REPO_ROOT / "app" / "src" / "main" / "assets" / "generic_foods.tsv"
REVIEW_PATH = RAW_DIR / "curated_review.tsv"

REQUIRED_FILES = [
    "food.csv",
    "food_nutrient.csv",
    "nutrient.csv",
    "food_category.csv",
    "food_portion.csv",
    "measure_unit.csv",
]

# FDC nutrient id -> (output field, expected nutrient.csv name prefix)
NUTRIENTS = {
    "1008": ("kcal", "Energy"),
    "1003": ("protein", "Protein"),
    "1005": ("carbs", "Carbohydrate, by difference"),
    "1004": ("fat", "Total lipid (fat)"),
    "1079": ("fiber", "Fiber, total dietary"),
}
REQUIRED_NUTRIENTS = ("kcal", "protein", "carbs", "fat")

HEADER = ["id", "name", "aliases", "category", "kcal", "protein", "carbs", "fat", "fiber", "portions", "popularity"]

MAX_PORTIONS = 4

# --- Exclusions --------------------------------------------------------------------------

EXCLUDED_CATEGORIES = {"Baby Foods", "Quality Control Materials"}

# Medical / meal-replacement products and formulas: not foods a person logs as "generic".
# (Plain protein powders are kept; people do log those.)
EXCLUDED_DESCRIPTION = re.compile(
    r"nutritional supplement|meal supplement|protein supplement|meal replacement"
    r"|nutritional shake|nutritional drink|instant breakfast|infant formula"
    r"|\bensure\b|glucerna|pediasure|nestle, boost|slim-?fast|muscle milk"
    r"|electrolyte solution|pedialyte",
    re.IGNORECASE,
)

# --- Name tidying ------------------------------------------------------------------------

NAME_NOISE = [
    re.compile(r"\s*\(\s*includes foods for usda'?s food distribution program\s*\)", re.IGNORECASE),
    re.compile(r",?\s*\(?\s*includes usda commodity food[^),]*\)?", re.IGNORECASE),
    re.compile(r",?\s*\bUPC\s*:?\s*[\d\s-]{6,}", re.IGNORECASE),
    re.compile(r",?\s*\bGTIN\s*:?\s*[\d\s-]{6,}", re.IGNORECASE),
    re.compile(r",?\s*NFSMI Recipe No\.?\s*[\w-]+", re.IGNORECASE),
    re.compile(r",\s*NFS\b"),
    re.compile(r"\bNFS\b"),
]

# All-caps tokens that should stay all-caps (acronyms); every other ALL-CAPS word
# (mostly brand names in SR Legacy, e.g. "KELLOGG'S") is converted to Title case.
KEEP_UPPER = {
    "USDA", "NLEA", "UHT", "KFC", "BBQ", "DHA", "EPA", "MSG", "TCBY", "IHOP", "UPC",
    "II", "III", "IV", "EAS", "A&W", "M&M", "M&M'S", "USA", "US", "LS", "TGI", "PB",
    "RTE", "RTD", "XL", "NY", "UV", "BLT",
}
CAPS_WORD = re.compile(r"[A-Za-z][A-Za-z'&.\-]*[A-Za-z]")


def _title_word(word: str) -> str:
    parts = word.split("-")
    out = []
    for part in parts:
        if not part:
            out.append(part)
            continue
        low = part.lower()
        if low.startswith("mc") and len(low) > 3:
            out.append("Mc" + low[2].upper() + low[3:])
        else:
            out.append(low[0].upper() + low[1:])
    return "-".join(out)


def _fix_caps(match: re.Match) -> str:
    word = match.group(0)
    if re.fullmatch(r"Mc[A-Z][A-Z'&.\-]*", word):  # "McDONALD'S" -> "McDonald's"
        return "Mc" + _title_word(word[2:])
    if word != word.upper():
        return word  # already mixed/lower case: keep USDA's casing
    if word.strip(".") in KEEP_UPPER or word in KEEP_UPPER:
        return word
    return _title_word(word)


def clean_text(text: str) -> str:
    """Strip tabs/newlines and collapse whitespace (output has no quoting)."""
    return re.sub(r"\s+", " ", text.replace("\t", " ").replace("\r", " ").replace("\n", " ")).strip()


def tidy_name(description: str) -> str:
    name = description
    for pattern in NAME_NOISE:
        name = pattern.sub("", name)
    name = clean_text(name)
    name = re.sub(r"\(\s+", "(", name)
    name = re.sub(r"\s+\)", ")", name)
    name = re.sub(r"\s+,", ",", name)
    name = re.sub(r",(\s*,)+", ",", name)
    name = re.sub(r",(?=\S)", ", ", name)
    name = name.strip(" ,;")
    name = CAPS_WORD.sub(_fix_caps, name)
    if name:
        name = name[0].upper() + name[1:]
    return name


# --- Portions ----------------------------------------------------------------------------

JUNK_PORTION = re.compile(r"undetermined|quantity not specified|not specified", re.IGNORECASE)
WEIGHT_ONLY = re.compile(r"^(oz|ounce|ounces|lb|lbs|pound|pounds|g|gram|grams|kg)\b")
MEANINGLESS = re.compile(r"^(unit|units)\b")
VOLUME = re.compile(
    r"^(cup|cups|tbsp|tablespoon|tablespoons|tsp|teaspoon|teaspoons|fl oz|ml|milliliter|liter|litre)\b"
)
SUB_PORTION = re.compile(
    r"^(slices?|strips?|pieces?|rings?|spears?|lea(f|ves)|wedges?|chunks?|cubes?|sticks?|florets?"
    r"|sprigs?|segments?|sections?|tbsp|tsp|cups?|servings?)\b"
)
DEMOTED = re.compile(
    r"nlea serving|^serving|^fl oz|extra small|extra large|jumbo|^quart|^pint|^gallon|^liter|^cubic"
    r"|yield|^package|^loaf|^pie\b|^cake\b|^pizza\b|^roast\b|^bird\b|^chicken\b|^recipe|^batch"
)


def fmt_number(value: float, decimals: int) -> str:
    text = f"{value:.{decimals}f}"
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text if text not in ("-0", "") else "0"


def clean_modifier(text: str) -> str:
    s = text
    previous = None
    while previous != s:  # remove (possibly nested) parenthetical descriptions
        previous = s
        s = re.sub(r"\([^()]*\)", " ", s)
    s = re.sub(r"\(.*$", " ", s)  # unbalanced "(" -> drop the rest
    s = s.replace(")", " ")
    s = s.lower()
    s = clean_text(s)
    s = re.sub(r",?\s*\bns as to\b.*$", "", s)  # "avocado, NS as to Florida or California"
    s = re.sub(r"\s*\bnfs\b", "", s)
    s = re.sub(r"\s*\b(serving size )?varied (from|on|by)\b.*$", "", s)  # "serving serving size varied ..."
    s = re.sub(r"\bserving serving\b", "serving", s)
    s = re.sub(r"\s*\bapprox\.?\s.*$", "", s)  # long dimension descriptions
    s = re.sub(r"(?<=[a-z])\s+\d+(?:[./]\d+)?\s*(?:fl oz|oz|lb|ml|g)\b", "", s)  # "can 12 fl oz" -> "can"
    s = re.sub(r"\b(\w+)( \1\b)+", r"\1", s)  # "can can" -> "can"
    s = re.sub(r"\s+,", ",", s)
    s = re.sub(r",(\s*,)+", ",", s)
    s = s.replace(";", ",").replace("=", "-")
    return s.strip(" ,:-")


def portion_tier(modifier: str) -> int:
    """0 = standard "medium" item, 1 = other countable household units, 2 = volume measures,
    3 = low-value measures (NLEA serving, fl oz, whole packages/pies, ...)."""
    if DEMOTED.search(modifier):
        return 3
    if re.search(r"\bmedium\b", modifier) and not SUB_PORTION.match(modifier):
        return 0
    if VOLUME.match(modifier):
        return 2
    return 1


def build_portions(rows: list[dict], units: dict[str, str]) -> str:
    candidates = []
    for row in rows:
        try:
            amount = float(row["amount"] or "1")
            grams = float(row["gram_weight"] or "0")
        except ValueError:
            continue
        if amount <= 0 or grams <= 0:
            continue
        raw = row.get("modifier") or ""
        if not raw.strip():
            raw = row.get("portion_description") or ""
        if not raw.strip():
            unit = units.get(row.get("measure_unit_id", ""), "")
            raw = "" if unit == "undetermined" else unit
        if JUNK_PORTION.search(raw):
            continue
        modifier = clean_modifier(raw)
        if not modifier or WEIGHT_ONLY.match(modifier) or MEANINGLESS.match(modifier):
            continue
        label = f"{fmt_number(amount, 2)} {modifier}"
        try:
            seq = float(row.get("seq_num") or "inf")
        except ValueError:
            seq = float("inf")
        candidates.append((portion_tier(modifier), seq, label, grams))
    candidates.sort(key=lambda c: (c[0], c[1]))
    # Low-value measures (tier 3) are only used when nothing better exists.
    if any(c[0] < 3 for c in candidates):
        candidates = [c for c in candidates if c[0] < 3]
    seen = set()
    picked = []
    for _tier, _seq, label, grams in candidates:
        if label in seen:
            continue
        seen.add(label)
        picked.append(f"{label}={fmt_number(round(grams, 1), 1)}")
        if len(picked) == MAX_PORTIONS:
            break
    return ";".join(picked)


# --- IO helpers --------------------------------------------------------------------------


def ensure_raw_data() -> None:
    if all((DATA_DIR / f).exists() for f in REQUIRED_FILES):
        return
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    zip_path = RAW_DIR / ZIP_NAME
    if not zip_path.exists():
        print(f"Downloading {ZIP_URL}")
        partial = zip_path.with_name(zip_path.name + ".part")
        with urllib.request.urlopen(ZIP_URL, timeout=300) as response, open(partial, "wb") as out:
            shutil.copyfileobj(response, out)
        partial.replace(zip_path)
    print(f"Extracting {zip_path.name}")
    root = RAW_DIR.resolve()
    with zipfile.ZipFile(zip_path) as archive:
        for member in archive.infolist():
            target = (RAW_DIR / member.filename).resolve()
            if root not in target.parents and target != root:
                raise SystemExit(f"Refusing to extract unsafe path: {member.filename}")
        archive.extractall(RAW_DIR)
    missing = [f for f in REQUIRED_FILES if not (DATA_DIR / f).exists()]
    if missing:
        raise SystemExit(f"Extracted archive is missing: {', '.join(missing)}")


def read_csv(name: str):
    with open(DATA_DIR / name, encoding="utf-8-sig", newline="") as handle:
        yield from csv.DictReader(handle)


def load_curated(path: Path) -> tuple[list[dict], list[str]]:
    errors = []
    rows = []
    with open(path, encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        expected = ["fdc_id", "display_name", "aliases", "popularity"]
        if reader.fieldnames is None or [f.strip() for f in reader.fieldnames] != expected:
            raise SystemExit(f"{path.name}: header must be {','.join(expected)} (got {reader.fieldnames})")
        for line_no, row in enumerate(reader, start=2):
            fdc_id = (row["fdc_id"] or "").strip()
            name = clean_text(row["display_name"] or "")
            aliases_raw = row["aliases"] or ""
            pop_raw = (row["popularity"] or "").strip()
            if not fdc_id.isdigit():
                errors.append(f"curated.csv:{line_no}: bad fdc_id {fdc_id!r}")
                continue
            if not name:
                errors.append(f"curated.csv:{line_no}: empty display_name")
                continue
            try:
                popularity = int(pop_raw)
            except ValueError:
                errors.append(f"curated.csv:{line_no}: bad popularity {pop_raw!r}")
                continue
            if not 1 <= popularity <= 100:
                errors.append(f"curated.csv:{line_no}: popularity {popularity} outside 1..100")
            aliases = []
            seen = {name.casefold()}
            for alias in aliases_raw.split("|"):
                alias = clean_text(alias)
                if alias and alias.casefold() not in seen:
                    seen.add(alias.casefold())
                    aliases.append(alias)
            rows.append({"line": line_no, "fdc_id": fdc_id, "name": name, "aliases": aliases, "popularity": popularity})
    return rows, errors


# --- Main --------------------------------------------------------------------------------


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--review", action="store_true", help=f"also write {REVIEW_PATH.relative_to(REPO_ROOT)}")
    parser.add_argument("--output", type=Path, default=OUTPUT_PATH, help="output TSV path")
    args = parser.parse_args()

    ensure_raw_data()

    # Sanity-check the nutrient ids against nutrient.csv.
    nutrient_names = {r["id"]: r["name"] for r in read_csv("nutrient.csv")}
    for nid, (_field, expected) in NUTRIENTS.items():
        if not nutrient_names.get(nid, "").startswith(expected):
            raise SystemExit(f"nutrient {nid} is {nutrient_names.get(nid)!r}, expected {expected!r}")

    categories = {r["id"]: clean_text(r["description"]) for r in read_csv("food_category.csv")}
    units = {r["id"]: r["name"].strip() for r in read_csv("measure_unit.csv")}
    foods = {r["fdc_id"]: r for r in read_csv("food.csv")}

    nutrients: dict[str, dict[str, float]] = {}
    for r in read_csv("food_nutrient.csv"):
        spec = NUTRIENTS.get(r["nutrient_id"])
        if spec is None or r["fdc_id"] not in foods:
            continue
        try:
            nutrients.setdefault(r["fdc_id"], {})[spec[0]] = float(r["amount"])
        except ValueError:
            pass

    portions_by_food: dict[str, list[dict]] = {}
    for r in read_csv("food_portion.csv"):
        portions_by_food.setdefault(r["fdc_id"], []).append(r)

    curated, errors = load_curated(CURATED_PATH)
    curated_by_id: dict[str, dict] = {}
    curated_names: dict[str, str] = {}
    for row in curated:
        fid = row["fdc_id"]
        food = foods.get(fid)
        if food is None:
            errors.append(f"curated.csv:{row['line']}: fdc_id {fid} not in food.csv")
            continue
        if fid in curated_by_id:
            errors.append(f"curated.csv:{row['line']}: duplicate fdc_id {fid}")
            continue
        key = row["name"].casefold()
        if key in curated_names:
            errors.append(f"curated.csv:{row['line']}: duplicate display_name {row['name']!r} (also {curated_names[key]})")
            continue
        category = categories.get(food["food_category_id"], "")
        if category in EXCLUDED_CATEGORIES or EXCLUDED_DESCRIPTION.search(food["description"]):
            errors.append(f"curated.csv:{row['line']}: fdc_id {fid} is in an excluded category/pattern")
        n = nutrients.get(fid, {})
        if any(k not in n for k in REQUIRED_NUTRIENTS):
            errors.append(f"curated.csv:{row['line']}: fdc_id {fid} lacks energy/macros")
        curated_by_id[fid] = row
        curated_names[key] = fid
    if errors:
        print("\n".join(errors), file=sys.stderr)
        print(f"{len(errors)} curated.csv error(s); aborting.", file=sys.stderr)
        return 1

    stats = {"excluded_category": 0, "excluded_pattern": 0, "missing_nutrients": 0, "duplicates": 0}
    records = []
    for fid, food in foods.items():
        category = categories.get(food["food_category_id"], "")
        if category in EXCLUDED_CATEGORIES:
            stats["excluded_category"] += 1
            continue
        if EXCLUDED_DESCRIPTION.search(food["description"]):
            stats["excluded_pattern"] += 1
            continue
        n = nutrients.get(fid, {})
        if any(k not in n for k in REQUIRED_NUTRIENTS):
            stats["missing_nutrients"] += 1
            continue
        cur = curated_by_id.get(fid)
        name = cur["name"] if cur else tidy_name(food["description"])
        portions = build_portions(portions_by_food.get(fid, []), units)
        records.append({
            "fdc_id": fid,
            "name": clean_text(name),
            "aliases": "|".join(cur["aliases"]) if cur else "",
            "category": category,
            "kcal": n["kcal"],
            "protein": n["protein"],
            "carbs": n["carbs"],
            "fat": n["fat"],
            "fiber": n.get("fiber", 0.0),
            "portions": portions,
            "popularity": cur["popularity"] if cur else 0,
            "curated": cur is not None,
            "description": food["description"],
        })

    # Deduplicate exact (case-insensitive) name collisions: keep the more common entry.
    def rank(rec: dict):
        return (-rec["popularity"], not rec["curated"], -rec["portions"].count("="), int(rec["fdc_id"]))

    best: dict[str, dict] = {}
    dropped = []
    for rec in records:
        key = rec["name"].casefold()
        other = best.get(key)
        if other is None:
            best[key] = rec
            continue
        keep, lose = (rec, other) if rank(rec) < rank(other) else (other, rec)
        best[key] = keep
        dropped.append((lose, keep))
    for lose, keep in dropped:
        if lose["curated"]:
            print(f"ERROR: curated {lose['fdc_id']} {lose['name']!r} collides with {keep['fdc_id']}", file=sys.stderr)
            return 1
    stats["duplicates"] = len(dropped)

    rows = sorted(best.values(), key=lambda r: (-r["popularity"], r["name"].casefold(), int(r["fdc_id"])))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with open(args.output, "w", encoding="utf-8", newline="\n") as out:
        out.write("\t".join(HEADER) + "\n")
        for r in rows:
            fields = [
                f"usda:{r['fdc_id']}",
                r["name"],
                r["aliases"],
                r["category"],
                f"{r['kcal']:.1f}",
                f"{r['protein']:.2f}",
                f"{r['carbs']:.2f}",
                f"{r['fat']:.2f}",
                f"{r['fiber']:.2f}",
                r["portions"],
                str(r["popularity"]),
            ]
            out.write("\t".join(clean_text(f) for f in fields) + "\n")

    if args.review:
        RAW_DIR.mkdir(parents=True, exist_ok=True)
        with open(REVIEW_PATH, "w", encoding="utf-8", newline="\n") as rev:
            rev.write("fdc_id\tpopularity\tdisplay_name\tusda_description\tcategory\tkcal\tprotein\tcarbs\tfat\taliases\tportions\n")
            for r in sorted((r for r in rows if r["curated"]), key=lambda r: (-r["popularity"], r["name"].casefold())):
                rev.write("\t".join([
                    r["fdc_id"], str(r["popularity"]), r["name"], r["description"], r["category"],
                    f"{r['kcal']:.1f}", f"{r['protein']:.2f}", f"{r['carbs']:.2f}", f"{r['fat']:.2f}",
                    r["aliases"], r["portions"],
                ]) + "\n")

    size = args.output.stat().st_size
    print(f"Wrote {args.output} ({size:,} bytes)")
    print(f"  rows: {len(rows)} (curated: {sum(1 for r in rows if r['curated'])})")
    print(f"  excluded by category: {stats['excluded_category']}, by pattern: {stats['excluded_pattern']}, "
          f"missing nutrients: {stats['missing_nutrients']}, duplicate names dropped: {stats['duplicates']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
