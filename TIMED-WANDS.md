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

Full build: `mvn -B package`. The inherited source requires private/local hook API JARs in `libs/` as specified by pom.xml; these are not distributed by this repository. A full build requires those original dependencies.

Server acceptance checks: issue a 1h wand, observe seconds decrease, move it to a chest and reopen later, restart and rejoin, and verify its original expiration persists after selling. Check expired selling/inspection, existing untimed wands, stacking modes and shulker storage. These require a Minecraft server and the hook dependencies.

Validation performed: the dependency-free duration/deadline checks passed, and the timer plus give-command classes compiled against the declared AxAPI and Spigot APIs using a minimal plugin bootstrap stand-in. The full Maven build was attempted and failed during dependency resolution because the inherited local hook JARs are absent (including CMI/CMILib, CoinsEngine, ExcellentEconomy, RoyaleEconomy, Residence, KingdomsX, IridiumSkyblock, DynamicShop and LockettePro). No runnable plugin JAR or live server verification is claimed.
