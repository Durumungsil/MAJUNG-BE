#!/usr/bin/env python3
"""Convert the 소상공인시장진흥공단 부산 상가 CSV to the store import format."""

import argparse
import csv
from pathlib import Path


CATEGORY_CODES = {
    "G2": "RETAIL",
    "I1": "LODGING",
    "I2": "FOOD",
    "M1": "SCIENCE_TECHNOLOGY",
    "R1": "ART_SPORTS",
}

EXCLUDED_CATEGORY_CODES = {"L1", "N1", "P1", "Q1", "S2"}

OUTPUT_FIELDS = (
    "name",
    "branch",
    "type",
    "latitude",
    "longitude",
    "sido",
    "sigungu",
    "address",
)


def clean(value: str | None) -> str:
    return (value or "").strip()


def preprocess(source: Path, destination: Path) -> int:
    destination.parent.mkdir(parents=True, exist_ok=True)
    count = 0

    with source.open("r", encoding="utf-8-sig", newline="") as source_file, destination.open(
        "w", encoding="utf-8", newline=""
    ) as destination_file:
        reader = csv.DictReader(source_file)
        writer = csv.DictWriter(destination_file, fieldnames=OUTPUT_FIELDS)
        writer.writeheader()

        for line_number, row in enumerate(reader, start=2):
            category_code = clean(row.get("상권업종대분류코드"))
            if category_code in EXCLUDED_CATEGORY_CODES:
                continue
            if category_code not in CATEGORY_CODES:
                raise ValueError(f"지원하지 않는 대분류 코드({category_code!r}) at line {line_number}")

            required = {
                "상호명": clean(row.get("상호명")),
                "경도": clean(row.get("경도")),
                "위도": clean(row.get("위도")),
                "도로명주소": clean(row.get("도로명주소")),
            }
            missing = [name for name, value in required.items() if not value]
            if missing:
                raise ValueError(f"필수값 누락({', '.join(missing)}) at line {line_number}")

            writer.writerow(
                {
                    "name": required["상호명"],
                    "branch": clean(row.get("지점명")),
                    "type": CATEGORY_CODES[category_code],
                    "latitude": required["위도"],
                    "longitude": required["경도"],
                    "sido": clean(row.get("시도명")),
                    "sigungu": clean(row.get("시군구명")),
                    "address": required["도로명주소"],
                }
            )
            count += 1

    return count


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    count = preprocess(args.source, args.destination)
    print(f"processed {count:,} stores -> {args.destination}")


if __name__ == "__main__":
    main()
