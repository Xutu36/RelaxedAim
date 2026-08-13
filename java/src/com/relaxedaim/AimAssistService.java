package com.relaxedaim;

import java.util.ArrayList;
import java.util.List;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.input.Mouse;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoCell;
import zombie.iso.IsoUtils;
import zombie.iso.IsoWorld;

/**
 * Phase 2: read-only aim-state collector and candidate zombie detector.
 *
 * <p>This service runs once per frame but only performs the relatively expensive
 * zombie scan every {@link #UPDATE_INTERVAL_FRAMES} frames. It never modifies
 * aiming, shooting, damage or movement. Its only job is to answer:
 * <ul>
 *   <li>Is the local player currently aiming with a ranged weapon?</li>
 *   <li>Which zombies near the mouse are valid targets?</li>
 *   <li>Which one of them is the best candidate right now?</li>
 * </ul>
 */
public final class AimAssistService {

    /** Scan for targets every N frames to keep the per-frame cost low in hordes. */
    private static final int UPDATE_INTERVAL_FRAMES = 5;

    /** Do not scan zombies further away than this many tiles. */
    private static final float MAX_WORLD_SCAN_RADIUS_TILES = 20.0f;

    /** Zombies must be on roughly the same floor as the player. */
    private static final float FLOOR_TOLERANCE = 0.5f;

    /** Only consider zombies within this many screen pixels of the mouse cursor. */
    private static final float MOUSE_SEARCH_RADIUS_PIXELS = 500.0f;

    /** World distance has a lower weight than mouse distance when scoring candidates. */
    private static final float WORLD_DISTANCE_WEIGHT = 5.0f;

    /** Frames elapsed since the mod was loaded. Used for throttling, not for game timing. */
    private static int frameCounter = 0;

    /** Last frame at which we printed a debug log line. */
    private static int lastLogFrame = 0;

    /** Minimum number of frames between two debug log lines. */
    private static final int LOG_INTERVAL_FRAMES = 120;

    // -------------------------------------------------------------------------
    // Debug state exposed to the overlay. These are updated every scan frame.
    // -------------------------------------------------------------------------
    public static boolean debugIsAiming = false;
    public static boolean debugHasRangedWeapon = false;
    public static String debugWeaponName = "-";
    public static float debugWeaponRange = 0.0f;
    public static int debugMouseX = 0;
    public static int debugMouseY = 0;
    public static float debugPlayerZ = 0.0f;
    public static int debugTotalZombies = 0;
    public static int debugRejectedFloor = 0;
    public static int debugRejectedRange = 0;
    public static int debugRejectedVisibility = 0;
    public static int debugRejectedMouse = 0;
    public static int debugCandidateCount = 0;
    public static String debugNearestZombie = "-";
    public static float debugNearestDistance = Float.MAX_VALUE;
    public static float debugNearestScreenX = 0.0f;
    public static float debugNearestScreenY = 0.0f;

    private static boolean lastActive = false;
    private static int lastCandidateCount = 0;

    private AimAssistService() {
    }

    /** Called from the IngameState UI render patch once per frame. */
    public static void update() {
        frameCounter++;

        // Reset per-frame debug values that are always refreshed.
        debugMouseX = Mouse.getX();
        debugMouseY = Mouse.getY();
        debugIsAiming = false;
        debugHasRangedWeapon = false;
        debugWeaponName = "-";
        debugWeaponRange = 0.0f;

        if (!IsoPlayer.hasInstance()) {
            debugLog(false, 0);
            return;
        }

        final IsoPlayer player = IsoPlayer.getInstance();
        debugPlayerZ = player.getZ();
        debugIsAiming = player.isAiming();

        final HandWeapon weapon = getActiveWeapon(player);
        if (weapon != null) {
            debugHasRangedWeapon = weapon.isRanged();
            debugWeaponName = weapon.getName() != null ? weapon.getName() : "?";
            debugWeaponRange = weapon.getMaxRange();
        }

        if (player == null || !player.isAiming()) {
            debugLog(false, 0);
            return;
        }

        if (weapon == null || !weapon.isRanged()) {
            debugLog(false, 0);
            return;
        }

        if (frameCounter % UPDATE_INTERVAL_FRAMES == 0) {
            final List<IsoZombie> candidates = findCandidates(player, weapon);
            final IsoZombie best = pickBestCandidate(candidates, player);
            debugCandidateCount = candidates.size();
            if (best != null) {
                final float dist = player.DistTo(best);
                debugLog(true, candidates.size(), best, dist);
                return;
            }
        }

        debugLog(true, debugCandidateCount);
    }

    /** Returns the ranged weapon currently held by the player, or {@code null}. */
    private static HandWeapon getActiveWeapon(final IsoPlayer player) {
        InventoryItem item = player.getPrimaryHandItem();
        if (item instanceof HandWeapon) {
            return (HandWeapon) item;
        }
        item = player.getSecondaryHandItem();
        if (item instanceof HandWeapon) {
            return (HandWeapon) item;
        }
        return player.getUseHandWeapon();
    }

