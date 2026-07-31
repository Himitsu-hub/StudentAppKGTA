import requests
from bs4 import BeautifulSoup
import json
import time
import os
import re

BASE_URL = "https://dksta.ru"

KAFEDRY_URLS = [
    "/kafedra-mashinostroyeniya",
    "/kafedra-bezopasnosti-zhiznedeyatelnosti-ekologii-i-khimii",
    "/kafedra-gidropnevmoavtomatiki-i-gidroprivoda",
    "/kafedra-robototekhniki-i-kompleksnoy-avtomatizatsii",
    "/kafedra-iis-i-ksn",
    "/kafedra-tekhnologii-mashinostroyeniya",
    "/kafedra-prikladnoy-matematiki-i-sistem-avtomatizirovannogo-proyektirovaniya",
    "/kafedra-priborostroyeniya",
    "/kafedra-elektrotekhniki",
    "/kafedra-loes",
    "/kafedra-ekonomiki-i-gumanitarnykh-nauk",
    "/kafedra-menedzhmenta",
    "/kafedra-inostrannykh-yazykov",
]

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
}


def fetch_page(url: str) -> BeautifulSoup:
    full_url = BASE_URL + url if url.startswith("/") else url
    resp = requests.get(full_url, headers=HEADERS, timeout=15)
    resp.raise_for_status()
    return BeautifulSoup(resp.text, "html.parser")


def find_teacher_list_url(department_url: str):
    soup = fetch_page(department_url)
    for a in soup.find_all("a", href=True):
        text = a.get_text(strip=True).lower()
        if "педагогический состав" in text or "педагогический" in text and "состав" in text:
            href = a["href"]
            if href.startswith("/") and not href.startswith("/sveden"):
                return href
    for a in soup.find_all("a", href=True):
        href = a["href"]
        text = a.get_text(strip=True).lower()
        if href.endswith("-pps") and ("педагогический" in text or "состав" in text):
            return href
    return None


def extract_teachers_from_list(url: str) -> list:
    soup = fetch_page(url)
    teachers = []
    seen = set()

    for td in soup.find_all("td", valign="top"):
        link = td.find("a", href=True)
        if not link:
            continue
        href = link.get("href", "")
        if not href.startswith("/") or href.startswith("/sveden") or href.startswith("/kafedr"):
            continue

        name_text = link.get_text(strip=True)
        if not name_text or len(name_text) < 5:
            continue

        name_text = re.sub(r'([а-яА-Я])([А-Я])', r'\1 \2', name_text)

        name_parts = name_text.split()
        if len(name_parts) < 2:
            continue

        if href in seen:
            continue
        seen.add(href)

        img = td.find("img")
        photo_url = ""
        if img:
            src = img.get("src", "") or img.get("onmouseout", "")
            if "this.src=" in src:
                src = src.split("this.src=")[-1].strip("'\"")
            if src.startswith("/"):
                src = BASE_URL + src
            photo_url = src

        teachers.append({
            "name": " ".join(name_parts),
            "profile_url": href,
            "photo_url": photo_url,
            "position": "",
            "email": "",
        })

    return teachers


def extract_teacher_details(profile_url: str) -> dict:
    try:
        soup = fetch_page(profile_url)
    except Exception:
        return {}

    details = {}

    table = soup.find("table")
    if table:
        for row in table.find_all("tr"):
            cells = row.find_all("td")
            if len(cells) >= 2:
                label = cells[0].get_text(strip=True).lower()
                value = cells[1].get_text(strip=True)

                if "должност" in label:
                    details["position"] = value
                elif "дисциплин" in label and "преподает" in label:
                    items = cells[1].find_all("li")
                    if items:
                        details["subjects"] = [li.get_text(strip=True).rstrip(",") for li in items]
                    else:
                        details["subjects"] = [v.strip().rstrip(",") for v in value.split("\n") if v.strip()]
                elif "контакт" in label:
                    text = cells[1].get_text()
                    match = re.search(r'E-mail преподавателя[:\s]*([\w.+-]+@[\w.-]+\.\w+)', text)
                    if match:
                        details["email"] = match.group(1)
                    else:
                        match = re.search(r'([\w.+-]+@dksta\.ru)', text)
                        if match and match.group(1) != "pmsapr@dksta.ru":
                            details["email"] = match.group(1)

    return details


def scrape_all():
    all_teachers = []

    for dept_url in KAFEDRY_URLS:
        print(f"\n=== Кафедра: {dept_url} ===")
        teacher_list_url = find_teacher_list_url(dept_url)
        if not teacher_list_url:
            print(f"  Не найдена ссылка на ППС, пропускаю")
            continue
        print(f"  ППС: {teacher_list_url}")
        teachers = extract_teachers_from_list(teacher_list_url)
        print(f"  Найдено: {len(teachers)} преподавателей")
        all_teachers.extend(teachers)
        time.sleep(0.5)

    print(f"\nВсего найдено: {len(all_teachers)} преподавателей")
    print("Загружаю детали...")

    for i, teacher in enumerate(all_teachers):
        print(f"  [{i+1}/{len(all_teachers)}] {teacher['name']}")
        details = extract_teacher_details(teacher["profile_url"])
        teacher.update(details)
        time.sleep(0.5)

    output_path = os.path.join(os.path.dirname(__file__), "teachers.json")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(all_teachers, f, ensure_ascii=False, indent=2)

    print(f"\nСохранено в {output_path}")
    return all_teachers


if __name__ == "__main__":
    scrape_all()
