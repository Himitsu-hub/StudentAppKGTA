# HTTPS для StudentApp — с чего начать

**Production (готово):** `https://apistudentkgtu.ru`  
VPS: `157.22.186.149` · админка: `https://apistudentkgtu.ru/admin`

Ниже — как это поднималось (чеклист / восстановление). Старый HTTP-only вариант: `http://157.22.186.149:8000` (порт 8000 снаружи лучше закрыть).

## Зачем это нужно

| HTTP сейчас | HTTPS потом |
|-------------|-------------|
| Трафик можно перехватить | Шифрование |
| Пароль админки по открытому каналу | Безопаснее |
| Google Play не любит cleartext | Release без cleartext |
| IP + порт в приложении | Нормальный URL |

---

## Шаг 1. Купить домен (обязательно)

**Let's Encrypt не выдаёт сертификат на голый IP** — нужен домен.

### Что купить
- Любой домен, например: `studentkgta.ru`, `kgta-app.ru`, `myschedule.ru`
- Достаточно **самого дешёвого** `.ru` / `.com` / `.online` (~200–500 ₽/год)

### Где купить (примеры)
| Регистратор | Плюсы |
|-------------|--------|
| [reg.ru](https://www.reg.ru) | Удобно на русском |
| [Timeweb](https://timeweb.com) | Часто рядом с VPS |
| [Namecheap](https://www.namecheap.com) | Дёшево .com |
| [Cloudflare Registrar](https://www.cloudflare.com/products/registrar/) | По себестоимости + DNS |

**Не обязательно** покупать хостинг у того же места — только **домен**.  
VPS у тебя уже есть (`157.22.186.149`).

### Поддомен или корень?
Рекомендация для API:

```text
api.твой-домен.ru  →  157.22.186.149
```

Админку тоже можно открывать как `https://api.твой-домен.ru/admin`.

---

## Шаг 2. DNS — A-запись

В панели домена создай:

| Тип | Имя | Значение | TTL |
|-----|-----|----------|-----|
| **A** | `api` (или `@` если корень) | `157.22.186.149` | 300 / Auto |

Проверка с Mac (через 5–30 мин, иногда до 24 ч):

```bash
dig +short api.твой-домен.ru
# должно вывести: 157.22.186.149
```

или

```bash
ping api.твой-домен.ru
```

---

## Шаг 3. Порты на VPS

Открыть **80** и **443** (для Let's Encrypt и HTTPS).

На сервере (если ufw):

```bash
ufw allow 80/tcp
ufw allow 443/tcp
ufw status
```

Порт **8000** снаружи лучше **закрыть** (API только через Caddy).

```bash
# опционально, после проверки HTTPS:
ufw deny 8000/tcp
```

---

## Шаг 4. Деплой с HTTPS

На своём Mac, из корня проекта:

```bash
export ADMIN_PASSWORD='твой-сильный-пароль'
export DOMAIN='api.твой-домен.ru'
./deploy.sh
```

Скрипт:
1. Зальёт код на `/opt/studentapp`
2. Запишет `.env` с `ADMIN_PASSWORD` и `DOMAIN`
3. Поднимет `schedule-api` + **Caddy**
4. Caddy сам получит сертификат Let's Encrypt

Проверка:

```bash
curl -sf https://api.твой-домен.ru/health
curl -sf https://api.твой-домен.ru/api/courses
```

Админка: `https://api.твой-домен.ru/admin`

---

## Шаг 5. Приложение Android

Когда HTTPS работает, в `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "BASE_URL", "\"https://api.твой-домен.ru/\"")
```

И в `network_security_config.xml` — убрать cleartext для боевого IP (оставить только localhost/эмулятор).

Соберёшь новый APK — и приложение ходит только по HTTPS.

---

## Сколько это стоит (примерно)

| Статья | Цена |
|--------|------|
| Домен .ru / .com | ~200–800 ₽ / год |
| VPS | уже есть |
| Сертификат Let's Encrypt | **бесплатно** |
| Caddy | **бесплатно** |

---

## Закрыть порт 8000 снаружи

После HTTPS API должен быть доступен **только** через `https://домен` (порт 443).  
Порт 8000 — служебный (внутри сервера / localhost).

На VPS (root):

```bash
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw deny 8000/tcp
ufw --force enable
ufw status
```

Или: `bash /opt/studentapp/scripts/harden_firewall.sh` (после деплоя скрипта).

Docker уже слушает `127.0.0.1:8000` — снаружи 8000 и так не должен быть виден; ufw — дополнительная защита.

## Бэкап из админки

`https://домен/admin` → блок **«Бэкап»**:
- **Скачать всё (ZIP)** — Excel всех курсов + `schedule.db` + `teachers.json`
- Отдельные файлы по списку

Храните ZIP на своём диске / облаке (не только на VPS).

## Чего **не** делать

- ❌ Не покупать «SSL-сертификат» за деньги — Let's Encrypt хватит  
- ❌ Не ставить самоподписанный сертификат в production (Android его не примет без плясок)  
- ❌ Не светить `ADMIN_PASSWORD` в чатах/GitHub  
- ❌ Не оставлять порт 8000 открытым в интернет после HTTPS  

---

## Минимальный чеклист

1. [ ] Купить домен  
2. [ ] A-запись → `157.22.186.149`  
3. [ ] `dig` показывает правильный IP  
4. [ ] Порты 80/443 открыты  
5. [ ] `DOMAIN=... ADMIN_PASSWORD=... ./deploy.sh`  
6. [ ] `curl https://DOMAIN/health` OK  
7. [ ] Обновить `BASE_URL` в приложении  
8. [ ] Собрать APK и проверить  

---

## Когда вернёшься с доменом

Напиши, например:

> Домен: `api.studentkgta.ru`, DNS уже смотрит на VPS

Дальше вместе:
1. Проверим DNS  
2. Задеплоим Caddy  
3. Переключим Android на `https://...`  
4. Уберём cleartext для production  

Пока домена нет — конфиги уже лежат в `server/` (Caddyfile + docker-compose), ничего не ломается: API по-прежнему на `127.0.0.1:8000` на сервере.
