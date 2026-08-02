"""
Pre-publish checks for schedule Excel files.

Empty cells are usually CORRECT (this subgroup/week simply has no pair).
A single filled cell among empty neighbours cannot be reliably told apart
from a broken merge — both look the same in the file. So we do NOT warn on
«only this cell has practice» patterns (e.g. Z15 foundations for one sub).

We only flag higher-confidence issues:
1) Stream / military lecture text in ONE column while several neighboring
   group columns are empty (broken multi-group lecture merge).
2) Small gap (1–2 cols) between two WIDE merges with the same text.
3) Unreadable file / no groups; subgroup with zero lessons both weeks.
"""
from __future__ import annotations

import re
from typing import Any, Dict, List, Optional, Set, Tuple

from parser import (
    DAY_START_ROWS,
    LAST_ROW,
    close_workbook,
    open_workbook,
    parse_groups_from_header,
    parse_schedule_for_group,
)


def _excel_cell(row0: int, col0: int) -> str:
    c = col0
    letters = ""
    while True:
        letters = chr(ord("A") + (c % 26)) + letters
        c = c // 26 - 1
        if c < 0:
            break
    return f"{letters}{row0 + 1}"


def _norm_lesson(text: str) -> str:
    s = text.replace("\n", " ").strip().lower()
    s = re.sub(r"\s+", " ", s)
    return s[:100]


def _is_stream_lesson(text: str) -> bool:
    """Lessons that are usually one wide merge across many groups."""
    t = text.lower().replace("\n", " ")
    if "военн" in t:
        return True
    # «лек.» / «лекция» but not mid-word (Электротехника)
    if re.search(r"(^|[\s,;])лек(\.|ция|\s|$)", t):
        return True
    if re.search(r"\bлек\.", t):
        return True
    # ALL-CAPS stream titles often used for course-wide lectures
    letters = re.sub(r"[^a-zA-Zа-яА-ЯёЁ]", "", text)
    if len(letters) >= 8 and letters.isupper():
        return True
    return False


def _is_language(text: str) -> bool:
    t = text.lower()
    keys = ("немец", "франц", "англий", "иностр", "язык", "китай")
    return any(k in t for k in keys)


