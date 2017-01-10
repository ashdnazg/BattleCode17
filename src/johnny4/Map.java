package johnny4;

import battlecode.common.*;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class Map {

    RobotController rc;
    Set<Intel> intel = new HashSet<>();

    public Map(RobotController rc){
        this.rc = rc;

        // init vision
        System.out.println(rc.getInitialArchonLocations(rc.getTeam())[0].toString());
    }

    public void sense(){
        int clocks = Clock.getBytecodeNum();
        for (RobotInfo r : rc.senseNearbyRobots()){
            if (!r.getTeam().equals(rc.getTeam())) {
                new Intel(r);
            }
        }
        System.out.println("Used " + (Clock.getBytecodeNum() - clocks) + " bytes for sensing");
    }

    public Optional<MapLocation> getTarget(MapLocation myLoc){
        final int round = rc.getRoundNum();
        return intel.stream().filter(i -> round - i.creationTime < 10).min(Comparator.comparing(i -> myLoc.distanceTo(i.location))).map(i -> i.location);
    }

    public class Intel{

        final int creationTime;
        final int robotId;
        final MapLocation location;
        final RobotType robotType;

        public Intel(int creationTime, int robotId, MapLocation location, RobotType robotType){

            this.creationTime = creationTime;
            this.robotId = robotId;
            this.location = location;
            this.robotType = robotType;

            //register
            if (!intel.stream().anyMatch(i -> i.equalRobot(this) && i.creationTime > creationTime)){
                intel.removeIf(i -> i.equalRobot(this));
                intel.add(this);
                System.out.println("Sensed enemy at " + location);
                System.out.println("Enemy coords: " + intel.stream().map(i -> i.location.toString()).reduce((u, i) -> u + ", " + i).get());
            }
        }

        public Intel(RobotInfo r) {
            this(rc.getRoundNum(), r.ID, r.getLocation(), r.getType());
        }

        public boolean equalRobot(Intel other){
            return other.robotId == this.robotId ||
                    (other.robotId | robotId) < 0 && location.distanceTo(other.location) < Math.abs(creationTime - other.creationTime) * RobotType.SCOUT.strideRadius;
        }
    }
}
