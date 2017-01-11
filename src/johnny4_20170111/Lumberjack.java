package johnny4_20170111;

import battlecode.common.*;

import static johnny4_20170111.Util.*;

public class Lumberjack {

    RobotController rc;
    johnny4_20170111.Map map;
    johnny4_20170111.Radio radio;
    MapLocation currentTreeTarget;
    Team enemy;
    Direction lastDirection = randomDirection();

    public Lumberjack(RobotController rc) {
        this.rc = rc;
        this.radio = new johnny4_20170111.Radio(rc);
        this.map = new johnny4_20170111.Map(rc, radio);
        this.enemy = rc.getTeam().opponent();
    }

    public void run() {
        while (true) {
            tick();
            Clock.yield();
        }
    }

    protected void tick() {
        try {
            if (rc.getTeamBullets() >= 10000f) {
                rc.donate(10000f);
            }

            int frame = rc.getRoundNum();
            MapLocation myLocation = rc.getLocation();
            if (frame % 8 == 0) {
                radio.reportMyPosition(myLocation);
            }
            RobotInfo nearbyRobots[] = rc.senseNearbyRobots();
            TreeInfo trees[] = rc.senseNearbyTrees();

            // acquire tree cutting requests
            if (currentTreeTarget == null) {
                currentTreeTarget = radio.findTreeToCut();
            }


            if (currentTreeTarget != null) {

                TreeInfo toBeCut = null;
                for (TreeInfo ti : trees) {
                    if (ti.location.distanceTo(currentTreeTarget) < 1) {
                        toBeCut = ti;
                    }
                }
                if (rc.canChop(currentTreeTarget)) {
                    rc.chop(currentTreeTarget);
                } else if (toBeCut != null) {
                    Direction toTree = myLocation.directionTo(currentTreeTarget);
                    tryMove(toTree);
                    if (rc.canChop(currentTreeTarget)) {
                        rc.chop(currentTreeTarget);
                    }
                } else {
                    Direction toTree = myLocation.directionTo(currentTreeTarget);
                    tryMove(toTree);
                }
                if (myLocation.distanceTo(currentTreeTarget) < 0.95 * RobotType.LUMBERJACK.sensorRadius && toBeCut == null) {
                    currentTreeTarget = null;
                }
                return;
            }

            // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
            int friendlies = 0;
            int enemies = 0;
            MapLocation nextEnemy = null;
            boolean treeenemy = false;
            boolean longrange = false;
            for (RobotInfo ri : nearbyRobots) {
                if (ri.location.distanceTo(myLocation) < RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.001f) {
                    if (ri.getTeam() == rc.getTeam()) {
                        friendlies++;
                    } else {
                        enemies++;
                    }
                }
                if (ri.getTeam() == rc.getTeam().opponent() && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > ri.location.distanceTo(myLocation))) {
                    nextEnemy = ri.location;
                }
            }

            for (TreeInfo ti : trees) {
                if (ti.team == rc.getTeam().opponent() && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > ti.location.distanceTo(myLocation))) {
                    nextEnemy = ti.location;
                    treeenemy = true;
                }
                if (rc.canChop(ti.location) && !(ti.getTeam() == rc.getTeam())){
                    rc.chop(ti.location);
                }
                if (ti.containedBullets > 0 && rc.canShake(ti.location)){
                    rc.shake(ti.location);
                }
            }
            if (nextEnemy == null) {
                longrange = true;
                nextEnemy = map.getTarget(myLocation);
                if (nextEnemy == null) {

                    nextEnemy = map.getTarget(myLocation, 0, 100);
                }
            }

            if (enemies > friendlies && !rc.hasAttacked()) {
                rc.strike();
            }
            boolean hasMoved = tryEvade();
            if (!hasMoved) {
                if (nextEnemy != null) {
                    Direction toe = myLocation.directionTo(nextEnemy);
                    if (!tryMove(toe)) {
                        if (rc.canMove(toe, 0.5f)) {
                            rc.move(toe, 0.5f);
                            hasMoved = true;
                        }
                    } else {
                        hasMoved = true;
                    }
                } else {
                    while (!rc.canMove(lastDirection) && Math.random() > 0.01) {
                        lastDirection = lastDirection.rotateRightDegrees((float) Math.random() * 60);
                    }
                    if (rc.canMove(lastDirection)) {
                        rc.move(lastDirection);
                    }
                }
            }

            if (rc.getRoundNum() - frame > 0 && !longrange) {
                System.out.println("Lumberjack took " + (rc.getRoundNum() - frame) + " frames at " + frame + " using longrange " + longrange);
            }

        } catch (Exception e) {
            System.out.println("Lumberjack Exception");
            e.printStackTrace();
        }
    }
}
