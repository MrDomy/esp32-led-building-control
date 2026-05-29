# Руководство по проекту: Android + ESP32 по HTTP

Это руководство я написал как практический разбор проекта для самостоятельного изучения за 3 дня.
Цель не просто “понять, где что лежит”, а научиться:
- читать код без страха;
- понимать поток данных от кнопки до ESP32;
- находить, где менять поведение;
- постепенно улучшать приложение и прошивку;
- не путаться между UI, логикой, протоколом и железом.

Название файла историческое. Сейчас проект уже работает не через Bluetooth, а через HTTP-запросы к ESP32.

---

## Что это за проект

Проект состоит из двух частей:
- Android-приложение на Kotlin;
- прошивка для ESP32 на C++/Arduino.

Android-приложение:
- показывает интерфейс с этажами, комнатами и режимами;
- хранит состояние выбранного этажа и включенных комнат;
- отправляет HTTP-запросы на ESP32;
- позволяет работать даже в офлайн-режиме интерфейса, если нужно просто тестировать UI.

ESP32:
- поднимает Wi-Fi точку доступа;
- принимает HTTP-запросы;
- управляет реле, режимами и светодиодами;
- отвечает на `/ping`, `/command` и `/status`.

Главная идея:

`кнопка в Android -> состояние -> HTTP-запрос -> ESP32 -> реле / светодиоды`

---

## Как читать этот документ

Если у тебя 3 дня, не пытайся выучить всё сразу.

Лучший путь:
- в первый день понять архитектуру и пройтись по основным файлам;
- во второй день разобрать команды и логику состояния;
- в третий день попробовать внести свою небольшую доработку.

В конце документа есть план на 3 дня и список улучшений, которые можно сделать самостоятельно.

---

## 1. Карта проекта

### Android-часть

Основные файлы:

- [MainActivity.kt](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/java/com/example/applicationledcontrol/MainActivity.kt)
- [ControlManager.kt](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/java/com/example/applicationledcontrol/ui/ControlManager.kt)
- [ControlUiState.kt](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/java/com/example/applicationledcontrol/ui/ControlUiState.kt)
- [Esp32HttpClient.kt](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/java/com/example/applicationledcontrol/data/Esp32HttpClient.kt)
- [BuildingProtocol.kt](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/java/com/example/applicationledcontrol/domain/BuildingProtocol.kt)
- [ThemeManager.kt](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/java/com/example/applicationledcontrol/ui/ThemeManager.kt)

Файлы разметки:

- [activity_main.xml](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/res/layout/activity_main.xml)
- [dialog_settings.xml](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/res/layout/dialog_settings.xml)

Важные ресурсы:

- [strings.xml](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/res/values/strings.xml)
- [colors.xml](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/res/values/colors.xml)
- [themes.xml](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/res/values/themes.xml)
- [AndroidManifest.xml](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/AndroidManifest.xml)

### Прошивка ESP32

- [esp32_building_controller.ino](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/esp32_firmware/esp32_building_controller.ino)

---

## 2. Как работает приложение в целом

Если смотреть на проект как на систему, то в нём есть 4 слоя:

### 2.1 UI-слой

Это то, что видит пользователь:
- кнопки;
- поле адреса ESP32;
- выбор этажа;
- кнопки комнат;
- кнопка офлайн-режима;
- настройки темы.

### 2.2 Слой состояния

Это `ControlUiState`.

Он хранит:
- подключен ли ESP32;
- какой адрес введен;
- включен ли офлайн-режим;
- какой этаж выбран;
- какие комнаты активны;
- каков последний ответ от ESP32.

### 2.3 Слой логики

Это `ControlManager`.

Он решает:
- что делать при нажатии кнопки;
- как менять состояние;
- какую команду послать;
- когда можно отправлять запрос, а когда нельзя.

### 2.4 Сетевой слой

Это `Esp32HttpClient`.

Он:
- строит URL;
- делает HTTP GET;
- читает ответ;
- сообщает об ошибке, если ESP32 не ответила.

---

## 3. Что делает каждый ключевой файл

### [MainActivity.kt](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/java/com/example/applicationledcontrol/MainActivity.kt)

Это главный экран.

Он:
- загружает layout;
- создает `ControlManager`;
- достаёт виджеты через `findViewById`;
- создает кнопки этажей и комнат;
- слушает состояние через `StateFlow`;
- показывает статус;
- открывает диалог настроек;
- сохраняет адрес ESP32 в `SharedPreferences`.

