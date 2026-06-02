#include <Adafruit_NeoPixel.h>
#include <WiFi.h>
#include <WebServer.h>

// Раскомментируйте строчку ниже для режима тестирования с 10 светодиодами.
// Закомментируйте её (поставьте // перед #define), когда подключите весь макет на 1061 светодиод.
#define TEST_10_LEDS

#ifdef TEST_10_LEDS
static constexpr int LED_COUNT = 10;
#else
static constexpr int LED_COUNT = 1061;
#endif

static constexpr int LED_PIN = 18;
static constexpr int RELAY_PIN = 23;
static constexpr int FLAG_PIN = 34;
static constexpr int BUTTON_AUTO_PIN = 32;
static constexpr int BUTTON_MANUAL_PIN = 33;

static constexpr char WIFI_SSID[] = "LED-Control";
static constexpr char WIFI_PASSWORD[] = "12345678";

Adafruit_NeoPixel strip(LED_COUNT, LED_PIN, NEO_GRB + NEO_KHZ800);
WebServer server(80);

bool autoMode = false;
bool relayActive = false;
uint32_t currentColor = 0;
unsigned long lastAutoTick = 0;
uint16_t autoStep = 0;

// State tracking for each window in the multi-story mockup
bool roomStates[20][9]; // 1..19 floors, 1..8 windows
uint8_t roomColors[20][9]; // 0 = white, 1 = green, 2 = yellow, 3 = red

uint32_t getRoomColor(int floor, int room) {
  uint8_t cIdx = roomColors[floor][room];
  if (cIdx == 1) return strip.Color(0, 100, 0); // green
  if (cIdx == 2) return strip.Color(100, 100, 0); // yellow
  if (cIdx == 3) return strip.Color(100, 0, 0); // red
  return strip.Color(255, 200, 50); // warm white (default)
}

#ifndef TEST_10_LEDS
struct Range {
  int start;
  int endExclusive;
};

static constexpr Range FLOOR_RANGES[19] = {
  {0, 55},
  {55, 109},
  {109, 163},
  {163, 217},
  {217, 271},
  {271, 325},
  {325, 379},
  {379, 433},
  {433, 487},
  {487, 541},
  {541, 595},
  {595, 649},
  {649, 703},
  {703, 757},
  {757, 811},
  {811, 865},
  {865, 919},
  {919, 973},
  {973, 1061}
};

static constexpr Range FLOOR1_ROOMS[8] = {
  {13, 22},
  {4, 13},
  {0, 4},
  {51, 55},
  {41, 51},
  {37, 41},
  {34, 36},
  {22, 34}
};

static constexpr Range FLOOR_2_TO_19_ROOMS[8] = {
  {68, 80},
  {62, 68},
  {59, 62},
  {55, 59},
  {106, 109},
  {102, 106},
  {96, 102},
  {89, 96}
};
#endif

void setRange(int start, int endExclusive, uint32_t color) {
  start = max(0, start);
  endExclusive = min(endExclusive, LED_COUNT);
  for (int i = start; i < endExclusive; ++i) {
    strip.setPixelColor(i, color);
  }
}

void clearAll() {
  strip.clear();
  strip.show();
  for (int f = 1; f <= 19; f++) {
    for (int r = 1; r <= 8; r++) {
      roomStates[f][r] = false;
    }
  }
}

void showAll(uint32_t color) {
  setRange(0, LED_COUNT, color);
  strip.show();
  for (int f = 1; f <= 19; f++) {
    for (int r = 1; r <= 8; r++) {
      roomStates[f][r] = true;
    }
  }
}

void setRelay(bool on) {
  relayActive = on;
  digitalWrite(RELAY_PIN, on ? LOW : HIGH);
}

void setModeAuto(bool enabled) {
  autoMode = enabled;
  if (enabled) {
    autoStep = 0;
    lastAutoTick = millis() - 100;
    strip.clear();
    strip.show();
  } else {
    // Включили ручной режим - восстанавливаем сохраненное состояние дома
    strip.clear();
    for (int f = 1; f <= 19; f++) {
      for (int r = 1; r <= 8; r++) {
        if (roomStates[f][r]) {
          applyRoomOn(f, r);
        }
      }
    }
    strip.show();
  }
}

