int relay = 12;
boolean needAck = false;
unsigned long lastseen = 0;

void relaySwitch(boolean on) {
  if (on) {
    digitalWrite(relay, HIGH);
  } else {
    digitalWrite(relay, LOW);
  }
}

void setup() {
  Serial.begin(9600);
  pinMode(relay, OUTPUT);
  delay(100);
}

void loop () {
  String data = "";

  delay(2000);
  unsigned long now = millis();
  while (Serial.available() > 0) {
    char c = Serial.read();
    data += c;
  }
  if (data.startsWith("relay")) {
    String verb = data.substring(6);
    if (verb.compareTo("on") == 0) {
      relaySwitch(true);
      Serial.write("ack");
      needAck = true;
      Serial.flush();
      lastseen = now;
    } else if (verb.compareTo("off") == 0) {
      relaySwitch(false);
      Serial.write("ack");
      needAck = true;
      Serial.flush();
    }
  } else if (data.compareTo("acked") == 0) {
      needAck = false;
  }
  if (needAck) {
      Serial.write("ack");
  }

  if(lastseen - millis() > 0) lastseen=0;

  if (now - lastseen > 1000L*60*40) {
      relaySwitch(false);
      needAck = false;
  }
}