Если очень коротко:

`MainActivity` = связка между экраном и логикой.

### [ControlManager.kt](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/java/com/example/applicationledcontrol/ui/ControlManager.kt)

Это мозг приложения.

Он не рисует UI и не знает деталей разметки.  
Он только управляет состоянием и отправкой команд.

### [ControlUiState.kt](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/java/com/example/applicationledcontrol/ui/ControlUiState.kt)

Это снимок состояния экрана.

Если представить приложение как игру, то `ControlUiState` - это сохранение текущего прогресса.

### [Esp32HttpClient.kt](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/java/com/example/applicationledcontrol/data/Esp32HttpClient.kt)

Это низкоуровневый клиент HTTP.

Он ничего не знает про этажи, комнаты или цветовые кнопки.
Он умеет только отправлять запросы на ESP32.

### [BuildingProtocol.kt](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/java/com/example/applicationledcontrol/domain/BuildingProtocol.kt)

Это словарь протокола.

Он отвечает на вопрос:
- какая строка означает включить реле;
- какая строка означает включить комнату;
- какая строка означает выключить комнату;
- какая строка означает этаж;
- какие команды соответствуют цветам.

### [esp32_building_controller.ino](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/esp32_firmware/esp32_building_controller.ino)

Это прошивка для ESP32.

В ней:
- Wi-Fi точка доступа;
- HTTP-эндпоинты;
- логика светодиодной ленты;
- управление реле;
- простая автоанимация.

---

## 4. Поток данных от кнопки до ESP32

Рассмотрим пример: ты нажал комнату на экране.

### Шаг 1. Пользователь нажимает кнопку

В `activity_main.xml` есть кнопка комнаты.

В `MainActivity` ей назначен обработчик:

```kotlin
controlManager.toggleRoom(selectedFloor, roomNumber)
```

### Шаг 2. `ControlManager` меняет состояние

Внутри `toggleRoom()`:
- проверяется, существует ли такая комната;
- определяется, была ли она уже включена;
- обновляется `roomStates`;
- обновляется `isRelayActive`;
- выбирается команда для HTTP.

### Шаг 3. `BuildingProtocol` формирует команду

Например:
- включить комнату 1 на 1-м этаже -> `111`
- выключить её -> `211`
- включить этаж 1 -> `10001`
- выключить его комнаты -> список команд выключения

### Шаг 4. `Esp32HttpClient` отправляет запрос

Например:

```text
http://192.168.4.1/command?value=111
```

или:

```text
http://192.168.4.1/ping
```

### Шаг 5. ESP32 принимает запрос

Прошивка смотрит на путь:
- `/ping`
- `/command`
- `/status`

Если пришел `/command?value=111`, ESP32:
- распознаёт строку `111`;
- включает нужную часть ленты;
- при необходимости включает реле.

---

## 5. Как устроен `ControlUiState`

Вот структура состояния:

```kotlin
data class ControlUiState(
    val esp32Host: String = "192.168.4.1",
    val isConnected: Boolean = false,
    val allowOfflineInteraction: Boolean = false,
    val selectedFloor: Int = 1,
    val lastCommand: String? = null,
    val lastResponse: String? = null,
    val isRelayActive: Boolean = false,
    val currentMode: String = "Manual",
    val roomStates: Map<Int, Set<Int>> = emptyMap()
)
```

### Что означает каждое поле

- `esp32Host` - адрес ESP32.
- `isConnected` - ответила ли ESP32 на проверку.
- `allowOfflineInteraction` - можно ли кликать кнопки без реального подключения.
- `selectedFloor` - выбранный этаж.
- `lastCommand` - последняя отправленная команда.
- `lastResponse` - последний ответ или ошибка.
- `isRelayActive` - активировано ли реле.
- `currentMode` - текущий режим, например `Manual` или `Auto`.
- `roomStates` - карта активных комнат по этажам.

### Почему `roomStates` - это `Map<Int, Set<Int>>`

Потому что:
- ключ - этаж;
- значение - набор комнат, которые сейчас включены на этом этаже.

Пример:

```kotlin
mapOf(
    1 to setOf(1, 3, 8),
    5 to setOf(2, 4)
)
```