def validate_schedule_file(file_path: str) -> Dict[str, Any]:
    errors: List[str] = []
    warnings: List[str] = []
    stats: Dict[str, Any] = {
        "groups": 0,
        "subgroups": 0,
        "lessons_numerator": 0,
        "lessons_denominator": 0,
        "group_stats": [],
        "breakdown": [],
    }

    try:
        sheet, wb = open_workbook(file_path)
    except Exception as exc:
        return {
            "ok": False,
            "errors": [f"Не удалось открыть файл: {exc}"],
            "warnings": [],
            "stats": stats,
        }

    try:
        groups = parse_groups_from_header(sheet)
        if not groups:
            errors.append(
                "В шапке не найдены группы (ожидаются И-…, У-…, П-…, ЭТ-… "
                "на строке с названиями групп)."
            )
            return {"ok": False, "errors": errors, "warnings": warnings, "stats": stats}

        stats["groups"] = len(groups)
        stats["subgroups"] = sum(len(g.subgroups) for g in groups.values())
        stats["breakdown"] = [
            {
                "group": gname,
                "subgroups": [s.name for s in info.subgroups],
                "count": len(info.subgroups),
            }
            for gname, info in sorted(groups.items(), key=lambda x: x[0])
        ]

        col_to_sub: Dict[int, Tuple[str, str]] = {}
        for gname, info in groups.items():
            for sub in info.subgroups:
                col_to_sub[sub.column] = (gname, sub.name)
        cols_sorted = sorted(col_to_sub.keys())
        group_cols: Set[int] = set(cols_sorted)

        seen_msgs: Set[str] = set()

        def add_warn(msg: str) -> None:
            if msg not in seen_msgs:
                seen_msgs.add(msg)
                warnings.append(msg)

        # NOTE: We intentionally do NOT warn on «only one of 4 cells filled»
        # for practices (Z15 foundations etc.). That pattern is normal when
        # a pair exists only for one subgroup / one week. A broken merge after
        # unmerge looks identical — cannot be detected reliably without AI
        # or the original merged file.

        # --- 1) Stream lesson only in one column, neighbors empty (broken merge) ---
        for row in range(4, min(LAST_ROW + 1, sheet.max_row)):
            texts: List[Tuple[int, str]] = []
            for c in cols_sorted:
                raw = sheet.get_text_with_merged(row, c)
                if raw and raw.strip() and raw.strip() not in ("-", "null"):
                    texts.append((c, raw.strip()))
                else:
                    texts.append((c, ""))

            for i, (c, text) in enumerate(texts):
                if not text:
                    continue
                if _is_language(text):
                    continue
                if not _is_stream_lesson(text):
                    continue

                # Already a wide merge covering 3+ group columns → OK
                merge = sheet.find_merge(row, c)
                if merge:
                    covered = sum(
                        1
                        for gc in cols_sorted
                        if merge["min_col"] <= gc <= merge["max_col"]
                    )
                    if covered >= 3:
                        continue

                empty_right = 0
                for j in range(i + 1, len(texts)):
                    if texts[j][1]:
                        break
                    empty_right += 1
                empty_left = 0
                for j in range(i - 1, -1, -1):
                    if texts[j][1]:
                        break
                    empty_left += 1

                # Need several free neighbor tracks (was a multi-group cell)
                if empty_right < 2 and empty_left < 2:
                    continue

                gname, subname = col_to_sub[c]
                snippet = text.replace("\n", " ").strip()
                if len(snippet) > 50:
                    snippet = snippet[:50] + "…"
                add_warn(
                    f"{_excel_cell(row, c)} · {gname} ({subname}): "
                    f"лекция/поток «{snippet}» только в одной колонке, "
                    f"рядом {max(empty_left, empty_right)} пустых столбцов других групп. "
                    f"Если это занятие на несколько групп — объедините ячейки; "
                    f"если лекция только у этой группы — можно игнорировать."
                )

        # --- 2) Gap between two WIDE same-text merges ---
        data_merges = [
            m
            for m in sheet.merged_ranges
            if m["min_row"] >= 4 and m["min_row"] <= LAST_ROW
        ]
        seen_holes: Set[Tuple[int, int]] = set()

        for i, m1 in enumerate(data_merges):
            t1 = sheet.get_cell_text(m1["min_row"], m1["min_col"])
            if not t1 or not t1.strip() or t1.strip() in ("-", "null"):
                continue
            if not _is_stream_lesson(t1):
                continue
            n1 = _norm_lesson(t1)
            if len(n1) < 10:
                continue

            for m2 in data_merges[i + 1 :]:
                if m1["max_col"] < m2["min_col"]:
                    left, right = m1, m2
                elif m2["max_col"] < m1["min_col"]:
                    left, right = m2, m1
                else:
                    continue

                w_left = left["max_col"] - left["min_col"] + 1
                w_right = right["max_col"] - right["min_col"] + 1
                if w_left < 3 or w_right < 3:
                    continue
                if right["max_col"] - left["min_col"] + 1 < 6:
                    continue

                gap_lo = left["max_col"] + 1
                gap_hi = right["min_col"] - 1
                if gap_hi - gap_lo + 1 < 1 or gap_hi - gap_lo + 1 > 2:
                    continue

                r_lo = max(left["min_row"], right["min_row"])
                r_hi = min(left["max_row"], right["max_row"])
                if r_lo > r_hi:
                    continue

                t2 = sheet.get_cell_text(right["min_row"], right["min_col"])
                if not t2 or _norm_lesson(t2) != n1:
                    continue

                snippet = t1.replace("\n", " ").strip()
                if len(snippet) > 50:
                    snippet = snippet[:50] + "…"

                for r in range(r_lo, r_hi + 1):
                    for c in range(gap_lo, gap_hi + 1):
                        if c not in group_cols or (r, c) in seen_holes:
                            continue
                        cell = sheet.get_text_with_merged(r, c)
                        if cell and cell.strip() and cell.strip() not in ("-", "null"):
                            continue
                        gname, subname = col_to_sub[c]
                        seen_holes.add((r, c))
                        add_warn(
                            f"{_excel_cell(r, c)} · {gname} ({subname}): "
                            f"разрыв в широком объединении «{snippet}»."
                        )

        # --- 3) parse stats ---
        for gname, info in groups.items():
            for sub in info.subgroups:
                num = parse_schedule_for_group(file_path, gname, sub.name, "Числитель")
                den = parse_schedule_for_group(file_path, gname, sub.name, "Знаменатель")
                n_lessons = sum(len(d.lessons) for d in num)
                d_lessons = sum(len(d.lessons) for d in den)
                stats["lessons_numerator"] += n_lessons
                stats["lessons_denominator"] += d_lessons
                stats["group_stats"].append(
                    {
                        "group": gname,
                        "subgroup": sub.name,
                        "lessons_numerator": n_lessons,
                        "lessons_denominator": d_lessons,
                    }
                )
                if n_lessons == 0 and d_lessons == 0:
                    add_warn(
                        f"{gname} / {sub.name}: 0 пар и в числителе, и в знаменателе. "
                        f"Если группа должна учиться — проверьте столбец в шапке."
                    )

        # Debug snapshot for Excel N49 block (1st course often has math here).
        # Helps see: still merged (OK) vs only N49 filled (should warn).
        try:
            n49 = sheet.get_cell_text(48, 13)
            p49 = sheet.get_cell_text(48, 15)
            n50 = sheet.get_cell_text(49, 13)
            p50 = sheet.get_cell_text(49, 15)
            m49 = sheet.find_merge(48, 13)
            stats["debug_N49"] = {
                "N49_raw": (n49 or "")[:80],
                "P49_raw": (p49 or "")[:80],
                "N50_raw": (n50 or "")[:80],
                "P50_raw": (p50 or "")[:80],
                "has_merge": m49 is not None,
                "merge": m49,
                "note": (
                    "Если has_merge=true — Excel всё ещё объединяет ячейки, "
                    "это не ошибка (текст только в левом верхнем углу). "
                    "Если has_merge=false и только N49_raw заполнен — должна быть ⚠."
                ),
            }
        except Exception as exc:
            stats["debug_N49"] = {"error": str(exc)}

        warnings = warnings[:40]
        return {
            "ok": len(errors) == 0,
            "errors": errors,
            "warnings": warnings,
            "stats": stats,
            "validator_version": "stream-only-v4",
        }
    except Exception as exc:
        errors.append(f"Ошибка проверки: {exc}")
        return {"ok": False, "errors": errors, "warnings": warnings, "stats": stats}
    finally:
        try:
            close_workbook(sheet, wb)
        except Exception:
            pass