void setColorPreset(uint32_t color) {
  currentColor = color;
}

void applyFloorOn(int floor) {
  if (floor < 1 || floor > 19) {
    return;
  }

  setRelay(true);
  
#ifdef TEST_10_LEDS
  if (floor != 1) return;
  // Для теста 10 светодиодов: включаем светодиоды 0..7 для комнат 1..8
  for (int r = 1; r <= 8; r++) {
    roomStates[floor][r] = true;
    uint32_t color = getRoomColor(floor, r);
    strip.setPixelColor(r - 1, color);
  }
#else
  // Fill the entire floor background first using the first room's color
  const Range range = FLOOR_RANGES[floor - 1];
  uint32_t floorColor = getRoomColor(floor, 1);
  setRange(range.start, range.endExclusive, floorColor);

  // Draw each room with its specific color
  for (int r = 1; r <= 8; r++) {
    roomStates[floor][r] = true;
    uint32_t color = getRoomColor(floor, r);
    if (floor == 1) {
      const Range rRange = FLOOR1_ROOMS[r - 1];
      setRange(rRange.start, rRange.endExclusive, color);
    } else {
      const int base = (floor - 2) * 54;
      const Range rRange = FLOOR_2_TO_19_ROOMS[r - 1];
      setRange(rRange.start + base, rRange.endExclusive + base, color);
    }
  }

  // Handle floor 19 extra ranges
  if (floor == 19) {
    for (int r = 1; r <= 8; r++) {
      uint32_t color = getRoomColor(19, r);
      switch (r) {
        case 1: setRange(1038, 1041, color); break;
        case 2: setRange(1035, 1037, color); break;
        case 4: setRange(1029, 1031, color); break;
        case 5:
          setRange(1027, 1028, color);
          setRange(1058, 1061, color);
          break;
        case 7: setRange(1053, 1056, color); break;
        case 8: setRange(1049, 1053, color); break;
        default: break;
      }
    }
  }
#endif

  strip.show();
}

void applyFloorOff(int floor) {
  if (floor < 1 || floor > 19) {
    return;
  }

#ifdef TEST_10_LEDS
  if (floor != 1) return;
  // Для теста 10 светодиодов: гасим светодиоды 0..7
  for (int r = 1; r <= 8; r++) {
    strip.setPixelColor(r - 1, strip.Color(0, 0, 0));
    roomStates[floor][r] = false;
  }
#else
  const Range range = FLOOR_RANGES[floor - 1];
  setRange(range.start, range.endExclusive, strip.Color(0, 0, 0));
  for (int r = 1; r <= 8; r++) {
    roomStates[floor][r] = false;
  }
#endif
  strip.show();
}

void applyRoomOn(int floor, int room) {
  if (floor < 1 || floor > 19 || room < 1 || room > 8) {
    return;
  }

  setRelay(true);
  roomStates[floor][room] = true;

  uint32_t color = getRoomColor(floor, room);

#ifdef TEST_10_LEDS
  if (floor != 1) return;
  // Для теста 10 светодиодов: маппим комнаты 1..8 на светодиоды 0..7
  strip.setPixelColor(room - 1, color);
#else
  if (floor == 1) {
    const Range range = FLOOR1_ROOMS[room - 1];
    setRange(range.start, range.endExclusive, color);
    strip.show();
    return;
  }

  const int base = (floor - 2) * 54;
  const Range range = FLOOR_2_TO_19_ROOMS[room - 1];
  setRange(range.start + base, range.endExclusive + base, color);

  if (floor == 19) {
    switch (room) {
      case 1: setRange(1038, 1041, color); break;
      case 2: setRange(1035, 1037, color); break;
      case 4: setRange(1029, 1031, color); break;
      case 5:
        setRange(1027, 1028, color);
        setRange(1058, 1061, color);
        break;
      case 7: setRange(1053, 1056, color); break;
      case 8: setRange(1049, 1053, color); break;
      default: break;
    }
  }
#endif

  strip.show();
}

