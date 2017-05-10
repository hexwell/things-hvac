/* ===Commands Table===
 * Legend:
 *    XX = int, 2 digits
 *    Y = int, 1 digit (:hvacStatus) { 0: off
 *                                     1: heating
 *                                     2: cooling
 *                                     3: ventilating
 *                                     4: 2 + 3
 *                                   }
 *    C = char, ASCII/Raw, not ";"
 *    I = int, 1 digit (:bool)       { 0: false -> wifi disconnected 
 *                                     1: true  -> wifi connected
 *                                   }
 *    * = char, ASCII/Raw
 * 
 * Commands:
 *     Lenght | IN | OUT |                Command                | Description
 *    ======================================================================================================================
 *      4 chr |    | OUT | "cXX;"                                | New current temp
 *      4 chr | IN | OUT | "wXX;"                                | New wanted temp
 *      3 chr | IN |     | "hY;"                                 | New hvac status
 *      2 chr |    | OUT | "u;"                                  | Request updated data
 *     34 chr | IN |     | "dCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC;"  | Display a message (16 chars top row, 16 chars bottom row)
 *      2 chr | IN | OUT | "s;"                                  | Display the system startup message
 *      3 chr | IN |     | "iI;"                                 | New WiFi Status
 */

#include <LiquidCrystal.h>
#include <SimpleTimer.h>

#define BAUD_RATE 115200                //baud
const int SYNC_RATE = 30000;            //millis
const int DISPLAY_REFRESH_RATE = 50;    //millis
const int MAX_CMD_LEN = 34;             //chars
const int MSG_SCREEN_TIME = 2000;       //millis
const int BUTTON_POLL_RATE = 200;       //millis
const int MAX_TEMP = 40;                //degrees (°C)
const int MENUS = 3;

const char TERMINATOR = ';';

const char WELCOME_MESSAGE[] = "ThingsHVAC";
const char STARTUP_MESSAGE[] = " System Started";

const char HOME_ROW_0_FORMAT[] = "Current temp:%02d";
const char HOME_ROW_1_FORMAT[] = "Desired temp:%02d";
const char HOME_ROW_2_FORMAT[] = "Status:";
const char HOME_ROW_4_FORMAT[] = "WIFI:";
const char **HOME_ROW_5_FORMAT = (char* []){"Disconnected", "Connected"};

LiquidCrystal lcd(6, 7, 2, 3, 4, 5);
SimpleTimer syncTimer;
SimpleTimer displayRefreshTimer;
SimpleTimer pollButtonTimer;

int current = 18;
int wanted;
int hvacStatus;
bool wifiConnected;

char incomingCmd[MAX_CMD_LEN + 1];
int cmdIndex;

bool selectedRow0 = true;
int currentMenu;

// up, down, right, left, select
bool keys[5];
int buttonVal;

void setup() {
  Serial.begin(BAUD_RATE);
  
  syncTimer.setInterval(SYNC_RATE, sync);
  displayRefreshTimer.setInterval(DISPLAY_REFRESH_RATE, displayRefresh);
  pollButtonTimer.setInterval(BUTTON_POLL_RATE, pollButton);
  
  lcd.begin(16, 2);
  lcd.print(WELCOME_MESSAGE);
  delay(MSG_SCREEN_TIME);
}

void loop() {
  syncTimer.run();
  displayRefreshTimer.run();
  pollButtonTimer.run();
}

void serialEvent(){
  while (Serial.available()) {
    char inChar = (char)Serial.read();
    if (inChar == TERMINATOR) {
      incomingCmd[cmdIndex] = 0;
      interpret();
      cmdIndex = 0;
    }
    else{
      incomingCmd[cmdIndex] = inChar;
      cmdIndex++;
    }
    if(cmdIndex >= MAX_CMD_LEN){
      cmdIndex = 0;
    }
  }
}