Это значит:
- на 1-м этаже включены комнаты 1, 3 и 8;
- на 5-м этаже включены комнаты 2 и 4.

Главный важный момент:
- в текущей версии можно включать несколько комнат одновременно;
- больше нет старой логики “одна кнопка заменяет другую”.

---

## 6. Как читать `ControlManager`

Это самый полезный файл для изучения логики.

### 6.1 `updateHost(host)`

Нормализует адрес ESP32:
- убирает `http://`;
- убирает `https://`;
- убирает лишние слэши.

### 6.2 `pingHost()`

Проверяет, отвечает ли ESP32.

Если запрос успешен:
- `isConnected = true`
- `lastCommand = "PING"`
- `lastResponse = "OK"` или текст ответа

Если неуспешен:
- `isConnected = false`
- в `lastResponse` пишется ошибка

### 6.3 `launchCommands(commands)`

Это важная внутренняя функция.

Она:
- выполняет запросы последовательно;
- защищает отправку `Mutex`-ом;
- делает паузу между командами;
- обновляет `lastCommand` и `lastResponse`.

Почему это важно:
- команды не должны накладываться друг на друга;
- ESP32 должна успеть прочитать и обработать запрос;
- при ошибке отправка останавливается.

### 6.4 `toggleRoom(floor, room)`

Это включение и выключение конкретной комнаты.

Поведение сейчас такое:
- если комната выключена, она включается;
- если комната включена, она выключается;
- если это последняя активная комната, реле тоже выключается.

### 6.5 `turnOnFloor(floor)`

Включает весь этаж целиком.

### 6.6 `turnOffFloor(floor)`

Выключает все комнаты этого этажа.

Это хороший пример того, как `ControlManager` объединяет:
- изменение локального состояния;
- формирование набора команд;
- отправку запросов на устройство.

### 6.7 `setAutoMode()` и `turnOnAll()`

Это команды более высокого уровня:
- `setAutoMode()` переводит систему в автоматический режим;
- `turnOnAll()` включает все комнаты всех этажей.

---

## 7. Как устроен `BuildingProtocol`

Этот файл особенно полезен, если хочешь менять поведение системы.

### Основные команды

- `RELAY_ON = "50"`
- `RELAY_OFF = "52"`
- `MODE_AUTO = "80"`
- `MODE_MANUAL = "60"`
- `COMMAND_ALL_ON = "11111"`

### Цветовые команды

- `COLOR_WHITE = "50000"`
- `COLOR_GREEN = "50100"`
- `COLOR_YELLOW = "50200"`
- `COLOR_RED = "50300"`

### Этажи

`getFloorCommand(floor)` возвращает команду вида:

```kotlin
10000 + floor
```

Пример:
- 1-й этаж -> `10001`
- 19-й этаж -> `10019`

### Комнаты

Для комнат у нас есть отдельная схема.

#### Первый этаж

- 1 -> `111`
- 2 -> `112`
- 3 -> `113`
- 4 -> `114`
- 5 -> `115`
- 6 -> `116`
- 7 -> `117`
- 8 -> `118`

#### Выключение комнаты

Выключающая команда обычно строится как:

```kotlin
onCommand + 100
```

Например:
- включить `118`
- выключить `218`

### Что важно для тебя как разработчика

Если потом меняется прошивка ESP32 или проводка:
- сначала меняется `BuildingProtocol`;
- потом под него подгоняется `ControlManager`;
- потом проверяется прошивка;
- потом UI.

Это правильный порядок.

---

## 8. Как устроен `Esp32HttpClient`

Это очень хороший файл для понимания работы с сетью.

### Что он делает

Он строит обычный HTTP GET:

```text
http://<host>/ping
http://<host>/command?value=111
```

### Что делает `normalizeHost()`

Пример:

```kotlin
" http://192.168.4.1/ "
```

превратится в:

```text
192.168.4.1
```

### Что делает `request()`

Он:
- создает `HttpURLConnection`;
- ставит таймауты;
- читает body ответа;
- если код ответа не 2xx, кидает `IOException`.

### Почему это написано через `suspend`

Потому что сетевые запросы нельзя делать в главном потоке.

`withContext(Dispatchers.IO)` переносит работу в фоновый поток.

Это стандартный и правильный подход в Android.

---

## 9. Как устроен `MainActivity`

Это файл, который удобно читать последним, когда уже понятна логика.

### Что делает `MainActivity`

