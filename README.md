# vital-android

The official Android Library for Vital API, Health Connect and Devices

## Installation

Make sure Maven Central is included in the list of repositories in your `build.gradle`.

```groovy
repositories {
  mavenCentral()
}
```

Then include our Android SDK artifacts as dependencies of your modules as needed:

```groovy
def vital_version = '4.1.1'

implementation 'io.tryvital:vital-client:$vital_version'
implementation 'io.tryvital:vital-health-connect:$vital_version'
implementation 'io.tryvital:vital-devices:$vital_version'
```


## Documentation

* [Installation](https://docs.tryvital.io/wearables/sdks/installation)
* [Authentication](https://docs.tryvital.io/wearables/sdks/authentication)
* [Core SDK](https://docs.tryvital.io/wearables/sdks/vital-core)
* [Health SDK](https://docs.tryvital.io/wearables/sdks/vital-health)
* [Devices SDK](https://docs.tryvital.io/wearables/sdks/vital-devices)

## License

vital-android is available under the AGPLv3 license. See the LICENSE file for more info. VitalDevices is under the `Adept Labs Enterprise Edition (EE) license (the “EE License”)`. Please refer to its license inside its folder.