void interpret(){ 
  switch(incomingCmd[0]){
    case 'w':
      if(incomingCmd[1] >= '0' && incomingCmd[1] <= '3' &&
      incomingCmd[2] >= '0' && incomingCmd[2] <= '9'
      && incomingCmd[3] == 0){
        incomingCmd[0] = ' ';
        wanted = atoi(incomingCmd);
      }
      break;
      
    case 'h':
      if(incomingCmd[1] >= '0' && incomingCmd[1] <= '4' &&
      incomingCmd[2] == 0){
        hvacStatus = incomingCmd[1] - '0';
      }
      break;
      
    case 'd':
      {
        char row0[16];
        char row1[16];
        
        memcpy(row0, &incomingCmd[1], 16);
        memcpy(row1, &incomingCmd[17], 16);
        
        lcd.clear();
        lcd.print(row0);
        lcd.setCursor(0, 1);
        lcd.print(row1);
  
        delay(MSG_SCREEN_TIME);
        break;
      }
      
    case 's':
      {
        lcd.clear();
        lcd.print(STARTUP_MESSAGE);

        delay(MSG_SCREEN_TIME);
        break;
      }
      
    case 'i':
      {
        if(incomingCmd[1] >= '0' && incomingCmd[1] <= '1' &&
           incomingCmd[2] == 0){
          wifiConnected = incomingCmd[1] - '0';
        }
      break;
      }
  }
}

void sync(){
  Serial.write("u;");
}

void sendData(bool sendCurrent){
  char data[5];
  char format[] = " %02d;";
  
  if(sendCurrent){
    sprintf(data, format, current);
    data[0] = 'c';
  }else{
    sprintf(data, format, wanted);
    data[0] = 'w';
  }

  Serial.write(data);
}

void displayRefresh(){
  char homeRow0[16];
  char homeRow1[16];
  lcd.clear();

  switch(currentMenu){
    case 0:
      sprintf(homeRow0, HOME_ROW_0_FORMAT, current);
      sprintf(homeRow1, HOME_ROW_1_FORMAT, wanted);
    
      if(selectedRow0){
        lcd.print(">");
      }else{
        lcd.print(" ");
      }
      lcd.print(homeRow0);
      lcd.setCursor(0, 1);
      if(!selectedRow0){
        lcd.print(">");
      }else{
        lcd.print(" ");
      }
      lcd.print(homeRow1);
      break;
    case 1:
      lcd.print(HOME_ROW_2_FORMAT);
      switch(hvacStatus){
        case 0:
          lcd.setCursor(0, 1);
          lcd.print("off");
          break;
        case 1:
          lcd.setCursor(0, 1);
          lcd.print("heating");
          break;
        case 2:
          lcd.setCursor(0, 1);
          lcd.print("cooling");
          break;
        case 3:
          lcd.setCursor(0, 1);
          lcd.print("ventilating");
          break;
        case 4:
          lcd.print("cooling &");
          lcd.setCursor(0, 1);
          lcd.print("ventilating");
          break;
      }
      break;
    case 2:
      lcd.print(HOME_ROW_4_FORMAT);
      lcd.setCursor(0, 1);
      lcd.print(HOME_ROW_5_FORMAT[(int)wifiConnected]);
      break;
  }
}

void pollButton(){
  buttonVal = analogRead(A0);

  keys[0] = buttonVal > 100 && buttonVal < 200;
  keys[1] = buttonVal > 300 && buttonVal < 400;
  keys[2] = buttonVal < 50;
  keys[3] = buttonVal > 500 && buttonVal < 600;
  keys[4] = buttonVal > 700 && buttonVal < 800;

  switch(currentMenu){
    case 0:
    {
      if(keys[0]){
        if(selectedRow0){
          if(current < MAX_TEMP){
            current++;
            sendData(true);
          }
        }else{
          if(wanted < MAX_TEMP){
            wanted++;
            sendData(false);
          }
        }
      }
      if(keys[1]){
        if(selectedRow0){
          if(current > 0){
            current--;
            sendData(true);
          }
        }else{
          if(wanted > 0){
            wanted--;
            sendData(false);
          }
        
        }
      }
      if(keys[2]){
        if(currentMenu < MENUS - 1){
          currentMenu++;
        }
      }
      if(keys[3]){
        if(currentMenu > 0){
          currentMenu--;
        }
      }
      if(keys[4]){
        selectedRow0 = !selectedRow0;
      }
      break;
    }
    case 1:
    {
      if(keys[2]){
        if(currentMenu < MENUS - 1){
          currentMenu++;
        }
      }
      if(keys[3]){
        if(currentMenu > 0){
          currentMenu--;
        }
      }
      break;
    }
    case 2:
    {
      if(keys[2]){
        if(currentMenu < MENUS - 1){
          currentMenu++;
        }
      }
      if(keys[3]){
        if(currentMenu > 0){
          currentMenu--;
        }
      }
      break;
    }
  }
}

