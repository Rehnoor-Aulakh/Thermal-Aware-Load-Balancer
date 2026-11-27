/*
 * ESP32 fan controller for the Thermal-Aware Load Balancer.
 *
 * The backend server agent sends one line over USB serial (115200 baud) with
 * every telemetry sample:
 *
 *     FAN <speed %> <CPU temperature> <predicted temperature, or -1>
 *
 * A plain number (0-100) typed in a serial monitor still sets the speed by
 * hand. If the agent stops sending, the fan goes to full speed.
 */
#include <Arduino.h>
#include <Wire.h>
#include <DHT.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

// ─── Pin Config ─────────────────────────────────────────
#define DHT_PIN 4
#define DHT_TYPE DHT22

#define OLED_WIDTH 128
#define OLED_HEIGHT 64
#define OLED_RESET -1
#define OLED_I2C_ADDR 0x3C
#define I2C_SDA_PIN 21
#define I2C_SCL_PIN 22

#define FAN_IN1_PIN 25
#define FAN_IN2_PIN 26
#define FAN_ENA_PIN 27

#define FAN_PWM_CHANNEL 0
#define FAN_PWM_FREQ 25000
#define FAN_PWM_RES 8

#define SENSOR_INTERVAL_MS 2000
#define DISPLAY_INTERVAL_MS 1000

// The agent sends every ~2 s; this much silence means it is gone.
#define HOST_TIMEOUT_MS 10000
#define FAIL_SAFE_PERCENT 100

// ─── Objects ────────────────────────────────────────────
DHT dht(DHT_PIN, DHT_TYPE);
Adafruit_SSD1306 oled(OLED_WIDTH, OLED_HEIGHT, &Wire, OLED_RESET);

// ─── Globals ────────────────────────────────────────────
float g_temperature = 0;
float g_humidity = 0;
bool g_sensorValid = false;

int g_fanPercent = 0;   // 0–100%
bool g_fanOn = false;

bool g_displayReady = false;

bool g_hostActive = false;  // the agent is in control
bool g_hostLost = false;    // it stopped sending
float g_cpuTemperature = -1;
float g_predictedTemperature = -1;
uint32_t lastCommand = 0;

uint32_t lastSensor = 0;
uint32_t lastDisplay = 0;

// ─── Fan Control ────────────────────────────────────────
void writeFanPwm(int pwm) {
#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
    ledcWrite(FAN_ENA_PIN, pwm);
#else
    ledcWrite(FAN_PWM_CHANNEL, pwm);
#endif
}

void setupFanPwm() {
#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
    ledcAttach(FAN_ENA_PIN, FAN_PWM_FREQ, FAN_PWM_RES);
#else
    ledcSetup(FAN_PWM_CHANNEL, FAN_PWM_FREQ, FAN_PWM_RES);
    ledcAttachPin(FAN_ENA_PIN, FAN_PWM_CHANNEL);
#endif
}

void setFanSpeed(int percent) {
    percent = constrain(percent, 0, 100);
    bool changed = percent != g_fanPercent;
    g_fanPercent = percent;

    int pwm = map(percent, 0, 100, 0, 255);

    if (pwm > 0) {
        digitalWrite(FAN_IN1_PIN, HIGH);
        digitalWrite(FAN_IN2_PIN, LOW);
        g_fanOn = true;
    } else {
        digitalWrite(FAN_IN1_PIN, LOW);
        digitalWrite(FAN_IN2_PIN, LOW);
        g_fanOn = false;
    }

    writeFanPwm(pwm);

    if (changed) {
        Serial.print("[Fan] Speed set to ");
        Serial.print(percent);
        Serial.println("%");
    }
}

// ─── Sensor ─────────────────────────────────────────────
void readSensor() {
    if (millis() - lastSensor < SENSOR_INTERVAL_MS) return;
    lastSensor = millis();

    float t = dht.readTemperature();
    float h = dht.readHumidity();

    if (isnan(t) || isnan(h)) {
        g_sensorValid = false;
        Serial.println("[Sensor] Error reading DHT22");
        return;
    }

    g_temperature = t;
    g_humidity = h;
    g_sensorValid = true;

    Serial.printf("[Sensor] Temp: %.1f C  Hum: %.1f%%\n", t, h);
}

