#!/usr/bin/env python3
"""Changelog utility for release automation.

- alpha/beta: extract the current Unreleased content for release notes.
- release: move Unreleased content into a versioned section and emit the notes.
"""
from __future__ import annotations

import argparse
import datetime as _dt
import pathlib
import re
import sys
from typing import List, Tuple

SECTION_HEADER_RE = re.compile(
    r"^## \[(?P<name>[^\]]+)\](?: - [^\n]+)?\s*$",
    re.MULTILINE,
)

UNRELEASED_BODY_TEMPLATE = "\n\n### Added\n\n### Changed\n\n### Fixed\n"


def parse_sections(text: str) -> Tuple[str, List[Tuple[str, str, str]]]:
    """Return the prefix (text before first section) and a list of sections."""
    matches = list(SECTION_HEADER_RE.finditer(text))
    if not matches:
        raise ValueError("CHANGELOG.md にセクション見出し (## [...]) が見つかりません")

    prefix = text[: matches[0].start()]
    sections = []
    for idx, match in enumerate(matches):
        header_line = match.group(0).rstrip("\n")
        name = match.group("name")
        start = match.end()
        end = matches[idx + 1].start() if idx + 1 < len(matches) else len(text)
        body = text[start:end]
        sections.append((name, header_line, body))
    return prefix, sections


def build_changelog(prefix: str, sections: List[Tuple[str, str, str]]) -> str:
    """Reconstruct changelog text from prefix and sections."""
    parts: List[str] = []
    if prefix:
        parts.append(prefix.rstrip("\n"))
    for _, header, body in sections:
        if parts:
            parts.append("")  # blank line between sections
        section_text = header + body.rstrip("\n")
        parts.append(section_text)
    return "\n".join(parts).rstrip("\n") + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["alpha", "beta", "release"], help="リリース種別")
    parser.add_argument("--file", default="CHANGELOG.md", help="Changelog ファイルのパス")
    parser.add_argument("--version", help="正式リリース時のバージョン (例: 1.2.3)")
    parser.add_argument(
        "--notes-output",
        help="リリースノートを書き出すファイルパス。未指定なら標準出力に出力",
    )
    parser.add_argument(
        "--update-file",
        action="store_true",
        help="リリース時に CHANGELOG.md を更新して保存する",
    )
    parser.add_argument(
        "--date",
        help="リリース日 (YYYY-MM-DD)。未指定なら今日の日付",
    )
    args = parser.parse_args()

    changelog_path = pathlib.Path(args.file)
    if not changelog_path.exists():
        raise SystemExit(f"Changelog ファイルが見つかりません: {changelog_path}")

    text = changelog_path.read_text(encoding="utf-8")
    prefix, sections = parse_sections(text)

    try:
        unreleased_index = next(i for i, section in enumerate(sections) if section[0].lower() == "unreleased")
    except StopIteration as exc:
        raise SystemExit("CHANGELOG.md に [Unreleased] セクションが見つかりません") from exc

    _, _, unreleased_body = sections[unreleased_index]
    notes = unreleased_body.strip()

    if args.mode == "release":
        if not args.version:
            raise SystemExit("--version は release モードで必須です")
        release_content = unreleased_body.strip("\n")
        if not release_content.strip():
            release_content = "変更点はありません。"
        date_str = args.date or _dt.date.today().isoformat()
        new_section_body = "\n\n" + release_content.strip("\n") + "\n"
        new_section = (
            args.version,
            f"## [{args.version}] - {date_str}",
            new_section_body,
        )
        sections[unreleased_index] = (
            "Unreleased",
            "## [Unreleased]",
            UNRELEASED_BODY_TEMPLATE,
        )
        sections.insert(unreleased_index + 1, new_section)
        updated_text = build_changelog(prefix, sections)
        if args.update_file:
            changelog_path.write_text(updated_text, encoding="utf-8")
        notes = release_content.strip()
    else:
        if not notes:
            notes = "変更点はありません。"

    if args.notes_output:
        pathlib.Path(args.notes_output).write_text(notes + "\n", encoding="utf-8")
    else:
        sys.stdout.write(notes)
        if not notes.endswith("\n"):
            sys.stdout.write("\n")


if __name__ == "__main__":
    main()
