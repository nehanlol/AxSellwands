# Timed sellwands

Based on vihaan69/AxSellwands, retaining its shulker-content selling support.

`/axsellwands give <player> <sellwand> <time> [amount]`

Examples:
- `/axsellwands give Steve normal-sellwand 24h 1`
- `/axsellwands give Steve unlimited-sellwand 2d 3`

Time is required, accepts positive whole hours (`h`) or days (`d`), and starts when issued. Amount defaults to 1 (maximum 64). Existing wands without a deadline remain usable as before. An unlimited-use wand still expires at its deadline.

Deadlines are saved in the item's persistent data. Time continues in containers, dropped items, unloaded chunks, while offline and through restarts/server downtime. Expired wands remain labeled Expired and cannot sell or inspect containers. Timed wands cannot be combined with the plugin's use-merging feature, preventing deadline resets.

One shared task dispatches once per second to online players' owning scheduler. It checks their inventories, cursor and currently open inventory; only timed items whose displayed text changes receive metadata updates. Closed containers, offline players and unloaded chunks need no scanning, tasks or database writes. Stored lore catches up when viewed. On Folia, a container is refreshed only when its region is owned by the viewer's task. Cost scales with online players and their visible inventory slots; no per-wand tasks or world scans.

The appended countdown line is customizable with `timer-lore` (`%time%`) and `timer-expired` in lang.yml. `invalid-time` and `sellwand-expired` customize feedback. Existing custom wand lore needs no changes. Deadlines follow the server's wall clock.

## Verification

Dependency-free Java 21 regression checks (from the repository root):

```powershell
javac -d target/timer-tests src/main/java/com/artillexstudios/axsellwands/timing/WandDuration.java src/test/java/com/artillexstudios/axsellwands/timing/WandDurationTest.java
java -cp target/timer-tests com.artillexstudios.axsellwands.timing.WandDurationTest
```

Release build (Python 3 and JDK 21): `python scripts/build-release.py`.

The build downloads the official AxSellwands 1.15.0 release and compile dependencies, verifies their pinned SHA-256 hashes, and recompiles every core plugin class from this fork. It retains the official release's bundled libraries and unchanged optional integration classes, avoiding the private/local hook API JARs required by the inherited Maven configuration. The integration sources were compared against upstream tag `1.15.0` and are identical. The script refuses to build if those integration sources change. It packages current resources, runs duration/deadline regression checks, verifies JAR integrity, and writes a SHA-256 checksum. Spigot and Adventure compile APIs are not added to the plugin JAR.

Output: `target/AxSellwands-1.15.0-timed.1.jar` and its `.jar.sha256` file. The packaged plugin version matches pom.xml. The original `mvn -B package` path still requires the local hook API JARs declared in pom.xml.

Validation performed: all core plugin classes compile against the actual bundled APIs, the duration/deadline regression checks pass, ZIP integrity passes, the plugin descriptor has the release version, and reused integration/library bytes match the verified official release. Live Minecraft server testing has not been performed.

Server acceptance checks: issue a 1h wand, observe seconds decrease, move it to a chest and reopen later, restart and rejoin, and verify its original expiration persists after selling. Check expired selling/inspection, existing untimed wands, stacking modes and shulker storage.
