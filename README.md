# 忘关就是开了？ / RelaxedAim

Project Zomboid Build 42 client-side aim-assist mod.

## Development build

From `java/`, run:

```powershell
.\gradlew.bat build
```

This creates and installs `42/media/java/client/RelaxedAim.jar`.

To deploy the complete local test mod (metadata plus JAR), run:

```powershell
.\gradlew.bat deployLocalMod
```

The deployment destination is `C:\Users\Administrator\Zomboid\mods\RelaxedAim\` and it preserves PZ's required B42 layout:

```text
RelaxedAim/
├─ common/mod.info
└─ 42/media/java/client/RelaxedAim.jar
```
