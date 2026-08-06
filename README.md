# StudentApp КГТА

Мобильное приложение для студентов КГТА (КГТУ / dksta.ru): расписание, преподаватели, новости, виджеты, офлайн.

**Платформы:** Android (`app/`) · iOS (`ios/`) · Backend (`server/`).

## Milestone: ядро готово (остались карты этажей)

**Статус:** продукт по расписанию и серверу **закончен** на Android **и** iOS.  
**Следующий крупный блок:** карта этажей / навигация по корпусу — когда появятся планы этажей.

| Готово | Не сделано |
|--------|------------|
| Android: расписание, тема, splash, виджеты, офлайн | Карта этажей |
| **iOS (SwiftUI):** те же экраны, splash, офлайн, свайп назад | Google Play / App Store (публикация) |
| Backend FastAPI: Excel → парсинг → JSON API | |
| Админка `/admin` + favicon | |
| VPS + домен **apistudentkgtu.ru** + HTTPS | |

**Версия:** Android `2.3.30` (code 55) · iOS `1.0` · API `2.0.0`

---

## Сервер и домен (production)

| Что | Значение |
|-----|----------|
| VPS | `157.22.186.149` |
| Домен | **`apistudentkgtu.ru`** |
| API | `https://apistudentkgtu.ru/` |
| Health | `https://apistudentkgtu.ru/health` |
| Админка | `https://apistudentkgtu.ru/admin` |
| Путь на сервере | `/opt/studentapp` |

```
Админ (Excel) → /admin → парсинг → SQLite
Студент (Android / iOS) → HTTPS JSON → карточки UI
```

---

## Структура репозитория

```
app/                 # Android (Kotlin + Jetpack Compose)
ios/                 # iOS (SwiftUI) — open StudentKGTU_IOS.xcodeproj
server/              # FastAPI + Docker + Caddy + админка
docs/HTTPS_SETUP.md
deploy.sh
```

---

## Android

### Стек
- Jetpack Compose + Material 3, Hilt, Room, Retrofit, DataStore

### Сборка
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`  
`BuildConfig.BASE_URL` = `https://apistudentkgtu.ru/`

---

## iOS

Display name: **КГТУ Студент** · API: `https://apistudentkgtu.ru/`

```bash
open ios/StudentKGTU_IOS.xcodeproj
```

1. Xcode → выбрать iPhone (или симулятор)  
2. Signing → Team = ваш Apple ID  
3. ▶ Run  
4. На iPhone: **Настройки → Основные → VPN и управление устройством → Доверить**

Подробнее: [`ios/README.md`](ios/README.md)

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
| GET | `/api/news` | Новости |
| GET | `/api/week-type` | Числитель/знаменатель |
| GET | `/admin` | Админ-панель |
| POST | `/admin/upload` | Загрузка Excel |

### Деплой HTTPS (с Mac)
```bash
cd ~/StudioProjects/StudentAppKGTA
ADMIN_PASSWORD='…' DOMAIN='apistudentkgtu.ru' ./deploy.sh
```

Полная инструкция: [`docs/HTTPS_SETUP.md`](docs/HTTPS_SETUP.md)

---

## Что дальше

1. **Карты этажей** — Android + iOS, когда будут схемы.  
2. Публикация в Google Play / App Store (по желанию).  
3. После деплоя: сменить `ADMIN_PASSWORD`, если пароль светился в чатах.
