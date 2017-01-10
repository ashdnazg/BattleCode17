package johnny4;

import battlecode.common.*;

import java.util.Optional;

import static johnny4.Util.*;

public class Soldier {

    RobotController rc;
    Map map;
    Radio radio;

    public Soldier(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
    }

    public void run() {
        while (true) {
            tick();
            Clock.yield();
        }
    }

    protected void tick() {
        try {
            map.sense();

            MapLocation myLocation = rc.getLocation();

            // See if there are any nearby enemy robots
            MapLocation target = map.getTarget(myLocation);

            if (target == null) {
                tryMove(randomDirection());
            } else {
                if (myLocation.distanceTo(target) < 0.7f * RobotType.SOLDIER.sensorRadius) {
                    tryMove(target.directionTo(myLocation));
                } else {
                    tryMove(myLocation.directionTo(target));
                }
                rc.fireSingleShot(myLocation.directionTo(target));
            }


        } catch (Exception e) {
            System.out.println("Soldier Exception");
            e.printStackTrace();
        }
    }
}