void applyRoomOff(int floor, int room) {
  if (floor < 1 || floor > 19 || room < 1 || room > 8) {
    return;
  }

  roomStates[floor][room] = false;

#ifdef TEST_10_LEDS
  if (floor != 1) return;
  // Для теста 10 светодиодов: гасим светодиод этой комнаты
  strip.setPixelColor(room - 1, strip.Color(0, 0, 0));
#else
  if (floor == 1) {
    const Range range = FLOOR1_ROOMS[room - 1];
    setRange(range.start, range.endExclusive, strip.Color(0, 0, 0));
    strip.show();
    return;
  }

  const int base = (floor - 2) * 54;
  const Range range = FLOOR_2_TO_19_ROOMS[room - 1];
  setRange(range.start + base, range.endExclusive + base, strip.Color(0, 0, 0));

  if (floor == 19) {
    switch (room) {
      case 1: setRange(1038, 1041, strip.Color(0, 0, 0)); break;
      case 2: setRange(1035, 1037, strip.Color(0, 0, 0)); break;
      case 4: setRange(1029, 1031, strip.Color(0, 0, 0)); break;
      case 5:
        setRange(1027, 1028, strip.Color(0, 0, 0));
        setRange(1058, 1061, strip.Color(0, 0, 0));
        break;
      case 7: setRange(1053, 1056, strip.Color(0, 0, 0)); break;
      case 8: setRange(1049, 1053, strip.Color(0, 0, 0)); break;
      default: break;
    }
  }
#endif

  strip.show();
}

void applyCommand(const String &value) {
  if (value.startsWith("F") && value.indexOf("W") > 0 && value.indexOf("S") > 0) {
    int wPos = value.indexOf("W");
    int sPos = value.indexOf("S");
    int floor = value.substring(1, wPos).toInt();
    int window = value.substring(wPos + 1, sPos).toInt();
    String state = value.substring(sPos + 1);

    if (autoMode && state != "2") {
      setModeAuto(false);
    }

    if (state == "3") {
      // Специальная команда для выхода из режима авто.
      // Код выше уже вызвал setModeAuto(false), так что просто выходим.
      return;
    }

    if (state == "1") {
      if (floor == 0 && window == 0) {
        setRelay(true);
        for (int f = 1; f <= 19; f++) {
          applyFloorOn(f);
        }
      } else if (window == 0) {
        applyFloorOn(floor);
      } else {
        applyRoomOn(floor, window);
      }
    } else if (state == "0") {
      if (floor == 0 && window == 0) {
        setRelay(false);
        clearAll();
      } else if (window == 0) {
        applyFloorOff(floor);
      } else {
        applyRoomOff(floor, window);
      }
    } else if (state == "2") {
      setModeAuto(true);
      setRelay(true);
    } else if (state.startsWith("C")) {
      int colorIdx = state.substring(1).toInt();
      uint32_t targetColor = strip.Color(255, 200, 50);
      if (colorIdx == 0) targetColor = strip.Color(255, 200, 50);
      else if (colorIdx == 1) targetColor = strip.Color(0, 100, 0);
      else if (colorIdx == 2) targetColor = strip.Color(100, 100, 0);
      else if (colorIdx == 3) targetColor = strip.Color(100, 0, 0);

      if (floor == 0 && window == 0) {
        // Change color for the entire building
        setColorPreset(targetColor);
        for (int f = 1; f <= 19; f++) {
          for (int r = 1; r <= 8; r++) {
            roomColors[f][r] = colorIdx;
            // Обновляем физически только те комнаты, которые уже включены
            if (roomStates[f][r]) {
              applyRoomOn(f, r);
            }
          }
        }
      } else if (window == 0) {
        // Change color for a specific floor
        for (int r = 1; r <= 8; r++) {
          roomColors[floor][r] = colorIdx;
          if (roomStates[floor][r]) {
            applyRoomOn(floor, r);
          }
        }
      } else {
        // Change color for a specific room
        roomColors[floor][window] = colorIdx;
        applyRoomOn(floor, window);
      }
    }
  }
}