// ─── Display ────────────────────────────────────────────
void updateDisplay() {
    if (!g_displayReady) return;
    if (millis() - lastDisplay < DISPLAY_INTERVAL_MS) return;
    lastDisplay = millis();

    oled.clearDisplay();

    oled.setTextSize(1);
    oled.setCursor(25, 0);
    oled.print("ESP32 MONITOR");

    oled.drawLine(0, 10, 127, 10, SSD1306_WHITE);

    // Room temperature from the DHT22
    oled.setTextSize(2);
    oled.setCursor(0, 14);
    oled.printf("%.1f", g_temperature);

    oled.setTextSize(1);
    oled.setCursor(70, 14);
    oled.print("o");

    oled.setTextSize(2);
    oled.setCursor(78, 14);
    oled.print("C");

    oled.setTextSize(1);
    oled.setCursor(0, 33);
    oled.printf("Humidity: %.1f%%", g_humidity);

    // CPU temperature now -> predicted, as reported by the agent
    oled.setCursor(0, 44);
    if (g_hostLost) {
        oled.print("NO HOST: FAIL-SAFE");
    } else if (!g_hostActive) {
        oled.print("CPU: manual mode");
    } else if (g_cpuTemperature < 0) {
        oled.print("CPU: sensor fault");
    } else if (g_predictedTemperature < 0) {
        oled.printf("CPU %.1fC  pred --", g_cpuTemperature);
    } else {
        oled.printf("CPU %.1f > %.1fC", g_cpuTemperature, g_predictedTemperature);
    }

    oled.setCursor(0, 55);
    oled.print("Fan: ");

    if (g_fanOn) {
        oled.print("ON ");
        oled.printf("%d%%", g_fanPercent);
    } else {
        oled.print("OFF");
    }

    oled.display();
}

// ─── Serial Input (agent commands and manual speed) ─────
bool isNumber(const String& text) {
    if (text.length() == 0 || text.length() > 3) return false;
    for (unsigned int i = 0; i < text.length(); i++) {
        if (!isDigit(text[i])) return false;
    }
    return true;
}

void handleSerial() {
    if (!Serial.available()) return;

    String input = Serial.readStringUntil('\n');
    input.trim();

    int percent;
    float cpu;
    float predicted;

    if (sscanf(input.c_str(), "FAN %d %f %f", &percent, &cpu, &predicted) == 3
            && percent >= 0 && percent <= 100) {
        g_hostActive = true;
        g_hostLost = false;
        lastCommand = millis();
        g_cpuTemperature = cpu;
        g_predictedTemperature = predicted;
        setFanSpeed(percent);
    } else if (isNumber(input) && input.toInt() <= 100) {
        // Manual control. The agent takes over again with its next command.
        g_hostActive = false;
        g_hostLost = false;
        setFanSpeed(input.toInt());
    } else {
        // Ignored, so that a garbled line can never stop the fan.
        Serial.println("[Error] Enter value 0–100");
    }
}

// ─── Fail-safe ──────────────────────────────────────────
void checkHost() {
    if (!g_hostActive || millis() - lastCommand < HOST_TIMEOUT_MS) return;

    g_hostActive = false;
    g_hostLost = true;
    Serial.println("[Fail-safe] No command from the agent");
    setFanSpeed(FAIL_SAFE_PERCENT);
}

// ─── Setup ──────────────────────────────────────────────
void setup() {
    Serial.begin(115200);

    pinMode(FAN_IN1_PIN, OUTPUT);
    pinMode(FAN_IN2_PIN, OUTPUT);
    setupFanPwm();
    setFanSpeed(0);  // Start OFF

    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);

    // A missing display must not stop the fan from being controlled.
    g_displayReady = oled.begin(SSD1306_SWITCHCAPVCC, OLED_I2C_ADDR);
    if (g_displayReady) {
        oled.clearDisplay();
        oled.setTextSize(1);
        oled.setTextColor(SSD1306_WHITE);
        oled.setCursor(20, 25);
        oled.print("Starting...");
        oled.display();
        delay(1500);
    } else {
        Serial.println("[OLED] Failed!");
    }

    dht.begin();

    Serial.println("=== READY ===");
    Serial.println("Enter speed (0–100)");
}

// ─── Loop ───────────────────────────────────────────────
void loop() {
    readSensor();
    updateDisplay();
    handleSerial();
    checkHost();
}
