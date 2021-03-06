package johnny4_20170111;

import battlecode.common.*;

import static johnny4_20170111.Util.*;

public class Soldier {

    RobotController rc;
    johnny4_20170111.Map map;
    johnny4_20170111.Radio radio;

    public Soldier(RobotController rc) {
        this.rc = rc;
        this.radio = new johnny4_20170111.Radio(rc);
        this.map = new johnny4_20170111.Map(rc, radio);
    }

    public void run() {
        while (true) {
            tick();
            Clock.yield();
        }
    }

    float circleDir = 0f;

    protected void tick() {
        try {
            if (rc.getTeamBullets() >= 10000f) {
                rc.donate(10000f);
            }

            int frame = rc.getRoundNum();
            MapLocation myLocation = rc.getLocation();
            if (frame % 8 == 0){
                radio.reportMyPosition(myLocation);
            }


            MapLocation nextEnemy = null;
            RobotInfo nearbyRobots[] = rc.senseNearbyRobots();
            TreeInfo trees[] = rc.senseNearbyTrees();
            for (RobotInfo r : nearbyRobots) {
                if (!r.getTeam().equals(rc.getTeam()) && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > r.location.distanceTo(myLocation))) {
                    nextEnemy = r.location;
                }
            }
            boolean longrange = false;
            if (nextEnemy == null) {
                longrange = true;
                nextEnemy = map.getTarget(myLocation);
                if (nextEnemy == null) {

                    nextEnemy = map.getTarget(myLocation, 0, 100);
                }
            }
            boolean hasMoved = tryEvade();
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
                    if (!hasMoved && tryMove(nextEnemy.directionTo(myLocation))){
                        hasMoved = true;
                    }



                } else {
                    if (!hasMoved) {
                        if (longrange) {
                            tryMove(myLocation.directionTo(nextEnemy));
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
            }
            if (rc.getRoundNum() - frame > 0 && !longrange) {
                //System.out.println("Soldier took " + (rc.getRoundNum() - frame) + " frames at " + frame + " using longrange " + longrange);
            }


        } catch (Exception e) {
            //System.out.println("Soldier Exception");
            e.printStackTrace();
        }
    }
}
