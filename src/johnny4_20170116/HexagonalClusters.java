package johnny4_20170116;

import battlecode.common.*;

import static johnny4_20170116.Util.*;

public class HexagonalClusters extends Grid {

    final static boolean[] PATTERN = new boolean[]{false, false, true, true, false, false, true, false, true, false, false, true, true, false, false};
    final static int[] PATTERN_SKIP = new int[]{2, 1, 1, 3, 2, 1, 2, 1, 3, 2, 1, 1, 5, 4, 3};
    final static int[] PATTERN_SKIP_INVERSE = new int[]{1, 3, 2, 1, 1, 2, 1, 2, 1, 1, 3, 2, 1, 1, 1};
    final static float OFFSET = -11.5001f;
    final static float SPACING = 2.22f;
    final float senseRadius;

    public HexagonalClusters(RobotController rc) {
        super(rc);
        senseRadius = rc.getType().sensorRadius;
    }

    @Override
    public MapLocation getNearestWalkableLocation(MapLocation myLocation) {
        float OFFSET = HexagonalClusters.OFFSET;
        int len = PATTERN.length;
        MapLocation best = null;
        float bestDist = 1000000;
        float dist;
        if (Util.DEBUG) System.out.println(OFFSET);
        try {
            float radius = 5;
            int xmax = (int) ((myLocation.x + radius) / SPACING);
            int ymax = (int) ((myLocation.y + radius) / SPACING);
            int gx, gy, i;
            MapLocation realLocation;
            MapLocation checkLocation;
            for (gx = (int) ((myLocation.x - radius) / SPACING); gx < xmax; gx++) {
                gy = (int) ((myLocation.y - radius) / SPACING);
                gy += (PATTERN[((gy + (int) (OFFSET * gx)) % len + len) % len]) ? PATTERN_SKIP_INVERSE[((gy + (int) (OFFSET * gx)) % len + len) % len] : 0;

                for (; gy < ymax; gy += PATTERN_SKIP_INVERSE[((gy + (int) (OFFSET * gx)) % len + len) % len]) {
                    //if (Util.DEBUG) System.out.println("f " + Clock.getBytecodeNum() + ": " + PATTERN[((gy + (int) (OFFSET * gx )) % len + len) % len]);
                    realLocation = new MapLocation(gx * SPACING, ((gy + 0.5f * (gx % 2)) * SPACING));
                    checkLocation = realLocation.add(new Direction((Math.round((myLocation.directionTo(realLocation).radians - 0.25f * 3.14159265f) / (0.5f * 3.14159265f)) * 0.5f * 3.14159265f + 0.25f * 3.14159265f)), 2);
                    if ((!rc.canSenseLocation(checkLocation) || rc.onTheMap(checkLocation))) {
                        //if (Util.DEBUG) rc.setIndicatorDot(realLocation, 0, 0, 255);
                        dist = realLocation.distanceTo(myLocation);
                        if (dist < bestDist && !rc.isCircleOccupiedExceptByThisRobot(realLocation, RobotType.GARDENER.bodyRadius)) {
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

    @Override
    public MapLocation getNearestPlantableLocation(MapLocation myLocation, TreeInfo[] treeInfos) {
        int time = Clock.getBytecodeNum();
        float OFFSET = HexagonalClusters.OFFSET;
        int len = PATTERN.length;
        MapLocation best = null;
        if (Util.DEBUG) System.out.println(OFFSET);
        myLocation = myLocation.add(Direction.getEast(), rand() * 0.01f);
        float bestDist = 1000000;
        float dist;
        try {
            if (plannedLocation != null && rc.canSenseLocation(plannedLocation) && rc.senseTreeAtLocation(plannedLocation) == null) {
                return plannedLocation;
            }
            float radius = senseRadius;
            int xmax = (int) ((myLocation.x + radius) / SPACING);
            int ymax = (int) ((myLocation.y + radius) / SPACING);
            int gx, gy, i;
            MapLocation realLocation;
            MapLocation checkLocation;
            for (gx = (int) ((myLocation.x - radius) / SPACING); gx < xmax; gx++) {
                gy = (int) ((myLocation.y - radius) / SPACING);
                gy += (!PATTERN[((gy + (int) (OFFSET * gx)) % len + len) % len]) ? PATTERN_SKIP[((gy + (int) (OFFSET * gx)) % len + len) % len] : 0;

                for (; gy < ymax; gy += PATTERN_SKIP[((gy + (int) (OFFSET * gx)) % len + len) % len]) {
                    //if (Util.DEBUG) System.out.println(gx + "|" + gy + " " + Clock.getBytecodeNum() + ": " + PATTERN[((gy + (int) (OFFSET * gx )) % len + len) % len]);
                    realLocation = new MapLocation(gx * SPACING, (gy + 0.5f * (gx % 2)) * SPACING);
                    checkLocation = realLocation.add(new Direction((Math.round((myLocation.directionTo(realLocation).radians - 0.25f * 3.14159265f) / (0.5f * 3.14159265f)) * 0.5f * 3.14159265f + 0.25f * 3.14159265f)), 2);

                    //if (Util.DEBUG) rc.setIndicatorDot(realLocation, 255, 255, 0);
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
