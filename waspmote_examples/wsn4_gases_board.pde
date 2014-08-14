#include <WaspXBee802.h>
#include <WaspFrame.h>
#include <WaspSensorGas_v20.h>

#define GAIN_2A      1      //GAIN of the sensor stage
#define RESISTOR_2A  2      //LOAD RESISTOR of the sensor stage

#define GAIN_4A      1
#define RESISTOR_4A  1

//Variable to store the read value
unsigned long uptime_sleep_delay;

int batteryVal;

float temperatureVal;

int x_acc;
int y_acc;
int z_acc;

float socket2AVal;
int socket2ACon[] = {
  1, 3, 30};
float socket2AOut[] = {
  0.8, 0.7, 0.3};

float socket4AVal;
int socket4ACon[] = {
  300, 700, 10000};
float socket4AOut[] = {
  6, 4, 1};

float humidityVal;

int state = 0;

//Pointer to an XBee packet structure 
packetXBee* packet; 

// Destination MAC address
char* MAC_ADDRESS="0013A200406FB421";
//char* MAC_ADDRESS="0013A20040B4DAFE";

char *ftoa(char *a, double f, int precision)
{
  long p[] = {
    0, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000  };

  char *ret = a;
  long heiltal = (long)f;
  itoa(heiltal, a, 10);
  while (*a != '\0') a++;
  *a++ = '.';
  long desimal = abs((long)((f - heiltal) * p[precision]));
  itoa(desimal, a, 10);
  return ret;
}

void setup()
{
  Utils.setLED(LED0, LED_ON);
  // Turn on the USB and print a start message
  USB.ON();
  USB.println(F("Start"));
  delay(100);

  // Turn on the RTC
  RTC.ON();
  RTC.setTime("12:07:17:02:17:25:00");

  // init XBee
  xbee802.ON();

  // Turn on accelerometer
  ACC.ON();

  // Turn on the sensor board
  SensorGasv20.ON();

  // Configure the 2A and 4A sensor sockets
  SensorGasv20.configureSensor(SENS_SOCKET2A, GAIN_2A, RESISTOR_2A);
  SensorGasv20.configureSensor(SENS_SOCKET4A, GAIN_4A, RESISTOR_4A);

  // Turn on the sensor on sockets 2A and 4A and wait for stabilization and
  // sensor response time
  SensorGasv20.setSensorMode(SENS_ON, SENS_SOCKET2A);
  SensorGasv20.setSensorMode(SENS_ON, SENS_SOCKET4A);

  USB.println(F("Warming up ..."));  
  delay(30000);
  
  uptime_sleep_delay = 0;
}

void loop()
{  
  Utils.setLED(LED1, LED_ON);

  // Read the sensor
  batteryVal = PWR.getBatteryLevel();

  temperatureVal = (float) RTC.getTemperature();

  x_acc = ACC.getX();
  y_acc = ACC.getY();
  z_acc = ACC.getZ();

  socket2AVal = SensorGasv20.readValue(SENS_SOCKET2A);
  socket2AVal = SensorGasv20.calculateResistance(SENS_SOCKET2A, socket2AVal, GAIN_2A, RESISTOR_2A);
  socket2AVal = SensorGasv20.calculateConcentration(socket2ACon, socket2AOut, socket2AVal / 10.0);

  socket4AVal = SensorGasv20.readValue(SENS_SOCKET4A);
  socket4AVal = SensorGasv20.calculateResistance(SENS_SOCKET4A, socket4AVal, GAIN_4A, RESISTOR_4A);
  socket4AVal = SensorGasv20.calculateConcentration(socket4ACon, socket4AOut, socket4AVal);

  humidityVal = SensorGasv20.readValue(SENS_HUMIDITY);

  ///////////////////////////////////////////
  // 1. Assemble packet
  ///////////////////////////////////////////  
  char SN04UPT01[20];
  sprintf(SN04UPT01, "SN04UPT01:%lu", (millis()/1000) + uptime_sleep_delay);

  char SN04BAT01[20];
  sprintf(SN04BAT01, "SN04BAT01:%d", batteryVal);

  char SN04TEM01[20];
  strcpy(SN04TEM01, "SN04TEM01:");
  ftoa(SN04TEM01 + 10, temperatureVal, 1);

  char SN04ACX01[20];
  strcpy(SN04ACX01, "SN04ACX01:");
  ftoa(SN04ACX01 + 10, (float)x_acc/100, 1);

  char SN04ACY01[20];
  strcpy(SN04ACY01, "SN04ACY01:");
  ftoa(SN04ACY01 + 10, (float)y_acc/100, 1);

  char SN04ACZ01[20];
  strcpy(SN04ACZ01, "SN04ACZ01:");
  ftoa(SN04ACZ01 + 10, (float)z_acc/100, 1);

  char SN04APO01[20];
  strcpy(SN04APO01, "SN04APO01:");
  ftoa(SN04APO01 + 10, socket2AVal, 1);

  char SN04LPG01[20];
  strcpy(SN04LPG01, "SN04LPG01:");
  ftoa(SN04LPG01 + 10, socket4AVal, 1);

  char SN04HUM01[20];
  strcpy(SN04HUM01, "SN04HUM01:");
  ftoa(SN04HUM01 + 10, humidityVal, 1);

  char payload[300];
  if (state == 0) {
    sprintf(payload, "<=>#%s#%s#%s#%s#%s#%s#", SN04UPT01, SN04BAT01, SN04TEM01, SN04ACX01, SN04ACY01, SN04ACZ01); //, SN04APO01, SN04LPG01, SN04HUM01);
    state = 1;
  } 
  else if (state == 1) {
    sprintf(payload, "<=>#%s#%s#%s#", SN04APO01, SN04LPG01, SN04HUM01);
    state = 0;
  }

  USB.println(payload);

  ///////////////////////////////////////////
  // 2. Send packet
  ///////////////////////////////////////////  

  // set parameters to packet:
  packet=(packetXBee*) calloc(1,sizeof(packetXBee)); // Memory allocation
  packet->mode=UNICAST; // Choose transmission mode: UNICAST or BROADCAST

    // set destination XBee parameters to packet
  xbee802.setDestinationParams(packet, MAC_ADDRESS, (uint8_t*)payload, strlen(payload), MAC_TYPE);   

  xbee802.setRetries(6);

  // send XBee packet
  xbee802.sendXBee(packet);

  // check TX flag
  if(xbee802.error_TX == 0)
  {
    USB.println(F("Data sent"));
  }
  else 
  {
    USB.println(F("Error sending data"));
  }

  // free variables
  free(packet);
  packet=NULL;

  Utils.setLED(LED1, LED_OFF);

  if (state == 0) {
    // Go to sleep disconnecting all the switches and modules
    // After 2 minutes, Waspmote wakes up thanks to the RTC Alarm
    Utils.setLED(LED0, LED_OFF);
    PWR.deepSleep("00:00:02:00",RTC_OFFSET,RTC_ALM1_MODE1,ALL_OFF);

    if( intFlag & RTC_INT )
    {
      intFlag &= ~(RTC_INT);
    }
    uptime_sleep_delay += 120;
    
    Utils.setLED(LED0, LED_ON);
    USB.ON();
    USB.println(F("Wake up"));  
    RTC.ON();
    SensorGasv20.ON();
    SensorGasv20.setSensorMode(SENS_ON, SENS_SOCKET2A);
    SensorGasv20.setSensorMode(SENS_ON, SENS_SOCKET4A);
    xbee802.ON();
    ACC.ON();
    USB.println(F("Warming up ..."));
    delay(25000);
  } 
  else {
    delay(5000);
  }
}