void runAutoAnimation() {
  if (!autoMode) {
    return;
  }

  const unsigned long now = millis();
  // Около 20 кадров в секунду для бегущего огонька
  if (now - lastAutoTick < 50) {
    return;
  }

  lastAutoTick = now;

  // Гасим предыдущий пиксель
  strip.setPixelColor(autoStep, strip.Color(0, 0, 0));

  // Сдвигаем на следующий пиксель
  autoStep++;
  if (autoStep >= LED_COUNT) {
    autoStep = 0;
  }

  // Включаем текущий пиксель белым цветом
  strip.setPixelColor(autoStep, strip.Color(255, 255, 255));
  
  strip.show();
}

void handlePing() {
  server.send(200, "text/plain", "OK");
}

void handleCommand() {
  if (!server.hasArg("value")) {
    server.send(400, "text/plain", "Missing value");
    return;
  }

  const String command = server.arg("value");
  applyCommand(command);
  server.send(200, "text/plain", command);
}

void handleStatus() {
  String json = "{";
  json += "\"mode\":\"";
  json += autoMode ? "Auto" : "Manual";
  json += "\",";
  json += "\"relay\":";
  json += relayActive ? "true" : "false";
  json += ",";
  json += "\"active\":[";
  bool first = true;
  for (int f = 1; f <= 19; f++) {
    for (int r = 1; r <= 8; r++) {
      if (roomStates[f][r]) {
        if (!first) json += ",";
        json += "\"F" + String(f) + "W" + String(r) + "\"";
        first = false;
      }
    }
  }
  json += "],";
  json += "\"colors\":{";
  first = true;
  for (int f = 1; f <= 19; f++) {
    for (int r = 1; r <= 8; r++) {
      if (roomColors[f][r] != 0) {
        if (!first) json += ",";
        json += "\"F" + String(f) + "W" + String(r) + "\":" + String(roomColors[f][r]);
        first = false;
      }
    }
  }
  json += "}";
  json += "}";
  server.send(200, "application/json", json);
}

void setupWifi() {
  WiFi.mode(WIFI_AP);
  WiFi.softAP(WIFI_SSID, WIFI_PASSWORD);
}

void setupRoutes() {
  server.on("/ping", HTTP_GET, handlePing);
  server.on("/command", HTTP_GET, handleCommand);
  server.on("/status", HTTP_GET, handleStatus);
  server.onNotFound([]() {
    server.send(404, "text/plain", "Not found");
  });
}

void setup() {
  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, HIGH);

  pinMode(FLAG_PIN, INPUT_PULLUP);
  pinMode(BUTTON_AUTO_PIN, INPUT_PULLUP);
  pinMode(BUTTON_MANUAL_PIN, INPUT_PULLUP);

  strip.begin();
  strip.show();
  setColorPreset(strip.Color(255, 200, 50));

  randomSeed(micros());

  // Initialize all roomStates to false and roomColors to 0
  for (int f = 0; f < 20; f++) {
    for (int r = 0; r < 9; r++) {
      roomStates[f][r] = false;
      roomColors[f][r] = 0;
    }
  }

  setupWifi();
  setupRoutes();
  server.begin();
}

void loop() {
  server.handleClient();
  runAutoAnimation();

  static unsigned long lastButtonPress = 0;
  const unsigned long now = millis();
  if (now - lastButtonPress >= 200) {
    if (digitalRead(BUTTON_AUTO_PIN) == LOW) {
      setModeAuto(true);
      setRelay(true);
      lastButtonPress = now;
    } else if (digitalRead(BUTTON_MANUAL_PIN) == LOW) {
      setModeAuto(false);
      lastButtonPress = now;
    }
  }
}
