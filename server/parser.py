import re
from datetime import datetime, timedelta
from typing import Optional, List, Dict, Tuple, Any
from dataclasses import dataclass, field

try:
    from openpyxl import load_workbook
    HAS_OPENPYXL = True
except ImportError:
    HAS_OPENPYXL = False

try:
    import xlrd
    HAS_XLRD = True
except ImportError:
    HAS_XLRD = False


@dataclass
class SubgroupInfo:
    name: str
    column: int


@dataclass
class GroupInfo:
    group_name: str
    subgroups: List[SubgroupInfo] = field(default_factory=list)


@dataclass
class Lesson:
    time: str
    subject: str
    teacher: str
    room: str
    type: str


@dataclass
class ScheduleDay:
    day_name: str
    lessons: List[Lesson] = field(default_factory=list)


ALLOWED_PREFIXES = ["И", "У", "П", "ЭТ", "ЛТ"]

HOLIDAYS = {
    "01.01": "Новый год",
    "02.01": "Новогодние каникулы",
    "03.01": "Новогодние каникулы",
    "04.01": "Новогодние каникулы",
    "05.01": "Новогодние каникулы",
    "06.01": "Новогодние каникулы",
    "07.01": "Рождество Христово",
    "08.01": "Новогодние каникулы",
    "23.02": "День защитника Отечества",
    "08.03": "Международный женский день",
    "01.05": "Праздник Весны и Труда",
    "09.05": "День Победы",
    "12.06": "День России",
    "04.11": "День народного единства",
}

# 0-based row of first pair (числитель) for each weekday
DAY_START_ROWS = {
    "Понедельник": 4,
    "Вторник": 18,
    "Среда": 32,
    "Четверг": 46,
    "Пятница": 60,
    "Суббота": 74,
}

TIME_SLOTS = {
    1: "8:00-09:25",
    2: "09:35-11:00",
    3: "12:00-13:25",
    4: "13:35-15:00",
    5: "15:10-16:35",
    6: "17:45-19:10",
    7: "19:20-20:45",
}

LAST_ROW = 84


def get_semester_starts(now: Optional[datetime] = None) -> Tuple[datetime, datetime]:
    """Return (first_semester_start, second_semester_start) for the current academic year."""
    now = (now or datetime.now()).replace(hour=0, minute=0, second=0, microsecond=0)
    year = now.year
    if now.month >= 9:
        first = datetime(year, 9, 1)
        second = datetime(year + 1, 1, 13)
    else:
        first = datetime(year - 1, 9, 1)
        second = datetime(year, 1, 13)
    return first, second


def get_current_week_type(now: Optional[datetime] = None) -> str:
    now = (now or datetime.now()).replace(hour=0, minute=0, second=0, microsecond=0)
    first, second = get_semester_starts(now)
    semester_start = second if now >= second else first
    diff_days = max(0, (now - semester_start).days)
    weeks_diff = diff_days // 7
    return "Числитель" if weeks_diff % 2 == 0 else "Знаменатель"


def get_today_name() -> str:
    days = ["Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"]
    return days[datetime.now().weekday()]


def get_date_for_day(day_name: str) -> datetime:
    day_map = {
        "Понедельник": 0,
        "Вторник": 1,
        "Среда": 2,
        "Четверг": 3,
        "Пятница": 4,
        "Суббота": 5,
        "Воскресенье": 6,
    }
    today = datetime.now()
    target_weekday = day_map.get(day_name, 0)
    current_weekday = today.weekday()
    diff = target_weekday - current_weekday
    return today + timedelta(days=diff)


def is_holiday(date: datetime) -> bool:
    return date.strftime("%d.%m") in HOLIDAYS


def get_holiday_name(date: datetime) -> str:
    return HOLIDAYS.get(date.strftime("%d.%m"), "Праздничный день")


def extract_group_name(text: Optional[str]) -> Optional[str]:
    if not text or not text.strip():
        return None
    cleaned = text.strip().upper().replace("\n", " ")
    for prefix in ALLOWED_PREFIXES:
        if cleaned.startswith(prefix):
            # "П-122 (3 ПОДГРУППА)" / "ЭТ-122 (3 ПОДГРУППА)"
            name = cleaned.split("(")[0].strip()
            name = re.sub(r"\s+", "", name)
            return name if name else None
    return None


