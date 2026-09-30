# MosqueDND Knowledge Graph Schema

## Node Types

- **Mosque**: Represents a mosque with properties like `name`, `latitude`, `longitude`, and `radius`.
- **PrayerTime**: Represents a prayer time with properties like `prayerType`, `time`, and `offset`.
- **DNDWindow**: Represents a DND window with properties like `startTime`, `endTime`, and `duration`.
- **Component**: Represents a code component with properties like `name`, `filePath`, and `dependsOn`.
- **SharedPreference**: Represents a SharedPreferences key with properties like `key`, `defaultValue`, and `description`.

## Edge Types

- **NEARBY**: Connects a Mosque to nearby Mosques within a certain distance.
- **TRIGGERS**: Connects a PrayerTime to a DNDWindow that it triggers.
- **DEPENDS_ON**: Connects a Component to another Component it depends on.
- **READS_PREF**: Connects a Component to a SharedPreference it reads.
- **WRITES_PREF**: Connects a Component to a SharedPreference it writes.

## Query Examples

### Find all mosques within 500 meters of a specific mosque

```kotlin
query(repository) {
    node<Mosque> {
        where { id == "mosque-123" }
    }
    edge<NEARBY> {
        where { distanceM < 500 }
    }
}
```

### Find all components that depend on AlarmScheduler

```kotlin
query(repository) {
    node<Component> {
        where { dependsOn == "AlarmScheduler" }
    }
}
```

### Find all prayer times with an offset greater than 30 minutes

```kotlin
query(repository) {
    node<PrayerTime> {
        where { offset > 30 }
    }
}
```

### Find all DND windows triggered by Fajr prayer

```kotlin
query(repository) {
    node<PrayerTime> {
        where { prayerType == PrayerType.FAJR }
    }
    edge<TRIGGERS> {
        where { true }
    }
}
```

### Find all components that read or write the auto-DND preference

```kotlin
query(repository) {
    node<SharedPreference> {
        where { key == "pref_auto_dnd_enabled" }
    }
    edge<READS_PREF> {
        where { true }
    }
    edge<WRITES_PREF> {
        where { true }
    }
}
```
