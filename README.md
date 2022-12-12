# vital_android

## Introduction

You can find detailed documentation at [`https://docs.tryvital.io/`].

The Vital SDK is split into three main components: `VitalClient`, `VitalHealthConnect`
and `VitalDevices`.

- `VitalClient` holds common components to both `VitalHealthConnect` and `VitalDevices`. Among other
  things, it has the network layer that allows us to send data from a device to a server.
- `VitalHealthConnect` is an abstraction over Health Connect
- `VitalDevices` is an abstraction over a set of Bluetooth devices.

## Installation

You can install the Vital SDK by adding the followings to your `build.gradle` file:

```groovy
repositories {
  ...
  maven { url 'https://jitpack.io' }
}
```

```groovy
implementation 'com.github.tryVital.vital-android:VitalClient:$vital_version'
implementation 'com.github.tryVital.vital-android:VitalHealthConnect:$vital_version'
implementation 'com.github.tryVital.vital-android:VitalDevices:$vital_version'
```

Replace `$vital_version` with the latest version of the SDK. You only need to add the dependencies
for the components you want to use.

The min version of the SDK is `21` for VitalClient but for VitalHealthConnect and VitalDevices it is
`26`.

### VitalClient installation

You have no additional steps to take to use VitalClient.

### VitalHealthConnect installation

The sdk compiles on min version `26` but to get data out of the underlying Health Connect SDK you
need to be on `28` or higher.

### VitalDevices installation

Vital Devices uses bluetooth and it requires different permissions based on your apps min version.
Here is an example of a `AndroidManifest.xml` file that uses Vital Devices:

```xml  
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation"/>
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>

    <!-- Request legacy Bluetooth permissions on older devices. -->
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30"/>
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30"/>
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30"/>
```

## Usage

### VitalClient

First you need to get an instance of `VitalClient`:

```kotlin
    val client = VitalClient(
    context = this,
    region = Region.EU,
    environment = Environment.Sandbox,
    apiKey = "sk_eu_S5LdX..." //your key from the dashboard
)
```

Now you can start using the client to send and receive data from the server.

You can read about the available methods in
the [docs](https://docs.tryvital.io/api-reference/user/create-user).

### VitalHealthConnect

Coming soon.

### VitalDevices

To interact with Vital Devices sdk you need to get an instance of the `VitalDeviceManager`:

```kotlin
val deviceManager = VitalDeviceManager.create(this)
```

Next you have to scan for one of the supported devices. You can find the list of supported devices
by calling `VitalDeviceManagerBrands.devices`.

You can search now.

```kotlin
vitalDeviceManager.search(deviceModel).collect { scannedDevice ->
    // scannedDevice is the device that was found of the type deviceModel
}
```

Depending on the type of device you are connecting to, you will have to call different methods to
connect to it.

#### Blood pressure monitor

```kotlin
vitalDeviceManager.bloodPressure(context, scannedDevice)
    .collect { bloodPressureSample ->
        // bloodPressureSample is the sample that was received from the device
    }
```

#### Glucose meter

```kotlin
vitalDeviceManager.glucoseMeter(context, scannedDevice)
    .collect { glucoseSample ->
        // glucoseSample is the sample that was received from the device
    }
```

After you have received samples depending on the type of device you might need to star scanning
again to receive the next set of samples.