- загружает тему;
- создает `ControlManager`;
- находит элементы интерфейса;
- заполняет список этажей;
- создает 8 кнопок комнат;
- читает адрес ESP32 из `SharedPreferences`;
- показывает статус;
- подписывается на `uiState`.

### Почему тут нет тяжелой логики

`MainActivity` должна быть тонкой.

Она не должна решать бизнес-логику, иначе код станет трудно поддерживать.

Идея простая:
- `MainActivity` рисует экран;
- `ControlManager` управляет поведением.

### Как работает поле адреса ESP32

Пользователь вводит адрес, например:

```text
192.168.4.1
```

Потом нажимает `Проверить ESP32`.

Приложение:
- сохраняет адрес;
- отправляет ping;
- если все хорошо, считает ESP32 доступной.

### Офлайн-режим

Кнопка `Режим без устройства` нужна для разработки.

Она:
- разрешает кликать кнопки;
- но не делает настоящие HTTP-запросы;
- удобно для проверки интерфейса и логики без ESP32.

### Почему кнопки этажей и комнат делаются в коде

Потому что так проще:
- не писать 19 отдельных кнопок этажей;
- не дублировать один и тот же XML;
- динамически менять число доступных комнат.

---

## 10. Как читать прошивку ESP32

Файл [esp32_building_controller.ino](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/esp32_firmware/esp32_building_controller.ino) уже сам по себе полезный учебный пример.

### 10.1 Библиотеки

Используются:
- `WiFi.h`
- `WebServer.h`
- `Adafruit_NeoPixel.h`

### 10.2 Что происходит в `setup()`

- настраиваются пины;
- запускается лента;
- включается Wi-Fi точка доступа;
- поднимаются HTTP-роуты.

### 10.3 Что происходит в `loop()`

- сервер обрабатывает запросы;
- проигрывается автоанимация;
- читаются аппаратные кнопки, если они есть.

### 10.4 HTTP-роуты

- `/ping` - проверка связи;
- `/command` - выполнение команды;
- `/status` - текущий статус устройства.

### 10.5 Как ESP32 понимает команды

Внутри есть `applyCommand()`.

Она сравнивает строку:
- `50`
- `52`
- `80`
- `11111`
- `50000`
- `111`
- `10001`
- и так далее.

Если строка соответствует известной команде, ESP32 выполняет нужное действие.

---

## 11. Как учиться по этому проекту за 3 дня

### День 1. Понять архитектуру

Цель дня:
- понять, какие части вообще есть в проекте;
- не пытаться запомнить все команды сразу.

Что читать:
- [MainActivity.kt](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/java/com/example/applicationledcontrol/MainActivity.kt)
- [ControlUiState.kt](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/java/com/example/applicationledcontrol/ui/ControlUiState.kt)
- [ControlManager.kt](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/java/com/example/applicationledcontrol/ui/ControlManager.kt)

Что нужно понять:
- откуда берется состояние;
- кто меняет состояние;
- почему UI обновляется автоматически;
- как кнопка становится командой.

Мини-практика:
- найди в `MainActivity` место, где создаются кнопки комнат;
- найди место, где экран подписывается на `uiState`;
- проследи, как кнопка приводит к `toggleRoom()`.

### День 2. Понять протокол и сеть

Цель дня:
- разобраться, как строка превращается в HTTP-запрос;
- понять, где описаны команды.

Что читать:
- [BuildingProtocol.kt](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/java/com/example/applicationledcontrol/domain/BuildingProtocol.kt)
- [Esp32HttpClient.kt](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/java/com/example/applicationledcontrol/data/Esp32HttpClient.kt)
- [esp32_building_controller.ino](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/esp32_firmware/esp32_building_controller.ino)

Что нужно понять:
- как строится URL;
- что такое `ping`;
- как выглядят запросы `command?value=...`;
- где ESP32 их принимает;
- как прошивка переводит строку в действие.

Мини-практика:
- возьми команду `111` и проследи весь путь;
- возьми `10001` и проследи весь путь;
- найди, где именно происходит включение реле.

### День 3. Менять и улучшать

Цель дня:
- попробовать сделать безопасное изменение;
- понять, что и где ломается при неправильной правке.

Что читать:
- [activity_main.xml](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/res/layout/activity_main.xml)
- [dialog_settings.xml](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/res/layout/dialog_settings.xml)
- [strings.xml](/C:/Users/Domy/AndroidStudioProjects/ApplicationLedControl/app/src/main/res/values/strings.xml)

