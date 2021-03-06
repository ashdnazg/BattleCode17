package johnny4_20170111_2;

import battlecode.common.*;

import java.util.Optional;

import static johnny4_20170111_2.Util.*;

public class Soldier {

    RobotController rc;
    Map map;
    Radio radio;

    public Soldier(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        stuckLocation = rc.getLocation();
        stuckSince = rc.getRoundNum();
    }

    public void run() {
        while (true) {
            tick();
            Clock.yield();
        }
    }

    float circleDir = 0f;
    MapLocation stuckLocation;
    int stuckSince;

    protected void tick() {
        try {
            if (rc.getTeamBullets() >= 10000f) {
                rc.donate(10000f);
            }

            int frame = rc.getRoundNum();
            radio.frame = frame;
            MapLocation myLocation = rc.getLocation();
            RobotInfo nearbyRobots[] = null;
            if (frame % 8 == 0) {
                radio.reportMyPosition(myLocation);
                nearbyRobots = map.sense();
            }
            if (nearbyRobots == null) {
                nearbyRobots = rc.senseNearbyRobots();
            }

            MapLocation nextEnemy = null;
            TreeInfo trees[] = rc.senseNearbyTrees();
            for (RobotInfo r : nearbyRobots) {
                if (!r.getTeam().equals(rc.getTeam()) && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > r.location.distanceTo(myLocation))) {
                    nextEnemy = r.location;
                }
            }

            if (myLocation.distanceTo(stuckLocation) > 5){
                stuckSince = frame;
                stuckLocation = myLocation;
            }
            if (rc.getRoundNum() - stuckSince > 50){
                //System.out.println("Stuck soldier reporting trees");
                stuckSince = 100000;
                for (TreeInfo t : trees){
                    if (t.getTeam().equals(rc.getTeam())) continue;
                    if (t.location.distanceTo(myLocation) < 5){
                        //System.out.println("Reported tree at " + t.location);
                        radio.requestTreeCut(t);
                    }
                }
            }

            boolean longrange = false;
            if (nextEnemy == null) {
                longrange = true;
//                nextEnemy = map.getTarget(myLocation);
                if (nextEnemy == null) {

                    nextEnemy = map.getTarget(myLocation, 0, 100, RobotType.SOLDIER.sensorRadius);
                }
            }
            BulletInfo[] bullets = rc.senseNearbyBullets();
            boolean hasMoved = tryEvade(bullets);
            myLocation = rc.getLocation();
            float dist = 10000f;
            if (nextEnemy != null) {
                dist = myLocation.distanceTo(nextEnemy);
                boolean hasFired = longrange;
                if (!hasFired && checkLineOfFire(myLocation, nextEnemy, trees, nearbyRobots, RobotType.SOLDIER.bodyRadius)) {
                    if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(myLocation.directionTo(nextEnemy));
                        hasFired = true;
                    }
                }
                if (dist < 0.5 * RobotType.SCOUT.sensorRadius) {
                    /*if (!hasFired && rc.getTeamBullets() > 400 && rc.canFirePentadShot()) {
                        rc.firePentadShot(myLocation.directionTo(nextEnemy));
                        hasFired = true;
                    }
                    if (!hasFired && rc.getTeamBullets() > 100 && rc.canFireTriadShot()) {
                        rc.fireTriadShot(myLocation.directionTo(nextEnemy));
                        hasFired = true;
                    }*/
                    if (!hasMoved && tryMove(nextEnemy.directionTo(myLocation))) {
                        hasMoved = true;
                        myLocation = rc.getLocation();
                    }


                } else {
                    if (!hasMoved) {
                        if (longrange) {
                            tryMove(myLocation.directionTo(nextEnemy));
                            myLocation = rc.getLocation();
                        } else {
                            Direction dir;
                            int tries = 0;
                            while (!hasMoved && tries++ < 30) {
                                if (circleDir > 0.5) {
                                    dir = myLocation.directionTo(nextEnemy).rotateRightDegrees(2 * tries + 50);
                                } else {
                                    dir = myLocation.directionTo(nextEnemy).rotateLeftDegrees(2 * tries + 50);
                                }
                                if (!hasMoved && rc.canMove(dir, 2f)) {
                                    rc.move(dir, 2f);
                                    hasMoved = true;
                                    myLocation = rc.getLocation();
                                } else {
                                    circleDir = (float) Math.random();
                                }
                            }
                        }

                    }
                }

                if (!hasFired && checkLineOfFire(myLocation, nextEnemy, trees, nearbyRobots, RobotType.SOLDIER.bodyRadius)) {
                    if (rc.canFireSingleShot()) {
                        rc.fireSingleShot(myLocation.directionTo(nextEnemy));
                        hasFired = true;
                    }
                }
            } else if (!hasMoved) {
                tryMove(randomDirection());
                myLocation = rc.getLocation();
            }
            if (rc.getRoundNum() - frame > 0) {
                //System.out.println("Soldier took " + (rc.getRoundNum() - frame) + " frames at " + frame + " using longrange " + longrange);
            }


        } catch (Exception e) {
            //System.out.println("Soldier Exception");
            //e.printStackTrace();
        }
    }
}