def extract_subgroup(text: Optional[str]) -> Optional[str]:
    if not text or not text.strip():
        return None
    lower = text.lower().strip()
    match = re.search(r"(\d)\s*подгрупп", lower)
    if match:
        return f"{match.group(1)} подгруппа"
    return None


def clean_subject(s: str) -> str:
    # Remove only standalone type markers, never mid-word (Электротехника).
    s = re.sub(
        r"(?i)\b(лекция|лек\.?|практика|практ\.?|прак\.?|лабораторн\w*|лаб\.?|л/р)\b\.?",
        "",
        s,
    )
    return re.sub(r"\s+", " ", s).strip(" .\t")


def parse_lesson_text(text: str, time: str) -> Optional[Lesson]:
    lines = [line.strip() for line in text.replace("\r", "\n").split("\n") if line.strip()]
    if not lines:
        return None

    subject = clean_subject(lines[0])
    if not subject or subject in ("-", "null"):
        return None

    lesson_type = "занятие"
    text_lower = text.lower()
    if "лек" in text_lower:
        lesson_type = "лекция"
    elif "практ" in text_lower:
        lesson_type = "практика"
    elif "лаб" in text_lower or "л/р" in text_lower:
        lesson_type = "лабораторная"

    # Special whole-day / non-class activities
    upper = subject.upper()
    if "ВОЕНН" in upper or "ФИЗИЧЕСКАЯ КУЛЬТУРА" in upper or "ОБЩАЯ ФИЗИЧЕСКАЯ" in upper:
        if "лек" not in text_lower and "практ" not in text_lower and "лаб" not in text_lower:
            lesson_type = "занятие"

    room = ""
    teacher = ""
    for i in range(len(lines) - 1, 0, -1):
        # room often like "319", "704к", "234 (с 1 по 8 нед)"
        room_match = re.search(r"(\d{2,4}\s*[а-яА-Яa-zA-ZкКлЛ]?)", lines[i])
        if room_match and re.search(r"\d", lines[i]):
            # Prefer trailing room token
            tail = re.findall(r"(\d{2,4}[а-яА-Яa-zA-ZкКлЛ]?)", lines[i])
            if tail:
                room = tail[-1]
                teacher = ", ".join(lines[1:i]) if i > 1 else lines[i][: lines[i].rfind(room)].strip(" ,")
                # If teacher is empty, try same line before room
                if not teacher and i == len(lines) - 1:
                    before = lines[i][: lines[i].rfind(room)].strip(" ,")
                    # skip pure week notes
                    if before and "нед" not in before.lower():
                        teacher = before
                break

    # Single-line: "ФИЗИЧЕСКАЯ КУЛЬТУРА и СПОРТ лек. 319"
    if not room and len(lines) == 1:
        m = re.search(r"(\d{2,4}[а-яА-Яa-zA-ZкКлЛ]?)\s*$", lines[0])
        if m:
            room = m.group(1)
            subject = clean_subject(lines[0][: m.start()])

    return Lesson(time=time, subject=subject, teacher=teacher, room=room, type=lesson_type)


