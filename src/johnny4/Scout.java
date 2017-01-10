package johnny4;

import battlecode.common.*;

import java.util.Optional;

import static johnny4.Util.randomDirection;
import static johnny4.Util.tryMove;

public class Scout {

    RobotController rc;
    Map map;
    Radio radio;

    public Scout(RobotController rc) {
        this.rc = rc;
        this.map = new Map(rc);
        this.radio = new Radio(rc);
    }

    public void run() {
        while (true) {
            tick();
            Clock.yield();
        }
    }

    MapLocation[] otherScouts = new MapLocation[100];

    protected void tick() {
        try {
            int frame = rc.getRoundNum();
            MapLocation myLocation = rc.getLocation();

            map.sense();

            if (frame % 8 == 0) {
                radio.reportMyPosition(myLocation);
                otherScouts = radio.getAllyPositions();
                System.out.println(Clock.getBytecodesLeft() + " for scout");
            }

            float fx, fy;
            fx = fy = 0;
            for (int i = 0; i < otherScouts.length; i++) {
                if (otherScouts[i] == null) break;
                float weight = 1f / myLocation.distanceTo(otherScouts[i]);
                fx += weight * (myLocation.x - otherScouts[i].x);
                fy += weight * (myLocation.y - otherScouts[i].y);
            }
            float mag = (float) Math.sqrt(fx * fx + fy * fy);
            if (mag < 0.001f) {
                tryMove(randomDirection());
            } else {
                tryMove(new Direction(RobotType.SCOUT.strideRadius * fx / mag, RobotType.SCOUT.strideRadius * fy / mag));
            }


        } catch (Exception e) {
            System.out.println("Scout Exception");
            e.printStackTrace();
        }
    }
}
