package johnny4_20170112;

import battlecode.common.*;

import java.util.*;

public class Map {

    RobotController rc;
    Radio radio;
    MapLocation[] archonPos;

    public Map(RobotController rc, Radio radio) {
        this.rc = rc;
        this.radio = radio;
        archonPos = rc.getInitialArchonLocations(rc.getTeam().opponent());

        // init vision
    }

    public RobotInfo[] sense() {
        int frame = rc.getRoundNum();
        int clocks = Clock.getBytecodeNum();
        RobotInfo[] ret = rc.senseNearbyRobots();
        for (RobotInfo r : ret) {
            if (!r.getTeam().equals(rc.getTeam())) {
                radio.reportEnemy(r.getLocation(), r.getType(), frame);
            }
        }
        return ret;
        //System.out.println("Used " + (Clock.getBytecodeNum() - clocks) + " bytes for sensing");
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
                x = (unitData & 0b11111111110000000000000000000000) >> 22;
                y = (unitData & 0b00000000001111111111000000000000) >> 12;
                dist = ((mx - x) * (mx - x) + (my - y) * (my - y)) * tmul;
                if (dist < mindist && dist * tmul > minDist) {
                    found++;
                    mindist = dist;
                    cx = x;
                    cy = y;
                }
            }
            clock = Clock.getBytecodeNum() - clock;
            if (clock > 1500 && frame == rc.getRoundNum()) {
                //System.out.println("Get target took " + clock + " evaluating " + (ecnt + 101 - i) + " found " + found);
            }
            if (maxAge > 100) {
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
