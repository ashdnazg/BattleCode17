package johnny4_seeding;

import battlecode.common.*;
import johnny4_seeding.*;
import johnny4_seeding.Grid;
import johnny4_seeding.Util;

import static johnny4_seeding.Util.rand;

public class SimpleHexagonalClusters extends Grid {

    final static int P_X = 4;
    final static int P_Y = 2;
    final static boolean[][] PATTERN = {{true, false}, {false, false}, {false, true}, {false, false}};
    final static int[][] PATTERN_SKIP = {{2, 1}, {10000, 10000}, {1, 2}, {10000, 10000}};
    final static int[][] PATTERN_SKIP_INVERSE = {{1, 2}, {1, 1}, {2, 1}, {1, 1}};
    final static float SPACING = 2.12f;
    final float senseRadius;

    public SimpleHexagonalClusters(RobotController rc) {
        super(rc);
        senseRadius = rc.getType().sensorRadius;
    }

    @Override
    public MapLocation getNearestWalkableLocation(MapLocation myLocation) {
        MapLocation best = null;
        boolean foundBest = false;
        MapLocation unitloc = rc.getLocation();
        float bestDist = 1000000;
        float dist;
        boolean canSense;
        try {
            float radius = 5;
            int xmax = (int) ((myLocation.x + radius) / SPACING);
            int ymax = (int) ((myLocation.y + radius) / SPACING);
            int ymin = Math.max(0, (int) ((myLocation.y - radius) / SPACING));
            int gx, gy, i;
            float gx2, gxSpacing;
            int[] PATTERN_SKIP_INVERSE_X;
            MapLocation realLocation = new MapLocation(0,0);
            MapLocation checkLocation = new MapLocation(0,0);
            Direction dir = new Direction(0);
            for (gx = (int) ((myLocation.x - radius) / SPACING); gx < xmax; gx++) {
                gy = ymin;
                PATTERN_SKIP_INVERSE_X = PATTERN_SKIP_INVERSE[gx % P_X];
                gy += (PATTERN[gx % P_X][gy % P_Y]) ? PATTERN_SKIP_INVERSE_X[gy % P_Y] : 0;
                gx2 = (gx % 2) * 0.5f;
                gxSpacing = gx * SPACING;
                for (; gy < ymax; gy += PATTERN_SKIP_INVERSE_X[gy % P_Y]) {
                    //if (Util.DEBUG) System.out.println("f " + Clock.getBytecodeNum() + ": " + PATTERN[((gy + (int) (OFFSET * gx )) % len + len) % len]);
                    realLocation = new MapLocation(gxSpacing,(gy + gx2) * SPACING);
                    checkLocation = unitloc.equals(realLocation) ? realLocation : realLocation.add(new Direction((Math.round((unitloc.directionTo(realLocation).radians - 0.25f * 3.14159265f) / (0.5f * 3.14159265f)) * 0.5f * 3.14159265f + 0.25f * 3.14159265f)), 2);
                    canSense = rc.canSenseLocation(checkLocation);
                    if ((!canSense || rc.onTheMap(checkLocation))) {
                        if (Util.DEBUG) rc.setIndicatorDot(realLocation, 0, 0, 255);
                        dist = realLocation.distanceTo(myLocation);
                        if (dist < bestDist && (!canSense || !rc.isCircleOccupiedExceptByThisRobot(realLocation, RobotType.GARDENER.bodyRadius) || unitloc.distanceTo(realLocation) < 0.1f)) {
                            best = realLocation;
                            bestDist = dist;
                        }
                    }
                    //if (Util.DEBUG) System.out.println(Clock.getBytecodeNum());
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return best;
    }

    MapLocation plannedLocation = null;
    int lastReplan = 0;

    @Override
    public MapLocation getNearestPlantableLocation(MapLocation myLocation, TreeInfo[] treeInfos) {
        int time = Clock.getBytecodeNum();
        MapLocation best = null;
        myLocation = myLocation.add(Direction.getEast(), rand() * 0.01f);
        float bestDist = 1000000;
        float dist;
        try {
            if (plannedLocation != null && rc.getRoundNum() - lastReplan < 10 && rc.canSenseAllOfCircle(plannedLocation, GameConstants.BULLET_TREE_RADIUS + 0.05f) && rc.senseTreeAtLocation(plannedLocation) == null && !rc.isCircleOccupiedExceptByThisRobot(plannedLocation, GameConstants.BULLET_TREE_RADIUS + 0.05f)) {
                return plannedLocation;
            }
            lastReplan = rc.getRoundNum();
            float radius = senseRadius;
            int xmax = (int) ((myLocation.x + radius) / SPACING);
            int ymax = (int) ((myLocation.y + radius) / SPACING);
            int ymin = Math.max(0, (int) ((myLocation.y - radius) / SPACING));
            int gx, gy, i;
            float gxSpacing, gx2;
            int[] PATTERN_SKIP_X;
            MapLocation realLocation = new MapLocation(0,0);
            MapLocation checkLocation;
            for (gx = (int) ((myLocation.x - radius) / SPACING); gx < xmax; gx++) {
                gy = ymin;
                // if (Util.DEBUG) System.out.println(gx + "|" + gy + " " + Clock.getBytecodeNum() + ": " + PATTERN[gx % P_X][gy % P_Y]);
                // if (Util.DEBUG) System.out.println(gx + "|" + gy + " " + Clock.getBytecodeNum() + ": " + PATTERN_SKIP[gx % P_X][gy % P_Y]);
                PATTERN_SKIP_X = PATTERN_SKIP[gx % P_X];
                gy += (!PATTERN[gx % P_X][gy % P_Y]) ? PATTERN_SKIP_X[gy % P_Y] : 0;
                gxSpacing = gx * SPACING;
                gx2 = 0.5f * (gx % 2);

                for (; gy < ymax; gy += PATTERN_SKIP_X[gy % P_Y]) {
                    realLocation = new MapLocation(gxSpacing, (gy + gx2) * SPACING);
                    checkLocation = realLocation.add(new Direction((Math.round((myLocation.directionTo(realLocation).radians - 0.25f * 3.14159265f) / (0.5f * 3.14159265f)) * 0.5f * 3.14159265f + 0.25f * 3.14159265f)), 2);
                    //if (Util.DEBUG) System.out.println(realLocation);

                    if (Util.DEBUG) rc.setIndicatorDot(realLocation, 255, 255, 0);
                    if (rc.canSenseLocation(checkLocation) && rc.senseTreeAtLocation(realLocation) == null && rc.onTheMap(checkLocation)) {
                        dist = realLocation.distanceTo(myLocation);
                        if (dist < bestDist && !rc.isCircleOccupiedExceptByThisRobot(realLocation, GameConstants.BULLET_TREE_RADIUS + 0.05f)) {
                            best = realLocation;
                            bestDist = dist;
                        }
                    }
                    //if (Util.DEBUG) System.out.println(Clock.getBytecodeNum());
                }
            }
            if (best != null)
                if (Util.DEBUG) rc.setIndicatorDot(best, 0, 255, 0);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        time = Clock.getBytecodeNum() - time;
        if (time > 1000){
            if (Util.DEBUG) System.out.println("Getting nearest plantable location took " + time);
        }
        plannedLocation = best;
        return best;
    }
}
