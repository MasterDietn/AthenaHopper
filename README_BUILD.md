# AthenaHopper (Gradle)

## Voraussetzungen
- Java 17
- `HytaleServer.jar` (wird NICHT commited)
- Übergabe via:
  - `-PhytaleServerJar="C:\path\to\HytaleServer.jar"` oder
  - Environment Variable `HETALE_SERVER_JAR="C:\path\to\HytaleServer.jar"`
  - (Fallback) `../../Modell/HytaleServer.jar` relativ zu diesem Projekt
- Gradle installiert (oder andere Möglichkeit, z.B. Gradle über IDE)

## Build
```powershell
cd "C:\Users\trist\Desktop\Hopper\FilterFunnelPlus"
gradle clean build -PhytaleServerJar="C:\Users\trist\Desktop\Modell\HytaleServer.jar"
```

Die Mod-Jar-Datei landet dann unter:
- `build\libs\AthenaHopper.jar`

## In Hytale testen
```powershell
Copy-Item -Force "build\libs\AthenaHopper.jar" "C:\Users\trist\AppData\Roaming\Hytale\UserData\Mods\"
```

