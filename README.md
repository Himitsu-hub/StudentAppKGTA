# StudentApp КГТА

Мобильное приложение для студентов КГТА (КГТУ / dksta.ru): расписание, преподаватели, новости, виджеты, офлайн.

## Milestone: ядро готово (остались карты этажей)

**Статус на момент этого коммита:** продукт по расписанию и серверу **закончен**.  
**Следующий крупный блок (не сделан):** карта этажей / навигация по корпусу — когда появятся планы этажей.

| Готово | Не сделано |
|--------|------------|
| Android: расписание, группы, тема, splash, логотип | Карта этажей |
| Офлайн-кэш + «обновлено в …» + pull-to-refresh | Google Play / iOS (позже) |
| Виджеты 4×2 и 2×2 | |
| Backend FastAPI: Excel → парсинг → JSON API | |
| Админка `/admin` (загрузка Excel, проверка, публикация) | |
| Свой VPS + домен + HTTPS (Caddy + Let’s Encrypt) | |
| Контакты кампуса с dksta.ru | |
| Деплой `deploy.sh`, UFW (закрыть 8000 снаружи), бэкап в админке | |

**Версия:** Android `2.3.30` (code 55) · API `2.0.0`

---

## Сервер и домен (production)

| Что | Значение |
|-----|----------|
| VPS | `157.22.186.149` |
| Домен (полный) | **`apistudentkgtu.ru`** (`apistudentkgtu` + зона `.ru`) |
| API (приложение) | `https://apistudentkgtu.ru/` |
| Health | `https://apistudentkgtu.ru/health` |
| Админка | `https://apistudentkgtu.ru/admin` |
| Путь на сервере | `/opt/studentapp` |
| Стек на VPS | Docker Compose: FastAPI (`127.0.0.1:8000`) + Caddy (80/443) |
| Пароль админки | только в `.env` на сервере (не в git) |

### Как устроено

```
Админ (Excel) → https://…/admin → парсинг/проверка → SQLite
Студент (приложение) → HTTPS JSON API → карточки UI / виджеты
```

Студенты **не** получают Excel — только JSON через API.

---

## Структура репозитория

```
app/                 # Android (Kotlin + Jetpack Compose)
server/              # FastAPI + Docker + Caddyfile
server/scripts/      # harden_firewall.sh (UFW)
docs/HTTPS_SETUP.md  # DNS, HTTPS, деплой, чеклист
deploy.sh            # Деплой backend на VPS с Mac
```

---

## Android

### Стек
- Jetpack Compose + Material 3
- Hilt, Room, Retrofit, DataStore, Coroutines + Flow

### Сборка
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`  
`BuildConfig.BASE_URL` = `https://apistudentkgtu.ru/`

---

## Backend

### Локально
```bash
cd server
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # задайте ADMIN_PASSWORD
uvicorn main:app --reload --port 8000
```

### API
| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/health` | Healthcheck |
| GET | `/api/courses` | Курсы |
| GET | `/api/groups?course=` | Группы |
| GET | `/api/schedule?course=&group=&subgroup=` | Расписание |
| GET | `/api/teachers` | Преподаватели |
| GET | `/api/news` | Новости с dksta.ru |
| GET | `/api/week-type` | Числитель/знаменатель |
| GET | `/admin` | Админ-панель |
| POST | `/admin/upload` | Загрузка Excel |
| GET | `/admin/backup/list` | Список файлов бэкапа |
| GET | `/admin/backup/zip` | ZIP бэкапа (пароль) |

### Деплой HTTPS (с Mac, не с VPS)
```bash
cd ~/StudioProjects/StudentAppKGTA
ADMIN_PASSWORD='…' DOMAIN='apistudentkgtu.ru' ./deploy.sh
```

Полная инструкция: [`docs/HTTPS_SETUP.md`](docs/HTTPS_SETUP.md)

---

## Что дальше

1. **Карты этажей** — когда будут схемы корпусов.  
2. По желанию: Google Play, iOS (тот же API).  
3. После деплоя: сменить `ADMIN_PASSWORD`, если пароль светился в чатах.
