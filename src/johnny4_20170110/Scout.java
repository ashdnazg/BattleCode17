package johnny4_20170110;

import battlecode.common.*;

import java.awt.*;
import java.util.Optional;

import static johnny4_20170110.Util.randomDirection;
import static johnny4_20170110.Util.tryMove;

public class Scout {

    RobotController rc;
    Map map;
    Radio radio;

    public Scout(RobotController rc) {
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

    float fx, fy, dx, dy, mag;
    MapLocation[] otherScouts = new MapLocation[100];
    Direction lastDirection = randomDirection();
    TreeInfo toShake = null;


    float circleDir = 0f;

    protected void tick() {
        try {
            if (rc.getTeamBullets() >= 10000f) {
                rc.donate(10000f);
            }

            int frame = rc.getRoundNum();
            MapLocation myLocation = rc.getLocation();

            MapLocation nextEnemy = null;
            MapLocation nextCivilian = null;
            float civSize = 0;

            for (RobotInfo r : map.sense()) {

                if (!r.getTeam().equals(rc.getTeam())) {
                    RobotType ut = r.getType();
                    if ((ut == RobotType.ARCHON || ut == RobotType.GARDENER) && (nextCivilian == null || nextCivilian.distanceTo(myLocation) > r.location.distanceTo(myLocation))) {
                        nextCivilian = r.location;
                        civSize = ut.bodyRadius;
                    }
                    if ((ut == RobotType.LUMBERJACK || ut == RobotType.SOLDIER || ut == RobotType.TANK || ut == RobotType.SCOUT) && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > r.location.distanceTo(myLocation))) {
                        nextEnemy = r.location;
                    }
                }
            }

            if (frame % 8 == 0) {
                radio.reportMyPosition(myLocation);
                otherScouts = radio.getAllyPositions();
                //System.out.println(Clock.getBytecodesLeft() + " for scout");
                fx = fy = 0;
                //System.out.println("Im at " + myLocation);
                for (int i = 0; i < otherScouts.length; i++) {
                    if (otherScouts[i] == null) {
                        //System.out.println(i + " other scouts");
                        break;
                    }
                    //System.out.println("Ohter scout at " + otherScouts[i]);
                    float dist = myLocation.distanceTo(otherScouts[i]);
                    if (dist > 2 * RobotType.SCOUT.sensorRadius) continue;
                    dx = (myLocation.x - otherScouts[i].x);
                    dy = (myLocation.y - otherScouts[i].y);
                    mag = (float) Math.sqrt(dx * dx + dy * dy);
                    fx += dx / mag / dist;
                    fy += dy / mag / dist;

                    //System.out.println("Moving " + weight * dx / mag + " | " + weight * dy / mag);
                }
            }


            mag = (float) Math.sqrt(fx * fx + fy * fy);
            if (nextEnemy == null && nextCivilian == null) {
                nextEnemy = map.getTarget(myLocation);
            }
            float dist = 100000f;
            if (nextEnemy != null) {
                dist = myLocation.distanceTo(nextEnemy);
            }
            if (toShake != null && dist > RobotType.SOLDIER.sensorRadius) {
                if (!tryMove(myLocation.directionTo(toShake.getLocation()))){
                    if (rc.canMove(myLocation.directionTo(toShake.getLocation()), 0.5f)) {
                        rc.move(myLocation.directionTo(toShake.getLocation()), 0.5f);
                    }else{
                        toShake = null;
                    }
                }
                if (rc.canShake(toShake.getID())){
                    rc.shake(toShake.getID());
                    toShake = null;
                }
            } else {
                toShake = null;
                if (nextCivilian != null && dist > RobotType.SOLDIER.sensorRadius) {

                    boolean hasMoved = false;
                    if (nextCivilian.distanceTo(myLocation) - civSize > 5.4) {
                        if (!tryMove(myLocation.directionTo(nextCivilian))) {
                            if (rc.canMove(myLocation.directionTo(nextCivilian), 0.5f)) {
                                rc.move(myLocation.directionTo(nextCivilian), 0.5f);
                                hasMoved = true;
                            }
                        } else {
                            hasMoved = true;
                        }
                    }
                    if (nextCivilian.distanceTo(myLocation) - civSize < 5.4) {
                    /*if (rc.canFirePentadShot()) {
                        rc.firePentadShot(myLocation.directionTo(nextCivilian));
                    }else if (rc.canFireTriadShot()) {
                        rc.fireTriadShot(myLocation.directionTo(nextCivilian));
                    } else*/
                        if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(myLocation.directionTo(nextCivilian));
                        }
                        Direction dir;
                        while (!hasMoved && Math.random() > 0.05f) {
                            if (circleDir > 0.5) {
                                dir = myLocation.directionTo(nextCivilian).rotateRightDegrees(2 * 42);
                            } else {

                                dir = myLocation.directionTo(nextCivilian).rotateLeftDegrees(42 * 2);
                            }
                            if (!hasMoved && rc.canMove(dir, 2f)) {
                                rc.move(dir, 2f);
                                hasMoved = true;
                            } else {
                                circleDir = (float) Math.random();
                            }
                        }
                    }
                } else if (nextEnemy != null && (Math.random() > 0.4 || dist < RobotType.SOLDIER.sensorRadius || mag < 1e-20f)) {
                    if (dist < RobotType.SOLDIER.sensorRadius) {
                        if (rc.getTeamBullets() > 150 && rc.canFireSingleShot()) {
                            rc.fireSingleShot(myLocation.directionTo(nextEnemy));
                        }
                        tryMove(nextEnemy.directionTo(myLocation));
                    } else {
                        //System.out.println("Moving towards enemy at distance " + dist);
                        tryMove(myLocation.directionTo(nextEnemy));
                    }
                } else if (mag < 1e-20f) {
                    while (!rc.canMove(lastDirection)) {
                        lastDirection = randomDirection();
                    }
                    rc.move(lastDirection);
                } else {
                    tryMove(new Direction(RobotType.SCOUT.strideRadius * fx / mag, RobotType.SCOUT.strideRadius * fy / mag));
                }
                if (rc.getRoundNum() - frame > 0 && frame % 8 != 0) {
                    System.out.println("Scout took " + (rc.getRoundNum() - frame) + " frames at " + frame);
                }
                if (Clock.getBytecodesLeft() > 1000 && toShake == null) {
                    for (TreeInfo t : rc.senseNearbyTrees()) {
                        if (t.getContainedBullets() > 0) {
                            toShake = t;
                            break;
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Scout Exception");
            e.printStackTrace();
        }
    }
}
