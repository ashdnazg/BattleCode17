package johnny4_20170111;

import battlecode.common.*;
import johnny4_20170111.*;
import johnny4_20170111.Radio;

import java.util.*;

public class Map {

    RobotController rc;
    johnny4_20170111.Radio radio;

    public Map(RobotController rc, Radio radio) {
        this.rc = rc;
        this.radio = radio;

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
        ////System.out.println("Used " + (Clock.getBytecodeNum() - clocks) + " bytes for sensing");
    }


    public MapLocation getTarget(MapLocation myLoc) {
        return getTarget(myLoc, 0);
    }

    public MapLocation getTarget(MapLocation myLoc, int type) {
        return getTarget(myLoc, type, 9);
    }
    public MapLocation getTarget(MapLocation myLoc, int type, int maxAge) {
        return getTarget(myLoc, type, maxAge, -1e10f);
    }

    //type: 0=any, 1=military, 2=civilian, 3=archon
    public MapLocation getTarget(MapLocation myLoc, int type, int maxAge, float minDist) {
        float cx, cy;
        cx = cy = 0;
        float mx = myLoc.x;
        float my = myLoc.y;
        float mindist = 1e10f;
        int frame = rc.getRoundNum();
        for (int i = radio.getEnemyCounter() + 101; i >= 101; i--) {
            if (frame - radio.getUnitAge(i) >= maxAge) break;
            RobotType ut = radio.getUnitType(i);
            if (ut == null) continue;
            if (type == 1 && !(ut == RobotType.LUMBERJACK || ut == RobotType.SOLDIER || ut == RobotType.TANK || ut == RobotType.SCOUT)) continue;
            if (type == 2 && !( ut == RobotType.GARDENER)) continue;
            if (type == 3 && !( ut == RobotType.ARCHON)) continue;
            float x = radio.getUnitX(i);
            float y = radio.getUnitY(i);
            float dist = ((mx - x) * (mx - x) + (my - y) * (my - y)) * (type == 3 ? -1 : 1);
            if (dist < mindist && dist > minDist){
                mindist = dist;
                cx = x;
                cy = y;
            }
        }
        if (mindist > 1e6f) return null;
        return new MapLocation(cx, cy);
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
                ////System.out.println("Sensed enemy at " + location);
                ////System.out.println("Enemy coords: " + intel.stream().map(i -> i.location.toString()).reduce((u, i) -> u + ", " + i).get());
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
