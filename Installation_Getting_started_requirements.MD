# BeerGuard - Installation/Getting started/Hardware softare requirements

## Hardware/software requirements:

* MetaWear C board: https://mbientlab.com/product/metawearc/

* An Android phone running at least Android 4.3

* A computer capable of running Android Studio

* Android Studio 

* Android API 26/Android SDK 8.0

* Java 1.8

## Installation
This app is designed to be installed via Android Studio 

* Ensure you have a Bluetooth-capable Android phone running at least Android 4.3

* Ensure you have a MetaWear C board

* Ensure you have Developer Options and USB debugging on the phone

* Ensure you have Android Studio installed, with the requisite SDK (API 26 was used for this project)

* Ensure you have Java 1.8 and Android SDK 8.0 with API 26 installed and all that jazz

* Clone this repo

* Open the project in Android Studio

* Set Source and Target Compatibility in Android Studio -> File -> Project Structure to 1.8

* Connect your phone, allow MTP, etc

* Enable Bluetooth on the phone

* IMPORTANT: CHANGE THE GLOBAL "mac_addr" VARIABLE in MyActivity.java TO YOUR OWN METAWEAR MAC ADDRESS

* Run the app, accept any privileges because why not. This should install the app onto your phone

* (Optional) disable Developer Options as there is no good reason to have them anymore

You may now run the app independently from Android Studio

## Getting started

* If everything works, then you should see the app's one and only activity

* The app will warn you if you forgot to enable bluetooth. So enable bluetooth

* However, if you get an alert about a lack of bluetooth connection, then simply shut down the app, remove and then re-insert the battery on your metawear board. For some reason that always works

* If it works, then feel free to press "Start" and see the ambient temperature being streamed in real time, or throw the board across the room to trigger an alarm, or put it in the fridge next to some beer as intended and wait for it to cool and then receive a slightly more pleasant alarm

* Press "stop" to stop the cooling cycle once the desired temperature is reached/to stop the alarms from going off

* There is no guarantee that the beer will be cold enough. But it reports the temperature of the inside of your fridge really well, so that is a plus. At least the anti-theft function works well. Unless you move the sensor out from the fridge r e a l l y  s l o w l y. This is because the MetaWear C board isn't that sensitive

* Press "exit" to quit the app. 





