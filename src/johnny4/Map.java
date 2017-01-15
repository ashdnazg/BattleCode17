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


    final int LUMBERJACK = Radio.typeToInt(RobotType.LUMBERJACK);
    final int SOLDIER = Radio.typeToInt(RobotType.SOLDIER);
    final int TANK = Radio.typeToInt(RobotType.TANK);
    final int SCOUT = Radio.typeToInt(RobotType.SCOUT);
    final int ARCHON = Radio.typeToInt(RobotType.ARCHON);
    final int GARDENER = Radio.typeToInt(RobotType.GARDENER);

    //type: 0=any, 1=military, 2=civilian, 3=archon
    public MapLocation getTarget(int type, MapLocation myLocation) {
        try {
            MapLocation best = null;
            for (int t = 0; t < farTargets.length; t++) {
                if (farTargets[t] == null) continue;
                if (type == 1 && !(t == LUMBERJACK || t == SOLDIER || t == TANK || t == SCOUT))
                    continue;
                if (type == 2 && !(t == GARDENER)) continue;
                if (type == 3 && !(t == ARCHON)) continue;
                if (type == 4 && !(t == LUMBERJACK || t == SOLDIER || t == TANK)) continue;
                if (best == null || best.distanceTo(myLocation) > farTargets[t].distanceTo(myLocation)) {
                    best = farTargets[t];
                }
            }
            return best;

        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }


    public void generateFarTargets(MapLocation myLoc, int maxAge, float minDist) throws GameActionException {
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
        for (int i = ecnt + 100; i >= 101; i--) {
            //System.out.println("1: " + Clock.getBytecodeNum());
            unitData = rc.readBroadcast(i);
            //System.out.println("1.2: " + Clock.getBytecodeNum());
            if (frame - ((unitData & 0b00000000000000000000000111111111)) * 8 >= maxAge)
                continue;
            ut = (unitData & 0b00000000000000000000111000000000) >> 9;
            //System.out.println("2: " + Clock.getBytecodeNum());

            x = (unitData & 0b11111111110000000000000000000000) >> 22;
            y = (unitData & 0b00000000001111111111000000000000) >> 12;
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
