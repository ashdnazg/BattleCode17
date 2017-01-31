package mapHack;

import battlecode.common.*;

public class Map {

    static RobotController rc;
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
    static float minX, minY, maxX, maxY;

    public static void updateRadioMapBoundaries() throws GameActionException {
        float curMinX = Radio.getMapMinX();
        float curMinY = Radio.getMapMinY();
        float curMaxX = Radio.getMapMaxX();
        float curMaxY = Radio.getMapMaxY();
        if (minX < curMinX) {
            Radio.setMapMinX(minX);
        } else {
            minX = curMinX;
        }
        if (minY < curMinY) {
            Radio.setMapMinY(minY);
        } else {
            minY = curMinY;
        }
        if (maxX > curMaxX) {
            Radio.setMapMaxX(maxX);
        } else {
            maxX = curMaxX;
        }
        if (maxY > curMaxY) {
            Radio.setMapMaxY(maxY);
        } else {
            maxY = curMaxY;
        }
    }

    public static void debug_drawBoundaries() {
        // System.out.println("drawing boundaries + " +  minX + " " + minY + " " + maxX + " " + maxY);
        //if (Radio.myRadioID == 0) {
            rc.setIndicatorLine(new MapLocation(minX, minY), new MapLocation(minX, maxY), 0, 255, 0);
            rc.setIndicatorLine(new MapLocation(minX, maxY), new MapLocation(maxX, maxY), 0, 255, 0);
            rc.setIndicatorLine(new MapLocation(maxX, maxY), new MapLocation(maxX, minY), 0, 255, 0);
            rc.setIndicatorLine(new MapLocation(maxX, minY), new MapLocation(minX, minY), 0, 255, 0);
        //}
    }

    public static void updateOwnMapBoundaries() throws GameActionException {
        float sensorRadius = rc.getType().sensorRadius;
        MapLocation myLocation = rc.getLocation();
        if (rc.onTheMap(myLocation, sensorRadius)) {
            if (myLocation.x - sensorRadius < minX) {
                minX = myLocation.x - sensorRadius;
            }
            if (myLocation.y - sensorRadius < minY) {
                minY = myLocation.y - sensorRadius;
            }
            if (myLocation.x + sensorRadius > maxX) {
                maxX = myLocation.x + sensorRadius;
            }
            if (myLocation.y + sensorRadius > maxY) {
                maxY = myLocation.y + sensorRadius;
            }
            return;
        }
    }

    public static void hackishMapSize(Direction dir) throws GameActionException {
        MapLocation myLocation = rc.getLocation();
        float tempRadius = rc.getType().bodyRadius;
        float lastOnMapRadius = 0;
        MapLocation tempLoc = myLocation.add(dir, tempRadius);
        MapLocation lastOnMapLoc = tempLoc;
        while (rc.onTheMap(tempLoc, -tempRadius)) {
            lastOnMapRadius = tempRadius;
            lastOnMapLoc = tempLoc;
            tempRadius *= 2.f;
            tempLoc = myLocation.add(dir, tempRadius);
        }
        float step = tempRadius * 0.5f;
        while (step >= 0.01f) {
            if (rc.onTheMap(tempLoc, -tempRadius)) {
                lastOnMapRadius = tempRadius;
                lastOnMapLoc = tempLoc;
                tempRadius += step;
                tempLoc = myLocation.add(dir, tempRadius);
            } else {
                tempRadius -= step;
                tempLoc = myLocation.add(dir, tempRadius);
            }
            step *= 0.5f;
        }
        if (rc.onTheMap(tempLoc, -tempRadius)) {
            lastOnMapRadius = tempRadius;
            lastOnMapLoc = tempLoc;
        }
        minX = Math.min(minX, lastOnMapLoc.x - lastOnMapRadius);
        minY = Math.min(minY, lastOnMapLoc.y - lastOnMapRadius);
        maxX = Math.max(maxX, lastOnMapLoc.x + lastOnMapRadius);
        maxY = Math.max(maxY, lastOnMapLoc.y + lastOnMapRadius);
    }




    public Map(RobotController rc, Radio radio) {
        this.rc = rc;
        this.radio = radio;
        ourArchonPos = rc.getInitialArchonLocations(rc.getTeam());
        enemyArchonPos = rc.getInitialArchonLocations(rc.getTeam().opponent());
        // init maphack
        MapLocation myLocation = rc.getLocation();
        try {
            if (Radio.myRadioID == 0) {
                minX = myLocation.x;
                maxX = myLocation.x;
                minY = myLocation.y;
                maxY = myLocation.y;
                for (MapLocation r: ourArchonPos) {
                    minX = Math.min(r.x, minX);
                    maxX = Math.max(r.x, maxX);
                    minY = Math.min(r.y, minY);
                    maxY = Math.max(r.y, maxY);
                }
                for (MapLocation r: enemyArchonPos) {
                    minX = Math.min(r.x, minX);
                    maxX = Math.max(r.x, maxX);
                    minY = Math.min(r.y, minY);
                    maxY = Math.max(r.y, maxY);
                }
                minX -= RobotType.ARCHON.bodyRadius;
                minY -= RobotType.ARCHON.bodyRadius;
                maxX += RobotType.ARCHON.bodyRadius;
                maxY += RobotType.ARCHON.bodyRadius;
            } else {
                minX = Radio.getMapMinX();
                maxX = Radio.getMapMaxX();
                minY = Radio.getMapMinY();
                maxY = Radio.getMapMaxY();
            }

            if (rc.getRoundNum() == 1) {
                hackishMapSize(Direction.NORTH);
                hackishMapSize(Direction.EAST);
                hackishMapSize(Direction.SOUTH);
                hackishMapSize(Direction.WEST);
            }

            //HEAD ARCHON ONLY
            if (Radio.myRadioID == 0) {
                Radio.setMapMinX(minX);
                Radio.setMapMaxX(maxX);
                Radio.setMapMinY(minY);
                Radio.setMapMaxY(maxY);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Something in Map");
        }
    }

    public RobotInfo[] sense() throws GameActionException {
        updateOwnMapBoundaries();
        updateRadioMapBoundaries();
        debug_drawBoundaries();
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
                    case 8:
                        if (t == SCOUT) {
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
            ex.printStackTrace();Util.EXCEPTION();
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
