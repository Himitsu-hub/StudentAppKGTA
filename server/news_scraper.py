"""Scrape news from dksta.ru and keep a local JSON cache as fallback."""

from __future__ import annotations

import json
import os
import re
from datetime import datetime
from typing import Any
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

BASE_URL = "https://dksta.ru"
NEWS_URL = f"{BASE_URL}/"
CACHE_PATH = os.path.join(os.path.dirname(__file__), "news_cache.json")

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "ru-RU,ru;q=0.9,en;q=0.8",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
}


def _normalize_url(href: str) -> str:
    if not href:
        return ""
    if href.startswith("http"):
        return href.split("?")[0].rstrip("/")
    return urljoin(BASE_URL + "/", href).split("?")[0].rstrip("/")


def _parse_date(raw: str) -> datetime | None:
    raw = (raw or "").strip()
    for fmt in ("%d.%m.%Y", "%d.%m.%y", "%Y-%m-%d"):
        try:
            return datetime.strptime(raw, fmt)
        except ValueError:
            continue
    return None


def _date_sort_key(item: dict[str, Any]) -> float:
    parsed = _parse_date(item.get("date", ""))
    if parsed:
        return parsed.timestamp()
    return 0.0


def _clean_title(title: str, date: str) -> str:
    title = (title or "").strip()
    if date and date in title:
        title = title.replace(date, "").strip(" ·|-–—")
    # Drop trailing date-like noise
    title = re.sub(r"\s+\d{2}\.\d{2}\.\d{4}\s*$", "", title).strip()
    return title


def _parse_news_wrap(soup: BeautifulSoup) -> list[dict[str, Any]]:
    news_wrap = soup.find("div", class_="news-wrap")
    if not news_wrap:
        return []

    scraped: list[dict[str, Any]] = []
    seen_urls: set[str] = set()

    for div in news_wrap.find_all("div", recursive=False):
        classes = div.get("class") or []
        if "news-pagination" in classes or "news-loading" in classes:
            continue

        link_tag = div.find("a", href=True)
        if not link_tag:
            continue

        href = link_tag.get("href", "")
        if "news_post" not in href and "/novosti/" not in href:
            continue

        url = _normalize_url(href)
        if not url or url in seen_urls:
            continue

        title = link_tag.get_text(" ", strip=True)
        if not title or title.isdigit():
            continue

        img_tag = div.find("img")
        image_url = ""
        if img_tag:
            src = img_tag.get("src") or img_tag.get("data-src") or ""
            image_url = _normalize_url(src) if src else ""

        text = div.get_text(" ", strip=True)
        date_match = re.search(r"(\d{2}\.\d{2}\.\d{4})", text)
        date = date_match.group(1) if date_match else ""
        title = _clean_title(title, date)

        desc = ""
        p_tag = div.find("p")
        if p_tag:
            desc = p_tag.get_text(" ", strip=True)
        elif date:
            after = text.split(date, 1)
            if len(after) > 1 and len(after[1].strip()) > 10:
                desc = after[1].strip()[:220]

        seen_urls.add(url)
        scraped.append(
            {
                "title": title,
                "url": url,
                "image_url": image_url,
                "date": date,
                "description": desc,
            }
        )
    return scraped


def _fetch_soup(url: str) -> BeautifulSoup | None:
    try:
        resp = requests.get(url, headers=HEADERS, timeout=25)
        resp.raise_for_status()
        return BeautifulSoup(resp.text, "html.parser")
    except Exception as exc:
        print(f"[news] fetch failed {url}: {exc}")
        return None


def _merge_news(scraped: list[dict[str, Any]], cached: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Merge by URL: scraped wins; keep older cache items not in scrape."""
    by_url: dict[str, dict[str, Any]] = {}
    for item in cached:
        u = item.get("url") or ""
        if u:
            by_url[u] = item
    for item in scraped:
        u = item.get("url") or ""
        if u:
            by_url[u] = item
    return _sorted(list(by_url.values()))


def scrape_news(limit: int = 15) -> list[dict[str, Any]]:
    """
    Return freshest news.
    Scrapes homepage + a few listing pages, merges into cache so VPS
    partial scrapes never wipe newer posts permanently.
    """
    cached = load_cached_news()
    scraped: list[dict[str, Any]] = []
    seen: set[str] = set()

    # Homepage first, then paginated lists (site uses novosti/p/N)
    page_urls = [NEWS_URL] + [
        urljoin(BASE_URL + "/", f"novosti/p/{i}") for i in range(1, 4)
    ]

    for page_url in page_urls:
        soup = _fetch_soup(page_url)
        if not soup:
            continue
        for item in _parse_news_wrap(soup):
            u = item.get("url") or ""
            if u and u not in seen:
                seen.add(u)
                scraped.append(item)
        if len(scraped) >= max(limit, 12):
            break

    if not scraped:
        print("[news] scrape empty, using cache")
        return _sorted(cached)[:limit]

    merged = _merge_news(scraped, cached)
    # Keep a reasonable history for clients
    merged = merged[:80]
    save_news_cache(merged)
    print(f"[news] scraped={len(scraped)} merged={len(merged)} -> return {min(limit, len(merged))}")
    return merged[:limit]


def _sorted(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return sorted(items, key=_date_sort_key, reverse=True)


def save_news_cache(news: list[dict[str, Any]]) -> None:
    with open(CACHE_PATH, "w", encoding="utf-8") as f:
        json.dump(news, f, ensure_ascii=False, indent=2)


def load_cached_news() -> list[dict[str, Any]]:
    if not os.path.exists(CACHE_PATH):
        return []
    try:
        with open(CACHE_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
        return data if isinstance(data, list) else []
    except Exception:
        return []


if __name__ == "__main__":
    for i, item in enumerate(scrape_news(10), 1):
        print(f"{i}. {item['title']} ({item.get('date', '')})")
        print(f"   {item['url']}")
