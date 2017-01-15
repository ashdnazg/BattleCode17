package johnny4;

import battlecode.common.*;

import java.util.*;

public class Map {

    RobotController rc;
    Radio radio;
    MapLocation[] archonPos;
    int _type;
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
        archonPos = rc.getInitialArchonLocations(rc.getTeam().opponent());

        // init vision
    }

    public RobotInfo[] sense() throws GameActionException {
        int frame = rc.getRoundNum();
        if (frame != lastSenseFrame) {
            lastSenseFrame = frame;
            nearbyRobots = rc.senseNearbyRobots();
            Radio.reportEnemies(nearbyRobots);
            //System.out.println("Used " + (Clock.getBytecodeNum() - clocks) + " bytes for sensing");
            return nearbyRobots;
        }
        //int clocks = Clock.getBytecodeNum();
        return nearbyRobots;
    }


    public MapLocation getTarget(MapLocation myLoc) {
        return getTarget(myLoc, 0);
    }

    public MapLocation getTarget(MapLocation myLoc, int type) {
        return getTarget(myLoc, type, 9);
    }

    public MapLocation getTarget(MapLocation myLoc, int type, int maxAge) {
        return getTarget(myLoc, type, maxAge, 0);
    }

    final int LUMBERJACK = Radio.typeToInt(RobotType.LUMBERJACK);
    final int SOLDIER = Radio.typeToInt(RobotType.SOLDIER);
    final int TANK = Radio.typeToInt(RobotType.TANK);
    final int SCOUT = Radio.typeToInt(RobotType.SCOUT);
    final int ARCHON = Radio.typeToInt(RobotType.ARCHON);
    final int GARDENER = Radio.typeToInt(RobotType.GARDENER);

    //type: 0=any, 1=military, 2=civilian, 3=archon
    public MapLocation getTarget(MapLocation myLoc, int type, int maxAge, float minDist) {
        try {
            minDist *= minDist; //square distances
            float cx, cy;
            cx = cy = 0;
            float mx = myLoc.x;
            float my = myLoc.y;
            float mindist = 1e10f;
            int frame = rc.getRoundNum();
            int ecnt = radio.getEnemyCounter();
            int clock = Clock.getBytecodeNum();
            int i;
            float x, y, dist;
            int unitData, age, ut, found = 0;
            float tmul = (type == 3 ? -1 : 1);
            for (i = ecnt + 100; i >= 101; i--) {
                //System.out.println("1: " + Clock.getBytecodeNum());
                unitData = //radio.read(i);
                        rc.readBroadcast(i);
                //System.out.println("1.2: " + Clock.getBytecodeNum());
                if (frame - ((unitData & 0b00000000000000000000000111111111)) * 8 >= maxAge || found > 8 || (ecnt + 101 - i) > 40)
                    break;
                ut = (unitData & 0b00000000000000000000111000000000) >> 9;
                //System.out.println("2: " + Clock.getBytecodeNum());
                if (ut == 0) continue;
                if (type == 1 && !(ut == LUMBERJACK || ut == SOLDIER || ut == TANK || ut == SCOUT))
                    continue;
                if (type == 2 && !(ut == GARDENER)) continue;
                if (type == 3 && !(ut == ARCHON)) continue;
                if (type == 4 && !(ut == LUMBERJACK || ut == SOLDIER || ut == TANK)) continue;
                x = (unitData & 0b11111111110000000000000000000000) >> 22;
                y = (unitData & 0b00000000001111111111000000000000) >> 12;
                dist = ((mx - x) * (mx - x) + (my - y) * (my - y)) * tmul;
                if (dist < mindist && dist * tmul > minDist) {
                    found++;
                    mindist = dist;
                    cx = x;
                    cy = y;
                    _type = ut;
                }
            }
            clock = Clock.getBytecodeNum() - clock;
            if (clock > 1500 && frame == rc.getRoundNum()) {
                //System.out.println("Get target took " + clock + " evaluating " + (ecnt + 101 - i) + " found " + found);
            }
            if (maxAge > 100 && mindist > 1e6f) {
                for (MapLocation m : archonPos) {
                    dist = m.distanceSquaredTo(myLoc) * tmul;
                    if (dist < mindist && dist * tmul > minDist) {
                        mindist = dist;
                        cx = m.x;
                        cy = m.y;
                    }
                }
            }
            if (mindist > 1e6f) {
                return null;
            }
            return new MapLocation(cx, cy);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }


    public void generateFarTargets(MapLocation myLoc, int maxAge, float minDist) throws GameActionException{
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
        int ecnt = radio.getEnemyCounter();
        for (int i = ecnt + 100; i >= 101; i--) {
            //System.out.println("1: " + Clock.getBytecodeNum());
            int unitData = rc.readBroadcast(i);
            //System.out.println("1.2: " + Clock.getBytecodeNum());
            if (frame - ((unitData & 0b00000000000000000000000111111111)) * 8 >= maxAge)
                continue;
            int ut = (unitData & 0b00000000000000000000111000000000) >> 9;
            //System.out.println("2: " + Clock.getBytecodeNum());

            float x = (unitData & 0b11111111110000000000000000000000) >> 22;
            float y = (unitData & 0b00000000001111111111000000000000) >> 12;
            float dx = (mx - x);
            float dy = (my - y);
            float dist = dx * dx + dy * dy;
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

    /*
    private static List<Intel> toremove = new ArrayList();

    public class Intel{

        final int creationTime;
        final int robotId;
        final MapLocation location;
        final RobotType robotType;
        final Intel previousIntel;


        public Intel(int creationTime, int robotId, MapLocation location, RobotType robotType){

            this.creationTime = creationTime;
            this.robotId = robotId;
            this.location = location;
            this.robotType = robotType;

            toremove.clear();;
            Intel bestprev = null;
            for (Intel i : intel) {
                if (i.equalRobot(this)){
                    toremove.add(i);
                    if (bestprev == null || bestprev.creationTime < i.creationTime){
                        bestprev = i;
                    }
                }
            }
            previousIntel = bestprev;
            //register
            if (previousIntel == null || previousIntel.creationTime < creationTime){
                intel.removeAll(toremove);
                intel.add(this);
                //System.out.println("Sensed enemy at " + location);
                //System.out.println("Enemy coords: " + intel.stream().map(i -> i.location.toString()).reduce((u, i) -> u + ", " + i).get());
            }
        }

        public Intel(RobotInfo r) {
            this(rc.getRoundNum(), r.ID, r.getLocation(), r.getType());
        }

        public boolean equalRobot(Intel other){
            return other.robotId == this.robotId ||
                    (other.robotId | robotId) < 0 && location.distanceTo(other.location) < Math.abs(creationTime - other.creationTime) * RobotType.SCOUT.strideRadius;
        }
    }*/
}
