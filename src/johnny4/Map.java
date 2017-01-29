package johnny4;

import battlecode.common.*;

public class Map {

    RobotController rc;
    Radio radio;
    static MapLocation[] enemyArchonPos;
    static MapLocation[] ourArchonPos;
    static MapLocation[] farTargets = new MapLocation[6];
    static float[] tempDist = new float[6];
    static float[] tempX = new float[6];
    static float[] tempY = new float[6];
    static int targetsFrame = -1;
    static int lastSenseFrame = -1;
    static RobotInfo[] nearbyRobots;

    public Map(RobotController rc, Radio radio) {
        this.rc = rc;
        this.radio = radio;
        ourArchonPos = rc.getInitialArchonLocations(rc.getTeam());
        enemyArchonPos = rc.getInitialArchonLocations(rc.getTeam().opponent());
        // init vision
    }

    public RobotInfo[] sense() throws GameActionException {
        int frame = rc.getRoundNum();
        if (frame != lastSenseFrame) {
            lastSenseFrame = frame;
            nearbyRobots = Util.senseClosestRobots();
            Radio.reportEnemies(nearbyRobots);
            //if (Util.DEBUG) System.out.println("Used " + (Clock.getBytecodeNum() - clocks) + " bytes for sensing");
            return nearbyRobots;
        }
        //int clocks = Clock.getBytecodeNum();
        return nearbyRobots;
    }


    final static int LUMBERJACK = Radio.typeToInt(RobotType.LUMBERJACK);
    final static int SOLDIER = Radio.typeToInt(RobotType.SOLDIER);
    final static int TANK = Radio.typeToInt(RobotType.TANK);
    final static int SCOUT = Radio.typeToInt(RobotType.SCOUT);
    final static int ARCHON = Radio.typeToInt(RobotType.ARCHON);
    final static int GARDENER = Radio.typeToInt(RobotType.GARDENER);

    public static MapLocation getTarget(int type, MapLocation myLocation) {
        return getTarget(ARCHON, GARDENER, LUMBERJACK, SCOUT, SOLDIER, TANK, type, myLocation);
    }

    static int targetType;

    //type: 0=any, 1=military, 2=civilian, 3=archon
    public static MapLocation getTarget(int ARCHON, int GARDENER, int LUMBERJACK, int SCOUT, int SOLDIER, int TANK, int type, MapLocation myLocation) {
        try {
            MapLocation best = null;
            for (int t = 0; t < farTargets.length; t++) {
                if (farTargets[t] == null) continue;
                switch (type) {
                    case 1:
                        if (t == LUMBERJACK || t == SOLDIER || t == TANK || t == SCOUT) {
                            break;
                        }
                        continue;
                    case 2:
                        if (t == GARDENER) {
                            break;
                        }
                        continue;
                    case 3:
                        if (t == ARCHON) {
                            break;
                        }
                        continue;
                    case 4:
                        if (t == LUMBERJACK || t == SOLDIER || t == TANK) {
                            break;
                        }
                        continue;
                    case 5:
                        if (t == GARDENER || t == SCOUT) {
                            break;
                        }
                        continue;
                    case 6:
                        if (t == LUMBERJACK || t == SOLDIER || t == TANK || t == SCOUT || t== GARDENER) {
                            break;
                        }
                        continue;
                    case 7:
                        if (t == LUMBERJACK || t == SOLDIER || t == TANK || t== GARDENER) {
                            break;
                        }
                        continue;
                }
                if (best == null || best.distanceTo(myLocation) > farTargets[t].distanceTo(myLocation)) {
                    best = farTargets[t];
                    targetType = t;
                }
            }
            return best;

        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }


    public static void generateFarTargets(RobotController rc, MapLocation myLoc, int maxAge, float minDist) throws GameActionException {
        minDist *= minDist; //square distances
        tempDist[0] = 1e10f;
        tempDist[1] = 1e10f;
        tempDist[2] = 1e10f;
        tempDist[3] = 1e10f;
        tempDist[4] = 1e10f;
        tempDist[5] = 1e10f;

        float cx, cy;
        cx = cy = 0;
        float mx = myLoc.x;
        float my = myLoc.y;
        int frame = rc.getRoundNum();
        int ecnt = rc.readBroadcast(1);
        float x, y, dx, dy, dist;
        int unitData, ut;
        int reportFrame;
        for (int i = ecnt * 2; i >= 2; i -= 2) {
            //if (Util.DEBUG) System.out.println("1: " + Clock.getBytecodeNum());
            unitData = rc.readBroadcast(i);
            reportFrame = rc.readBroadcast(i + 1);
            //if (Util.DEBUG) System.out.println("my target was reported in: " + reportFrame);
            //if (Util.DEBUG) System.out.println("1.2: " + Clock.getBytecodeNum());
            if ((frame - reportFrame) >= maxAge)
                continue;
            ut = (unitData & 0b00000000000000000000000000000111);
            //if (Util.DEBUG) System.out.println("2: " + Clock.getBytecodeNum());

            x = ((unitData & 0b11111111111111000000000000000000) >>> 18) / 16.0f;
            y = ((unitData & 0b00000000000000111111111111110000) >>> 4) / 16.0f;
            dx = (mx - x);
            dy = (my - y);
            dist = dx * dx + dy * dy;
            if (dist > tempDist[ut] || dist < minDist) {
                continue;
            }

            tempDist[ut] = dist;
            tempX[ut] = x;
            tempY[ut] = y;
        }

        if (tempDist[0] != 1e10f) {
            farTargets[0] = new MapLocation(tempX[0], tempY[0]);
        } else {
            farTargets[0] = null;
        }

        if (tempDist[1] != 1e10f) {
            farTargets[1] = new MapLocation(tempX[1], tempY[1]);
        } else {
            farTargets[1] = null;
        }

        if (tempDist[2] != 1e10f) {
            farTargets[2] = new MapLocation(tempX[2], tempY[2]);
        } else {
            farTargets[2] = null;
        }

        if (tempDist[3] != 1e10f) {
            farTargets[3] = new MapLocation(tempX[3], tempY[3]);
        } else {
            farTargets[3] = null;
        }

        if (tempDist[4] != 1e10f) {
            farTargets[4] = new MapLocation(tempX[4], tempY[4]);
        } else {
            farTargets[4] = null;
        }

        if (tempDist[5] != 1e10f) {
            farTargets[5] = new MapLocation(tempX[5], tempY[5]);
        } else {
            farTargets[5] = null;
        }
        targetsFrame = frame;
    }

}
