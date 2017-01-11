package johnny4;

import battlecode.common.*;

import java.awt.*;
import java.util.Optional;

import static johnny4.Util.*;

public class Scout {

    RobotController rc;
    Map map;
    Radio radio;
    final boolean isShaker;

    public Scout(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        isShaker = rc.getID() % 3 == 0;
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
    MapLocation lastCivilian = null;


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
            float civMinDist = 10000f;

            RobotInfo nearbyRobots[] = map.sense();
            TreeInfo trees[] = rc.senseNearbyTrees();
            for (RobotInfo r : nearbyRobots) {

                if (!r.getTeam().equals(rc.getTeam())) {
                    RobotType ut = r.getType();
                    if ((ut == RobotType.GARDENER) && (civMinDist > r.location.distanceTo(myLocation) ) || lastCivilian != null && r.location.distanceTo(lastCivilian) < 3 ) {
                        nextCivilian = r.location;
                        civMinDist = (lastCivilian != null && r.location.distanceTo(lastCivilian) < 3) ? 0f : (r.location.distanceTo(myLocation) );
                        civSize = ut.bodyRadius;
                    }
                    if ((ut == RobotType.LUMBERJACK || ut == RobotType.SOLDIER || ut == RobotType.TANK || ut == RobotType.SCOUT) && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > r.location.distanceTo(myLocation))) {
                        nextEnemy = r.location;
                    }
                }
            }
            if (nextCivilian == null) {
                nextCivilian = map.getTarget(myLocation, 2);
            }
            if (nextCivilian == null) {
                System.out.println("No civ");
            }
            lastCivilian = nextCivilian;

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
            boolean hasMoved = tryEvade();
            if (nextEnemy != null) {
                dist = myLocation.distanceTo(nextEnemy);
            }
            if (toShake != null && dist > RobotType.SOLDIER.sensorRadius) {
                //System.out.println("Shaking " + toShake.getLocation());
                if (!hasMoved && !tryMove(myLocation.directionTo(toShake.getLocation()))) {
                    if (rc.canMove(myLocation.directionTo(toShake.getLocation()), 0.5f)) {
                        rc.move(myLocation.directionTo(toShake.getLocation()), 0.5f);
                        hasMoved = true;
                    } else {
                        toShake = null;
                    }
                }
                if (rc.canShake(toShake.getID())) {
                    rc.shake(toShake.getID());
                    System.out.println("Shaken " + toShake.getLocation());
                    toShake = null;
                }
            } else {
                toShake = null;
                if (nextCivilian != null && dist > 3) {

                    System.out.println(myLocation);
                    System.out.println("Scorching " + nextCivilian + " | " + hasMoved);
                    if (nextCivilian.distanceTo(myLocation) - civSize > 5.4) {
                        if (!hasMoved && !tryMove(myLocation.directionTo(nextCivilian))) {
                            if (rc.canMove(myLocation.directionTo(nextCivilian), 0.5f)) {
                                rc.move(myLocation.directionTo(nextCivilian), 0.5f);
                                hasMoved = true;
                                System.out.println("i tried");
                            } else {

                                System.out.println("i failed");
                            }
                        } else {
                            hasMoved = true;

                            System.out.println("maybe?");
                        }
                    }
                    if (nextCivilian.distanceTo(myLocation) - civSize < 5.4) {
                        System.out.println("circling?");
                    /*if (rc.canFirePentadShot()) {
                        rc.firePentadShot(myLocation.directionTo(nextCivilian));
                    }else if (rc.canFireTriadShot()) {
                        rc.fireTriadShot(myLocation.directionTo(nextCivilian));
                    } else*/
                        if (checkLineOfFire(myLocation, nextCivilian, trees, nearbyRobots, RobotType.SCOUT.bodyRadius)) {
                            if (rc.canFireSingleShot()) {
                                rc.fireSingleShot(myLocation.directionTo(nextCivilian));
                            }
                        } else {
                            //System.out.println("Don't shoot");
                        }
                        Direction dir;
                        while (!hasMoved && Math.random() > 0.03f) {
                            if (circleDir > 0.5) {
                                dir = myLocation.directionTo(nextCivilian).rotateRightDegrees((float) Math.random() * 42 + 42);
                            } else {
                                dir = myLocation.directionTo(nextCivilian).rotateLeftDegrees((float) Math.random() * 42 + 42);
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
                        if (checkLineOfFire(myLocation, nextEnemy, trees, nearbyRobots, RobotType.SCOUT.bodyRadius)) {
                            if (rc.getTeamBullets() > 150 && rc.canFireSingleShot()) {
                                rc.fireSingleShot(myLocation.directionTo(nextEnemy));
                            }
                        } else {
                            System.out.println("Don't shoot");
                        }
                        if (!hasMoved) tryMove(nextEnemy.directionTo(myLocation));
                    } else {
                        //System.out.println("Moving towards enemy at distance " + dist);
                        if (!hasMoved) tryMove(myLocation.directionTo(nextEnemy));
                    }
                } else if (mag < 1e-20f) {
                    if (!hasMoved) {
                        while (!rc.canMove(lastDirection)) {
                            lastDirection = randomDirection();
                        }
                        rc.move(lastDirection);
                    }
                } else {
                    if (!hasMoved)
                        tryMove(new Direction(RobotType.SCOUT.strideRadius * fx / mag, RobotType.SCOUT.strideRadius * fy / mag));
                }
                if (toShake == null && isShaker) {
                    for (TreeInfo t : trees) {
                        if (t.getContainedBullets() > 10) {
                            toShake = t;
                            break;
                        }
                    }
                }
                if (rc.getRoundNum() - frame > 0 && frame % 8 != 0) {
                    System.out.println("Scout took " + (rc.getRoundNum() - frame) + " frames at " + frame);
                }
            }

        } catch (Exception e) {
            System.out.println("Scout Exception");
            e.printStackTrace();
        }
    }
}