class SheetAdapter:
    """
    Unified adapter for openpyxl and xlrd.

    All coordinates are 0-based inclusive.
    Merged ranges are stored as 0-based inclusive min/max.
    """

    def __init__(self, sheet: Any, workbook: Any, is_xls: bool = False):
        self.sheet = sheet
        self.workbook = workbook
        self.is_xls = is_xls
        # list of dicts: min_row, max_row, min_col, max_col (0-based inclusive)
        self.merged_ranges: List[Dict[str, int]] = []

        if is_xls:
            # xlrd: (rlo, rhi, clo, chi) half-open, 0-based
            for rng in sheet.merged_cells:
                rlo, rhi, clo, chi = rng
                if rhi <= rlo or chi <= clo:
                    continue
                self.merged_ranges.append({
                    "min_row": rlo,
                    "max_row": rhi - 1,
                    "min_col": clo,
                    "max_col": chi - 1,
                })
        else:
            # openpyxl: 1-based inclusive
            for rng in sheet.merged_cells.ranges:
                self.merged_ranges.append({
                    "min_row": rng.min_row - 1,
                    "max_row": rng.max_row - 1,
                    "min_col": rng.min_col - 1,
                    "max_col": rng.max_col - 1,
                })

    def get_cell_text(self, row: int, col: int) -> Optional[str]:
        try:
            if self.is_xls:
                if row < 0 or col < 0 or row >= self.sheet.nrows or col >= self.sheet.ncols:
                    return None
                val = self.sheet.cell(row, col).value
            else:
                cell = self.sheet.cell(row=row + 1, column=col + 1)
                val = cell.value

            if val is None:
                return None
            s = str(val).strip()
            return s if s else None
        except (IndexError, TypeError, AttributeError):
            return None

    def find_merge(self, row: int, col: int) -> Optional[Dict[str, int]]:
        for merge in self.merged_ranges:
            if (
                merge["min_row"] <= row <= merge["max_row"]
                and merge["min_col"] <= col <= merge["max_col"]
            ):
                return merge
        return None

    def get_text_with_merged(self, row: int, col: int) -> Optional[str]:
        """Return cell text, resolving merged cells (value lives in top-left)."""
        raw = self.get_cell_text(row, col)
        if raw and raw not in ("-", "null"):
            return raw

        merge = self.find_merge(row, col)
        if merge:
            top_left = self.get_cell_text(merge["min_row"], merge["min_col"])
            if top_left and top_left not in ("-", "null"):
                return top_left
        return None

    @property
    def max_column(self) -> int:
        if self.is_xls:
            return self.sheet.ncols
        return self.sheet.max_column or 0

    @property
    def max_row(self) -> int:
        if self.is_xls:
            return self.sheet.nrows
        return self.sheet.max_row or 0


def open_workbook(file_path: str) -> Tuple[SheetAdapter, Any]:
    """Open Excel file. Files from dksta are often real .xls saved as .xlsx."""
    errors = []

    # 1) True .xls (Composite Document) — need xlrd + formatting_info for merges
    if HAS_XLRD:
        try:
            wb = xlrd.open_workbook(file_path, formatting_info=True)
            sheet = wb.sheet_by_index(0)
            return SheetAdapter(sheet, wb, is_xls=True), wb
        except Exception as e:
            errors.append(f"xlrd+fmt: {e}")
            try:
                wb = xlrd.open_workbook(file_path)
                sheet = wb.sheet_by_index(0)
                return SheetAdapter(sheet, wb, is_xls=True), wb
            except Exception as e2:
                errors.append(f"xlrd: {e2}")

    # 2) Real .xlsx
    if HAS_OPENPYXL:
        try:
            wb = load_workbook(file_path, data_only=True)
            sheet = wb.active
            return SheetAdapter(sheet, wb, is_xls=False), wb
        except Exception as e:
            errors.append(f"openpyxl: {e}")

    raise ValueError(
        f"Cannot open {file_path}. Install openpyxl/xlrd. Details: {'; '.join(errors)}"
    )


def close_workbook(adapter: SheetAdapter, wb: Any) -> None:
    if not adapter.is_xls:
        try:
            wb.close()
        except Exception:
            pass


def _group_header_span(sheet: SheetAdapter, col: int) -> Tuple[int, int]:
    """Return (start_col, end_col) for group name cell / merge on row 2."""
    start_col = col
    end_col = col
    merge = sheet.find_merge(2, col)
    if merge and merge["min_row"] <= 2 <= merge["max_row"]:
        start_col = merge["min_col"]
        end_col = merge["max_col"]
    return start_col, end_col