Что попробовать изменить:
- текст кнопки;
- подпись статуса;
- цвет кнопки;
- количество комнат на этаже;
- поведение кнопки офлайн-режима;
- обработку ответа `/ping`.

Мини-практика:
- измени текст одной кнопки;
- измени один статус;
- запусти `./gradlew test`;
- проверь, не сломался ли проект.

---

## 12. Какие места в коде удобнее всего улучшать

Если захочешь развивать проект дальше, вот хорошие точки входа.

### 12.1 Улучшить связь Android и ESP32

Сейчас можно добавить:
- показ адреса и порта в удобном виде;
- кнопку повторной проверки;
- индикацию “ESP32 online/offline”;
- автоподключение или автопинг по таймеру.

### 12.2 Улучшить UI

Можно сделать:
- более явный индикатор выбранного этажа;
- отдельный счётчик активных комнат;
- цветовую подсветку состояния кнопок;
- анимацию переходов.

### 12.3 Улучшить логику состояния

Можно добавить:
- историю последних команд;
- отдельную модель для ошибок;
- режим “только просмотр”;
- сохранение активных комнат между запусками.

### 12.4 Улучшить прошивку ESP32

Можно добавить:
- JSON-ответы в `/status`;
- команду сброса;
- ручное управление через кнопки на плате;
- более строгую валидацию входящих команд;
- логирование в Serial.

---

## 13. На что смотреть, если что-то не работает

### Если ESP32 не отвечает

Проверяй:
- правильный ли адрес введён;
- поднята ли Wi-Fi точка доступа;
- отвечает ли `/ping`;
- совпадает ли порт;
- не блокируется ли HTTP на устройстве.

### Если кнопка нажимается, но ничего не меняется

Проверяй:
- попала ли команда в `ControlManager`;
- разрешен ли офлайн-режим;
- поставлено ли `isConnected = true`;
- корректна ли команда в `BuildingProtocol`;
- понимает ли ее ESP32.

### Если UI обновляется, но ESP32 не реагирует

Проверяй:
- правильный ли URL строится в `Esp32HttpClient`;
- что возвращает `/ping`;
- есть ли ошибка в `lastResponse`;
- видит ли ESP32 запрос вообще.

### Если прошивка светит не тем, чем нужно

Проверяй:
- карту `FLOOR1_ROOMS`;
- карту `FLOOR_2_TO_19_ROOMS`;
- соответствие команд и индексов;
- совпадает ли логика `applyRoomOn()` и `applyRoomOff()`.

---

## 14. Самые важные идеи, которые стоит запомнить

### Идея 1

UI не должен содержать сложную логику.

### Идея 2

`ControlUiState` - это источник правды о том, что сейчас происходит на экране.

### Идея 3

`BuildingProtocol` должен быть единственным местом, где описаны команды.

### Идея 4

HTTP-клиент не должен знать ничего про этажи и комнаты.

### Идея 5

Прошивка ESP32 должна понимать те же строки, что и Android отправляет.

Если помнить эти 5 пунктов, проект будет намного проще читать и улучшать.

---

## 15. Чеклист на конец 3 дней

После 3 дней ты должен уметь:
- объяснить, как кнопка в UI превращается в HTTP-запрос;
- найти, где хранится состояние экрана;
- добавить новую строку в протокол;
- поменять текст и поведение одной кнопки;
- понять, почему `ping` не проходит;
- описать, что делает прошивка ESP32;
- изменить один обработчик на ESP32 и не сломать всё остальное.

Если это получится, значит, ты уже не просто “посмотрел проект”, а реально его понял.

---

## 16. Короткий итог

Сейчас проект устроен так:
- Android управляет состоянием и интерфейсом;
- `ControlManager` является центром логики;
- `Esp32HttpClient` отправляет HTTP-запросы;
- `BuildingProtocol` хранит соответствие между действиями и строками;
- ESP32 принимает запросы и управляет железом.

Это хорошая архитектура для дальнейшего развития.

Если хочешь, следующим шагом я могу сделать ещё одну версию документации:
- совсем короткую, как шпаргалку;
- или, наоборот, расширенный учебник с примерами “вот тут поменять, вот тут посмотреть, вот так отладить”.  
