# Bluetooth Communication App

This Android application enables communication between Bluetooth devices. It allows discovering, connecting, and exchanging messages over Bluetooth, leveraging Jetpack Compose for the user interface.

## Features

- Discover nearby Bluetooth devices.
- Connect to a selected device.
- Send and receive text messages between connected devices.
- Request and handle Bluetooth permissions dynamically.

## Technologies

- **Kotlin**: The main language for the app.
- **Jetpack Compose**: For building declarative and reactive UIs.
- **Bluetooth API**: To manage Bluetooth connections.
- **Coroutines**: For asynchronous operations (discovery, connection, message handling).
- **StateFlow**: To manage UI state.

## Permissions

The app requires the following permissions to function properly:

- `BLUETOOTH_CONNECT`
- `BLUETOOTH_SCAN`
- `BLUETOOTH_ADVERTISE`

These permissions are requested at runtime and are critical for Bluetooth functionality.

## How It Works

### User Interface

- The UI is built using Jetpack Compose. 
- The main activity (`MainActivity`) checks and requests Bluetooth permissions.
- If permissions are granted, the UI displays a list of available devices.
- Once connected to a device, the app allows sending and receiving messages.

### ViewModel

The `BluetoothViewModel` manages:
- Discovery of nearby Bluetooth devices.
- Connecting to a selected device.
- Sending and receiving messages through the `BluetoothManageConnection` class.

### Bluetooth Connection

The `BluetoothManageConnection` class handles:
- Receiving messages using input streams.
- Sending messages using output streams.
- Managing the Bluetooth socket connection.

## Setup

1. Clone this repository.
2. Open the project in Android Studio.
3. Build and run the app on a device that supports Bluetooth.