def parse_groups_from_header(sheet: SheetAdapter) -> Dict[str, GroupInfo]:
    """
    Header:
      row 2 — group names (may be merged across subgroup columns)
      row 3 — optional "N подгруппа"
    Same group can appear in several blocks (e.g. main + 3-я подгруппа).
    """
    groups: Dict[str, GroupInfo] = {}
    last_col = sheet.max_column

    # Collect ordered group anchors: (start_col, end_col, group_name)
    anchors: List[Tuple[int, int, str]] = []
    seen_cols = set()

    for col in range(last_col):
        if col in seen_cols:
            continue

        text = sheet.get_cell_text(2, col)
        group_name = extract_group_name(text)
        if group_name is None:
            # value may sit only in top-left of a merge
            merged_text = sheet.get_text_with_merged(2, col)
            group_name = extract_group_name(merged_text)

        if group_name is None:
            continue

        start_col, end_col = _group_header_span(sheet, col)
        for c in range(start_col, end_col + 1):
            seen_cols.add(c)
        anchors.append((start_col, end_col, group_name))

    # Sort by column
    anchors.sort(key=lambda a: a[0])

    for idx, (start_col, end_col, group_name) in enumerate(anchors):
        # Block ends at next anchor (any group) so "3 подгруппа" of same name is separate
        if idx + 1 < len(anchors):
            block_end = anchors[idx + 1][0] - 1
        else:
            # stop before trailing time/day columns
            block_end = last_col - 1
            for c in range(end_col + 1, last_col):
                t = sheet.get_cell_text(2, c) or ""
                if t.strip().lower() in ("время", "№ пары", "день недели", "день\nнедели"):
                    block_end = c - 1
                    break

        block_end = max(block_end, end_col)

        if group_name not in groups:
            groups[group_name] = GroupInfo(group_name=group_name)

        found: List[Tuple[str, int]] = []
        for c in range(start_col, block_end + 1):
            sub_text = sheet.get_cell_text(3, c)
            # subgroup label can also be inside group name cell
            if not sub_text:
                sub_text = sheet.get_cell_text(2, c)
            subgroup = extract_subgroup(sub_text)
            if subgroup and not any(s[0] == subgroup for s in found):
                found.append((subgroup, c))

        if found:
            for name, c in found:
                if not any(s.name == name for s in groups[group_name].subgroups):
                    groups[group_name].subgroups.append(SubgroupInfo(name=name, column=c))
        else:
            # No explicit labels → whole block is one column for the group (1-я)
            fallback = "1 подгруппа"
            if not any(s.column == start_col for s in groups[group_name].subgroups):
                if not any(s.name == fallback for s in groups[group_name].subgroups):
                    groups[group_name].subgroups.append(
                        SubgroupInfo(name=fallback, column=start_col)
                    )
                else:
                    # second block without label — treat as next free subgroup number
                    used = {s.name for s in groups[group_name].subgroups}
                    for n in range(1, 6):
                        candidate = f"{n} подгруппа"
                        if candidate not in used:
                            groups[group_name].subgroups.append(
                                SubgroupInfo(name=candidate, column=start_col)
                            )
                            break

    # Sort subgroups by name for stable API
    for info in groups.values():
        info.subgroups.sort(key=lambda s: s.name)

    return groups


def _pair_index_for_row(start_row: int, row: int) -> Optional[int]:
    """Return 0-based pair index if row belongs to this day block."""
    if row < start_row:
        return None
    offset = row - start_row
    if offset < 0 or offset >= 14:
        return None
    return offset // 2


def _time_range_for_pairs(first_pair: int, last_pair: int) -> str:
    """first_pair/last_pair are 0-based pair indices within the day."""
    start = TIME_SLOTS.get(first_pair + 1, "Неизвестно")
    end = TIME_SLOTS.get(last_pair + 1, "Неизвестно")
    if first_pair == last_pair:
        return start
    start_t = start.split("-")[0].strip()
    end_t = end.split("-")[-1].strip()
    return f"{start_t}-{end_t}"


