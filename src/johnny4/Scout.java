package johnny4;

import battlecode.common.*;

import java.awt.*;
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
                //System.out.println(Clock.getBytecodesLeft() + " for scout");
            }

            float fx, fy, dx, dy, mag;
            fx = fy = 0;
            //System.out.println("Im at " + myLocation);
            for (int i = 0; i < otherScouts.length; i++) {
                if (otherScouts[i] == null) {
                    System.out.println(i + " other scouts");
                    break;
                }
                //System.out.println("Ohter scout at " + otherScouts[i]);
                float dist = myLocation.distanceTo(otherScouts[i]);
                if (dist > 2 * RobotType.SCOUT.sensorRadius) continue;
                dx =  (myLocation.x - otherScouts[i].x);
                dy =  (myLocation.y - otherScouts[i].y);
                mag = (float) Math.sqrt(dx * dx + dy * dy);
                fx +=  dx / mag / dist;
                fy +=  dy / mag / dist;

                //System.out.println("Moving " + weight * dx / mag + " | " + weight * dy / mag);
            }
            mag = (float) Math.sqrt(fx * fx + fy * fy);
            if (mag < 1e-20f) {
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
