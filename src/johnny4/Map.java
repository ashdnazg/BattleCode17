package johnny4;

import battlecode.common.*;

import java.util.*;

public class Map {

    RobotController rc;
    Radio radio;

    public Map(RobotController rc, Radio radio) {
        this.rc = rc;
        this.radio = radio;

        // init vision
    }

    public void sense() {
        int frame = rc.getRoundNum();
        int clocks = Clock.getBytecodeNum();
        for (RobotInfo r : rc.senseNearbyRobots()) {
            if (!r.getTeam().equals(rc.getTeam())) {
                radio.reportEnemy(r.getLocation(), r.getType(), frame);
            }
        }
        //System.out.println("Used " + (Clock.getBytecodeNum() - clocks) + " bytes for sensing");
    }


    public MapLocation getTarget(MapLocation myLoc) {
        float cx, cy;
        cx = cy = 0;
        float mx = myLoc.x;
        float my = myLoc.y;
        float mindist = Float.MAX_VALUE;
        int frame = rc.getRoundNum();
        for (int i = 0; i < 100; i++) {
            if (frame - radio.getUnitAge(i) >= 8) continue;
            if (radio.getUnitType(i) == null) continue;
            float x = radio.getUnitX(i);
            float y = radio.getUnitY(i);
            float dist = (mx - x) * (mx - x) + (my - y) * (my - y);
            if (dist < mindist){
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
