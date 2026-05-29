#include <Adafruit_NeoPixel.h>
#include <WiFi.h>
#include <WebServer.h>

// Adjust these pins to your wiring if needed.
static constexpr int LED_COUNT = 1061;
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
unsigned long nextAutoTick = 0;
uint16_t autoStep = 0;

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
  {973, 1060}
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
}

void showAll(uint32_t color) {
  setRange(0, LED_COUNT, color);
  strip.show();
}

void setRelay(bool on) {
  relayActive = on;
  digitalWrite(RELAY_PIN, on ? LOW : HIGH);
}

void setModeAuto(bool enabled) {
  autoMode = enabled;
  autoStep = 0;
  nextAutoTick = millis();
}

void setColorPreset(uint32_t color) {
  currentColor = color;
}

bool decodeRoomCommand(int command, int &floor, int &room) {
  if (command >= 111 && command <= 118) {
    floor = 1;
    room = command - 110;
    return true;
  }

  if (command < 81) {
    return false;
  }

  floor = command / 80 + 1;
  if (floor < 2 || floor > 19) {
    return false;
  }

  const int remainder = command % 80;
  room = remainder == 0 ? 1 : (remainder / 10 + 1);
  return room >= 1 && room <= 8;
}

void applyFloorOn(int floor) {
  if (floor < 1 || floor > 19) {
    return;
  }

  const Range range = FLOOR_RANGES[floor - 1];
  setRelay(true);
  setRange(range.start, range.endExclusive, currentColor);
  strip.show();
}

void applyRoomOn(int floor, int room) {
  if (floor < 1 || floor > 19 || room < 1 || room > 8) {
    return;
  }

  setRelay(true);

  if (floor == 1) {
    const Range range = FLOOR1_ROOMS[room - 1];
    setRange(range.start, range.endExclusive, currentColor);
    strip.show();
    return;
  }

  const int base = (floor - 2) * 54;
  const Range range = FLOOR_2_TO_19_ROOMS[room - 1];
  setRange(range.start + base, range.endExclusive + base, currentColor);

  if (floor == 19) {
    switch (room) {
      case 1: setRange(1038, 1041, currentColor); break;
      case 2: setRange(1035, 1037, currentColor); break;
      case 4: setRange(1029, 1031, currentColor); break;
      case 5:
        setRange(1027, 1028, currentColor);
        setRange(1058, 1060, currentColor);
        break;
      case 7: setRange(1053, 1056, currentColor); break;
      case 8: setRange(1049, 1053, currentColor); break;
      default: break;
    }
  }

  strip.show();
}

void applyRoomOff(int floor, int room) {
  if (floor < 1 || floor > 19 || room < 1 || room > 8) {
    return;
  }

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
        setRange(1058, 1060, strip.Color(0, 0, 0));
        break;
      case 7: setRange(1053, 1056, strip.Color(0, 0, 0)); break;
      case 8: setRange(1049, 1053, strip.Color(0, 0, 0)); break;
      default: break;
    }
  }

  strip.show();
}

void applyCommand(const String &value) {
  if (value == "50") {
    setRelay(true);
    return;
  }

  if (value == "52") {
    setRelay(false);
    clearAll();
    return;
  }

  if (value == "80") {
    setModeAuto(true);
    setRelay(true);
    return;
  }

  if (value == "60") {
    setModeAuto(false);
    return;
  }

  if (value == "11111") {
    setRelay(true);
    showAll(currentColor);
    return;
  }

  if (value == "50000") {
    setColorPreset(strip.Color(255, 200, 50));
    return;
  }

  if (value == "50100") {
    setColorPreset(strip.Color(0, 100, 0));
    return;
  }

  if (value == "50200") {
    setColorPreset(strip.Color(100, 100, 0));
    return;
  }

  if (value == "50300") {
    setColorPreset(strip.Color(100, 0, 0));
    return;
  }

  const int numeric = value.toInt();

  if (numeric >= 10001 && numeric <= 10019) {
    applyFloorOn(numeric - 10000);
    return;
  }

  if (numeric >= 200) {
    const int baseCommand = numeric - 100;
    int floor = 0;
    int room = 0;
    if (decodeRoomCommand(baseCommand, floor, room)) {
      applyRoomOff(floor, room);
    }
    return;
  }

  int floor = 0;
  int room = 0;
  if (decodeRoomCommand(numeric, floor, room)) {
    applyRoomOn(floor, room);
  }
}

void runAutoAnimation() {
  if (!autoMode) {
    return;
  }

  const unsigned long now = millis();
  if (now < nextAutoTick) {
    return;
  }

  nextAutoTick = now + 100;
  autoStep++;

  if (autoStep < 500) {
    const int index = random(LED_COUNT);
    strip.setPixelColor(index, currentColor);
    strip.show();
  } else if (autoStep < 1500) {
    const int index = random(LED_COUNT);
    strip.setPixelColor(index, strip.Color(0, 0, 0));
    strip.show();
  } else if (autoStep == 2200) {
    setRelay(false);
    clearAll();
  } else if (autoStep > 2700) {
    autoStep = 0;
  }
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
  json += "\"color\":\"warm\"";
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

  setupWifi();
  setupRoutes();
  server.begin();
}

void loop() {
  server.handleClient();
  runAutoAnimation();

  if (digitalRead(BUTTON_AUTO_PIN) == LOW) {
    setModeAuto(true);
    setRelay(true);
    delay(200);
  }

  if (digitalRead(BUTTON_MANUAL_PIN) == LOW) {
    setModeAuto(false);
    delay(200);
  }
}
