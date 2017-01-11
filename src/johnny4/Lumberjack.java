package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Lumberjack {

    RobotController rc;
    Map map;
    Radio radio;
    MapLocation currentTreeTarget;
    Team enemy;
    Direction lastDirection = randomDirection();

    public Lumberjack(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.enemy = rc.getTeam().opponent();
    }

    public void run() {
        while (true) {
            tick();
            Clock.yield();
        }
    }

    private boolean canMove(Direction dir){
        MapLocation nloc = rc.getLocation().add(dir, RobotType.SCOUT.strideRadius);
        float br = rc.getType().bodyRadius;
        for (BulletInfo bi : bullets){
            if (bi.location.distanceTo(nloc) < br){
                return false;
            }
        }
        return rc.canMove(dir);
    }
    private boolean canMove(Direction dir, float dist){
        try {
            MapLocation nloc = rc.getLocation().add(dir, dist);
            float br = rc.getType().bodyRadius;
            for (BulletInfo bi : bullets) {
                if (bi.location.distanceTo(nloc) < br) {
                    return false;
                }
            }
            return rc.canMove(dir, dist);
        }catch(Exception ex){

            System.out.println("canMove exception with args " + dir + ": " + dist);
            ex.printStackTrace();
            return false;
        }
    }

    int treeInSenseSince = 100000;
    BulletInfo bullets[];

    protected void tick() {
        try {
            if (rc.getTeamBullets() >= 10000f) {
                rc.donate(10000f);
            }

            int frame = rc.getRoundNum();
            radio.frame = frame;
            MapLocation myLocation = rc.getLocation();
            bullets = rc.senseNearbyBullets();
            RobotInfo nearbyRobots[] = null;
            if (frame % 8 == 0) {
                radio.reportMyPosition(myLocation);
                nearbyRobots = map.sense();
            }
            if (nearbyRobots == null) {
                nearbyRobots = rc.senseNearbyRobots();
            }
            TreeInfo trees[] = rc.senseNearbyTrees();

            boolean alarm = radio.getAlarm();

            // acquire tree cutting requests
            if (currentTreeTarget == null) {
                currentTreeTarget = radio.findTreeToCut();
                treeInSenseSince = 100000;
            }


            if (!alarm && currentTreeTarget != null) {

                TreeInfo toBeCut = null;
                for (TreeInfo ti : trees) {
                    //System.out.println(ti.location + " -> " + currentTreeTarget + " : " + ti.location.distanceTo(currentTreeTarget));
                    if (ti.location.distanceTo(currentTreeTarget) < 2.5 || (toBeCut != null && ti.location.distanceTo(currentTreeTarget) < toBeCut.location.distanceTo(currentTreeTarget))) {
                        toBeCut = ti;
                    }
                }
                if (rc.canChop(currentTreeTarget)) {
                    rc.chop(currentTreeTarget);
                } else if (toBeCut != null) {
                    Direction toTree = myLocation.directionTo(currentTreeTarget);
                    LJ_tryMove(toTree);
                    if (rc.canChop(currentTreeTarget)) {
                        rc.chop(currentTreeTarget);
                    }
                } else {
                    Direction toTree = myLocation.directionTo(currentTreeTarget);
                    LJ_tryMove(toTree);
                }
                if (myLocation.distanceTo(currentTreeTarget) < RobotType.LUMBERJACK.sensorRadius) {
                    if (treeInSenseSince > frame)
                        treeInSenseSince = frame;
                } else {
                    treeInSenseSince = 100000;
                }
                if (frame - treeInSenseSince > 10 && toBeCut == null) {
                    radio.reportTreeCut(currentTreeTarget);
                    System.out.println("Cut tree at " + currentTreeTarget);
                    currentTreeTarget = null;
                    treeInSenseSince = 100000;
                }
                return;
            }

            // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
            int friendlies = 0;
            int enemies = 0;
            MapLocation nextEnemy = null;
            float enemyRadius = 1;
            boolean treeenemy = false;
            boolean longrange = false;
            for (RobotInfo ri : nearbyRobots) {
                if (ri.location.distanceTo(myLocation) < RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + ri.type.bodyRadius + 0.001f) {
                    if (ri.getTeam().equals(rc.getTeam())) {
                        friendlies+= getWeight(ri.type);
                    } else {
                        enemies+= getWeight(ri.type);
                    }
                }
                if (ri.getTeam() == rc.getTeam().opponent() && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > ri.location.distanceTo(myLocation))) {
                    nextEnemy = ri.location;
                    enemyRadius = ri.type.bodyRadius;
                }
            }

            boolean hasChopped = false;
            for (TreeInfo ti : trees) {
                if (ti.team == rc.getTeam().opponent() && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > ti.location.distanceTo(myLocation))) {
                    nextEnemy = ti.location;
                    enemyRadius = ti.radius;
                    treeenemy = true;
                }
                if (rc.canChop(ti.location) && !(ti.getTeam() == rc.getTeam())) {
                    rc.chop(ti.location);
                    hasChopped = true;
                }
                if (ti.containedBullets > 0 && rc.canShake(ti.location)) {
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
            boolean hasMoved = tryEvade(bullets);
            myLocation = rc.getLocation();
            if (!hasMoved) {
                if (nextEnemy != null) {
                    Direction toe = myLocation.directionTo(nextEnemy);
                    if (canMove(toe, Math.min(RobotType.LUMBERJACK.strideRadius - 0.001f, myLocation.distanceTo(nextEnemy) - RobotType.LUMBERJACK.bodyRadius - enemyRadius - 0.01f))) {
                        rc.move(toe,  Math.min(RobotType.LUMBERJACK.strideRadius - 0.001f, myLocation.distanceTo(nextEnemy)- RobotType.LUMBERJACK.bodyRadius - enemyRadius - 0.01f));
                        hasMoved = true;
                        myLocation = rc.getLocation();
                    }else
                    if (!LJ_tryMove(toe)) {
                        if (canMove(toe, 0.5f)) {
                            rc.move(toe, 0.5f);
                            hasMoved = true;
                            myLocation = rc.getLocation();
                        }
                    } else {
                        hasMoved = true;
                        myLocation = rc.getLocation();
                    }
                }
            }
            if (!hasMoved && !rc.hasAttacked() && !hasChopped) {
                while (!canMove(lastDirection) && Math.random() > 0.1) {
                    lastDirection = lastDirection.rotateRightDegrees((float) Math.random() * 60);
                }
                if (canMove(lastDirection)) {
                    rc.move(lastDirection);
                    hasMoved = true;
                    myLocation = rc.getLocation();
                }
            }
            //try to strike again
            if (!rc.hasAttacked()) {
                for (RobotInfo ri : nearbyRobots) {
                    if (ri.location.distanceTo(myLocation) < RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + ri.type.bodyRadius + 0.001f) {
                        if (ri.getTeam().equals(rc.getTeam())) {
                            friendlies+= getWeight(ri.type);
                        } else {
                            enemies+= getWeight(ri.type);
                        }
                    }
                }
                if (enemies > friendlies) {
                    rc.strike();
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

    private static int getWeight(RobotType rt){
        switch (rt) {
            case SCOUT:
                return 4;
            case ARCHON:
                return 1;
            case GARDENER:
                return 3;
            case LUMBERJACK:
                return 2;
            case SOLDIER:
                return 2;
            case TANK:
                return 1;
        }
        return 0;
    }

    boolean LJ_tryMove(Direction dir) {
        try {
            return LJ_tryMove(dir, 42, 2);
        } catch (Exception ex) {
            return false;
        }
    }

    boolean LJ_tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        if (canMove(dir)) {
            rc.move(dir);
            return true;
        }

        boolean moved = false;
        int currentCheck = 1;

        while (currentCheck <= checksPerSide) {
            try {
                Direction d = dir.rotateLeftDegrees(degreeOffset * currentCheck);
                if (canMove(d)) {
                    rc.move(d);
                    return true;
                }
                d = dir.rotateRightDegrees(degreeOffset * currentCheck);
                if (canMove(d)) {
                    rc.move(d);
                    return true;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }
}
