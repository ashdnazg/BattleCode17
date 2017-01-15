package johnny4;

import battlecode.common.*;

import static johnny4.Util.rand;

public class WellSpacedHexagonalClusters extends Grid {

    final static int P_X = 6;
    final static int P_Y = 3;
    final static boolean[][] PATTERN = {{true, true, false}, {true, false, false}, {false, false, false}, {false, true, true}, {false, false, true}, {false, false, false}};
    final static int[][] PATTERN_SKIP = {{1,2,1}, {3,2,1}, {10000,10000,10000}, {1,1,2}, {2,1,3}, {10000,10000,10000}};
    final static int[][] PATTERN_SKIP_INVERSE = {{2,1,3},{1,1,2},{1,1,1},{3,2,1},{1,2,1},{1,1,1}};
    final static float SPACING = 2.12f;
    final float senseRadius;

    public WellSpacedHexagonalClusters(RobotController rc) {
        super(rc);
        senseRadius = rc.getType().sensorRadius;
    }

    @Override
    public MapLocation getNearestWalkableLocation(MapLocation myLocation) {
        MapLocation best = null;
        MapLocation unitloc = rc.getLocation();
        float bestDist = 1000000;
        float dist;
        try {
            float radius = 5;
            int xmax = (int) ((myLocation.x + radius) / SPACING);
            int ymax = (int) ((myLocation.y + radius) / SPACING);
            int ymin = Math.max(0, (int) ((myLocation.y - radius) / SPACING));
            int gx, gy, i;
            MapLocation realLocation;
            MapLocation checkLocation;
            for (gx = (int) ((myLocation.x - radius) / SPACING); gx < xmax; gx++) {
                gy = ymin;
                gy += (PATTERN[gx % P_X][gy % P_Y]) ? PATTERN_SKIP_INVERSE[gx % P_X][gy % P_Y] : 0;

                for (; gy < ymax; gy += PATTERN_SKIP_INVERSE[gx % P_X][gy % P_Y]) {
                    //System.out.println("f " + Clock.getBytecodeNum() + ": " + PATTERN[((gy + (int) (OFFSET * gx )) % len + len) % len]);
                    realLocation = new MapLocation(gx * SPACING, ((gy + 0.5f * (gx % 2)) * SPACING));
                    checkLocation = realLocation.add(new Direction((Math.round((unitloc.directionTo(realLocation).radians - 0.25f * 3.14159265f) / (0.5f * 3.14159265f)) * 0.5f * 3.14159265f + 0.25f * 3.14159265f)), 2);
                    if ((!rc.canSenseLocation(checkLocation) || rc.onTheMap(checkLocation))) {
                        //rc.setIndicatorDot(realLocation, 0, 0, 255);
                        dist = realLocation.distanceTo(myLocation);
                        if (dist < bestDist && (!rc.canSenseLocation(checkLocation) || !rc.isCircleOccupiedExceptByThisRobot(realLocation, RobotType.GARDENER.bodyRadius) || unitloc.distanceTo(realLocation) < 0.1f)) {
                            best = realLocation;
                            bestDist = dist;
                        }
                    }
                    //System.out.println(Clock.getBytecodeNum());
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
        MapLocation best = null;
        myLocation = myLocation.add(Direction.getEast(), rand() * 0.01f);
        float bestDist = 1000000;
        float dist;
        try {
            if (plannedLocation != null && rc.canSenseLocation(plannedLocation) && rc.senseTreeAtLocation(plannedLocation) == null && !rc.isCircleOccupiedExceptByThisRobot(plannedLocation, GameConstants.BULLET_TREE_RADIUS + 0.05f)) {
                return plannedLocation;
            }
            float radius = senseRadius;
            int xmax = (int) ((myLocation.x + radius) / SPACING);
            int ymax = (int) ((myLocation.y + radius) / SPACING);
            int ymin = Math.max(0, (int) ((myLocation.y - radius) / SPACING));
            int gx, gy, i;
            MapLocation realLocation;
            MapLocation checkLocation;
            for (gx = (int) ((myLocation.x - radius) / SPACING); gx < xmax; gx++) {
                gy = ymin;
                System.out.println(gx + "|" + gy + " " + Clock.getBytecodeNum() + ": " + PATTERN[gx % P_X][gy % P_Y]);
                System.out.println(gx + "|" + gy + " " + Clock.getBytecodeNum() + ": " + PATTERN_SKIP[gx % P_X][gy % P_Y]);
                gy += (!PATTERN[gx % P_X][gy % P_Y]) ? PATTERN_SKIP[gx % P_X][gy % P_Y] : 0;

                for (; gy < ymax; gy += PATTERN_SKIP[gx % P_X][gy % P_Y]) {
                    System.out.println(gx + "|" + gy + " " + Clock.getBytecodeNum() + ": " + PATTERN[gx % P_X][gy % P_Y]);
                    realLocation = new MapLocation(gx * SPACING, (gy + 0.5f * (gx % 2)) * SPACING);
                    checkLocation = realLocation.add(new Direction((Math.round((myLocation.directionTo(realLocation).radians - 0.25f * 3.14159265f) / (0.5f * 3.14159265f)) * 0.5f * 3.14159265f + 0.25f * 3.14159265f)), 2);

                    rc.setIndicatorDot(realLocation, 255, 255, 0);
                    if (rc.canSenseLocation(checkLocation) && rc.senseTreeAtLocation(realLocation) == null && rc.onTheMap(checkLocation)) {
                        dist = realLocation.distanceTo(myLocation);
                        if (dist < bestDist && !rc.isCircleOccupiedExceptByThisRobot(realLocation, GameConstants.BULLET_TREE_RADIUS + 0.05f)) {
                            best = realLocation;
                            bestDist = dist;
                        }
                    }
                    //System.out.println(Clock.getBytecodeNum());
                }
            }
            if (best != null)
                rc.setIndicatorDot(best, 0, 255, 0);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        time = Clock.getBytecodeNum() - time;
        if (time > 1000){
            System.out.println("Getting nearest plantable location took " + time);
        }
        plannedLocation = best;
        return best;
    }
}