def parse_lessons_for_day(
    sheet: SheetAdapter,
    start_row: int,
    column: int,
    week_type: str,
) -> List[Lesson]:
    """
    For each pair slot there are two rows:
      num_row = start_row + i*2      (числитель)
      den_row = num_row + 1          (знаменатель)

    Cases handled via merges:
    - num+den merged → same lesson both weeks
    - only num or only den filled → that week only
    - horizontal merge across subgroups / whole stream → all those columns share text
    - vertical merge across many pairs (e.g. военная подготовка) → one lesson for the range
    """
    lessons: List[Lesson] = []
    # skip pair indices already covered by a multi-pair merge
    covered_pairs: set = set()

    for i in range(7):
        if i in covered_pairs:
            continue

        num_row = start_row + i * 2
        den_row = num_row + 1
        if num_row > LAST_ROW:
            break

        selected_row = num_row if week_type == "Числитель" else den_row
        if selected_row > LAST_ROW:
            break

        text = sheet.get_text_with_merged(selected_row, column)
        if not text or not text.strip() or text.strip() in ("-", "null"):
            # Fallback: if this week cell is empty but num+den are vertically
            # merged, get_text_with_merged already handled it. If only the other
            # week has a lesson, correctly return nothing.
            continue

        merge = sheet.find_merge(selected_row, column)
        first_pair = i
        last_pair = i

        if merge:
            # Pairs of this day touched by the merge
            for j in range(7):
                pair_num = start_row + j * 2
                pair_den = pair_num + 1
                # overlap of [pair_num, pair_den] with [min_row, max_row]
                if pair_den >= merge["min_row"] and pair_num <= merge["max_row"]:
                    first_pair = min(first_pair, j)
                    last_pair = max(last_pair, j)

            # Only collapse when merge covers more than one pair slot
            if last_pair > first_pair:
                for j in range(first_pair, last_pair + 1):
                    covered_pairs.add(j)
                # emit once at the first pair of the block
                if i != first_pair:
                    # re-process from first_pair next... but we're past it; emit now
                    pass

        time = _time_range_for_pairs(first_pair, last_pair)
        lesson = parse_lesson_text(text, time)
        if lesson:
            lessons.append(lesson)

        if last_pair > first_pair:
            for j in range(first_pair, last_pair + 1):
                covered_pairs.add(j)

    return lessons


def schedule_to_dict(schedule: List[ScheduleDay]) -> list:
    return [
        {
            "dayName": day.day_name,
            "lessons": [
                {
                    "time": l.time,
                    "subject": l.subject,
                    "teacher": l.teacher,
                    "room": l.room,
                    "type": l.type,
                }
                for l in day.lessons
            ],
        }
        for day in schedule
    ]


def parse_schedule_for_group(
    file_path: str,
    group_name: str,
    subgroup: Optional[str],
    week_type: Optional[str] = None,
) -> List[ScheduleDay]:
    sheet, wb = open_workbook(file_path)
    try:
        groups = parse_groups_from_header(sheet)
        # normalize group name lookup
        lookup = group_name.strip().upper().replace(" ", "")
        info = groups.get(lookup) or groups.get(group_name)
        if info is None:
            # fuzzy: strip spaces
            for name, g in groups.items():
                if name.replace(" ", "") == lookup:
                    info = g
                    break
        if info is None:
            return []

        subgroup_column = None
        if subgroup:
            for s in info.subgroups:
                if s.name == subgroup or s.name.replace(" ", "") == subgroup.replace(" ", ""):
                    subgroup_column = s.column
                    break
        if subgroup_column is None and info.subgroups:
            subgroup_column = info.subgroups[0].column

        if subgroup_column is None:
            return []

        active_week = week_type or get_current_week_type()
        result: List[ScheduleDay] = []

        for day_name, start_row in DAY_START_ROWS.items():
            if start_row > LAST_ROW:
                break
            date = get_date_for_day(day_name)
            if is_holiday(date):
                h = get_holiday_name(date)
                result.append(
                    ScheduleDay(
                        day_name=day_name,
                        lessons=[
                            Lesson(time="", subject=h, teacher="", room="", type="праздник")
                        ],
                    )
                )
                continue
            lessons = parse_lessons_for_day(sheet, start_row, subgroup_column, active_week)
            result.append(ScheduleDay(day_name=day_name, lessons=lessons))

        return result
    finally:
        close_workbook(sheet, wb)


def get_groups_from_file(file_path: str) -> Dict[str, List[str]]:
    sheet, wb = open_workbook(file_path)
    try:
        groups = parse_groups_from_header(sheet)
        return {name: [s.name for s in info.subgroups] for name, info in groups.items()}
    finally:
        close_workbook(sheet, wb)
