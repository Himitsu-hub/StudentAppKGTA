# StudentApp КГТА

Мобильное приложение для студентов КГТА: расписание, преподаватели, новости и напоминания.

## Структура

```
app/          # Android (Kotlin + Jetpack Compose)
server/       # FastAPI backend + Docker
deploy.sh     # Деплой backend на VPS
```

## Android

### Стек
- Jetpack Compose + Material 3
- Hilt (DI)
- Room (offline-кэш)
- Retrofit + OkHttp
- DataStore (выбор группы)
- Coroutines + Flow

### Сборка
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Базовый URL API задаётся в `app/build.gradle.kts` (`BuildConfig.BASE_URL`).

## Backend

### Запуск локально
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
| GET | `/api/courses` | Доступность курсов |
| GET | `/api/groups?course=` | Группы |
| GET | `/api/schedule?course=&group=&subgroup=` | Расписание (из SQLite-кэша) |
| GET | `/api/teachers` | Преподаватели |
| GET | `/api/news` | Новости с dksta.ru |
| GET | `/api/week-type` | Числитель/знаменатель |
| GET | `/admin` | Админ-панель (пароль в форме) |
| POST | `/admin/upload` | Загрузка Excel |

### Деплой
```bash
ADMIN_PASSWORD='strong-password' ./deploy.sh
```

При старте и после upload сервер индексирует Excel в SQLite (оба типа недели).

## Версия
`2.0.0`