    /** Gathers all zombies that pass the Phase 2 filters. */
    private static List<IsoZombie> findCandidates(final IsoPlayer player, final HandWeapon weapon) {
        final List<IsoZombie> candidates = new ArrayList<>();

        // Reset filter counters each scan.
        debugTotalZombies = 0;
        debugRejectedFloor = 0;
        debugRejectedRange = 0;
        debugRejectedVisibility = 0;
        debugRejectedMouse = 0;
        debugNearestZombie = "-";
        debugNearestDistance = Float.MAX_VALUE;
        debugNearestScreenX = 0.0f;
        debugNearestScreenY = 0.0f;

        if (IsoWorld.instance == null) {
            return candidates;
        }
        final IsoCell cell = IsoWorld.instance.currentCell;
        if (cell == null) {
            return candidates;
        }

        final ArrayList<IsoZombie> zombies = cell.getZombieList();
        if (zombies == null || zombies.isEmpty()) {
            return candidates;
        }

        final int playerIndex = IsoPlayer.getPlayerIndex();
        final float playerZ = player.getZ();
        float maxRange = weapon.getMaxRange();
        if (maxRange <= 0.0f || maxRange > MAX_WORLD_SCAN_RADIUS_TILES) {
            maxRange = MAX_WORLD_SCAN_RADIUS_TILES;
        }

        final int mouseX = Mouse.getX();
        final int mouseY = Mouse.getY();

        for (final IsoZombie zombie : zombies) {
            if (zombie == null || zombie.isDead() || !zombie.isAlive()) {
                continue;
            }

            debugTotalZombies++;

            final float dist = player.DistTo(zombie);
            if (dist < debugNearestDistance) {
                debugNearestDistance = dist;
                debugNearestZombie = zombie.toString();
                debugNearestScreenX = zombie.getScreenX();
                debugNearestScreenY = zombie.getScreenY();
            }

            if (Math.abs(zombie.getZ() - playerZ) > FLOOR_TOLERANCE) {
                debugRejectedFloor++;
                continue;
            }
            if (dist > maxRange) {
                debugRejectedRange++;
                continue;
            }
            if (zombie.getSquare() != null && !zombie.getSquare().getCanSee(playerIndex)) {
                debugRejectedVisibility++;
                continue;
            }

            final float sx = zombie.getScreenX();
            final float sy = zombie.getScreenY();
            final float mouseDist = IsoUtils.DistanceTo(mouseX, mouseY, sx, sy);
            if (mouseDist > MOUSE_SEARCH_RADIUS_PIXELS) {
                debugRejectedMouse++;
                continue;
            }

            candidates.add(zombie);
        }

        return candidates;
    }

    /** Picks the candidate with the lowest combined screen + weighted world distance. */
    private static IsoZombie pickBestCandidate(final List<IsoZombie> candidates, final IsoPlayer player) {
        IsoZombie best = null;
        float bestScore = Float.MAX_VALUE;

        final int mouseX = Mouse.getX();
        final int mouseY = Mouse.getY();

        for (final IsoZombie zombie : candidates) {
            final float screenDist = IsoUtils.DistanceTo(mouseX, mouseY, zombie.getScreenX(), zombie.getScreenY());
            final float worldDist = player.DistTo(zombie);
            final float score = screenDist + worldDist * WORLD_DISTANCE_WEIGHT;
            if (score < bestScore) {
                bestScore = score;
                best = zombie;
            }
        }

        return best;
    }

    /** Throttled logger that only prints when the active/candidate state changes. */
    private static void debugLog(boolean active, int candidateCount) {
        if (active == lastActive && candidateCount == lastCandidateCount) {
            return;
        }
        if (frameCounter - lastLogFrame < LOG_INTERVAL_FRAMES) {
            return;
        }
        lastActive = active;
        lastCandidateCount = candidateCount;
        lastLogFrame = frameCounter;
        System.out.println("[RelaxedAim] Phase2 active=" + active + ", candidates=" + candidateCount);
    }

    /** Throttled logger that includes the currently selected best target. */
    private static void debugLog(boolean active, int candidateCount, IsoZombie best, float distance) {
        if (active == lastActive && candidateCount == lastCandidateCount) {
            return;
        }
        if (frameCounter - lastLogFrame < LOG_INTERVAL_FRAMES) {
            return;
        }
        lastActive = active;
        lastCandidateCount = candidateCount;
        lastLogFrame = frameCounter;
        System.out.println("[RelaxedAim] Phase2 active=" + active
                + ", candidates=" + candidateCount
                + ", best=" + best
                + ", distance=" + distance);
    }
}
