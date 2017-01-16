package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Lumberjack {

    RobotController rc;
    Map map;
    Radio radio;
    Movement movement;
    MapLocation currentTreeTarget;
    Team enemy;
    Direction lastDirection = randomDirection();
    MapLocation myLocation;
    final float MIN_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
    RobotInfo guardener = null;
    RobotInfo[] nearbyRobots = new RobotInfo[0];
    MapLocation lastRandomLocation;
    TreeInfo[] trees;
    boolean fakeTask = true;

    public Lumberjack(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.enemy = rc.getTeam().opponent();
        this.movement = new Movement(rc);

        boolean hasLJ = false;
        for (RobotInfo ri : rc.senseNearbyRobots()) {
            if (ri.getTeam().equals(rc.getTeam())) {
                if (ri.type == RobotType.GARDENER) {
                    guardener = ri;
                }
                if (ri.type == RobotType.LUMBERJACK) {
                    hasLJ = true;
                }
            }
        }
        if (hasLJ || rand() > 0.2f) {
            guardener = null;
        }
        lastRandomLocation = map.archonPos[(int) (map.archonPos.length * rand())];
    }

    public void run() {
        while (true) {
            int frame = rc.getRoundNum();
            tick();
            if (frame != rc.getRoundNum()) {
                System.out.println("BYTECODE OVERFLOW");
            }
            Clock.yield();
        }
    }

    int treeInSenseSince = 100000;
    BulletInfo bullets[];

    protected void tick() {
        try {
            preTick();
            int frame = rc.getRoundNum();
            radio.frame = frame;
            myLocation = rc.getLocation();
            bullets = rc.senseNearbyBullets();
            nearbyRobots = map.sense();
            trees = senseClosestTrees();

            boolean alarm = radio.getAlarm();

            movement.init(nearbyRobots, trees, bullets);

            // acquire tree cutting requests
            if (currentTreeTarget == null || fakeTask) {
                MapLocation mp = radio.findClosestTreeToCut(myLocation);
                if (mp != null) {
                    currentTreeTarget = mp;
                    treeInSenseSince = 100000;
                    fakeTask = false;
                }
            }

            // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
            MapLocation nextEnemy = null;
            MapLocation nextTree = currentTreeTarget;
            float enemyRadius = 1;
            boolean longrange = false;
            for (RobotInfo ri : nearbyRobots) {
                if (guardener != null && guardener.getID() == ri.getID()) {
                    guardener = ri;
                }
                if (ri.getTeam().equals(rc.getTeam().opponent()) && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > ri.location.distanceTo(myLocation))) {
                    nextEnemy = ri.location;
                    enemyRadius = ri.type.bodyRadius;
                }
            }
            if (nextEnemy != null && fakeTask) {
                currentTreeTarget = null;
            }

            if (nextEnemy == null && !alarm && currentTreeTarget == null) {
                for (TreeInfo ti : trees) {
                    if (ti.team.equals(rc.getTeam())) continue;
                    if (nextTree == null || nextTree.distanceTo(myLocation) > ti.location.distanceTo(myLocation)) {
                        nextTree = ti.location;
                    }
                }
                if (nextTree != null) {
                    currentTreeTarget = nextTree;
                    treeInSenseSince = 100000;
                    fakeTask = true;
                }
            }
            if ((alarm || currentTreeTarget == null) && nextEnemy == null) {
                if (frame % 9 == 0) {
                    map.generateFarTargets(myLocation, 50, RobotType.LUMBERJACK.sensorRadius);
                }
                System.out.println("Using long range target");
                longrange = true;
                nextEnemy = map.getTarget(0, myLocation);
            }
            boolean hasStriked = tryStrike();
            boolean hasChopped = false;
            boolean hasMoved = false;
            System.out.println("STATUS enemy: " + nextEnemy + " tree: " + currentTreeTarget + " alarm: " + alarm);


            if (longrange && !hasChopped) hasChopped = tryChop();

            myLocation = rc.getLocation();
            if (guardener != null && !hasMoved && myLocation.distanceTo(guardener.location) > RobotType.LUMBERJACK.sensorRadius + 3) {
                movement.findPath(guardener.location, null); //protect your padawan
                hasMoved = true;
            }
            if (!alarm && currentTreeTarget != null) {
                System.out.println("Chopping tree at " + currentTreeTarget);
                rc.setIndicatorDot(currentTreeTarget, 0, 255, 255);
                //System.out.println("Going for " + currentTreeTarget);

                TreeInfo toBeCut = null;
                for (TreeInfo ti : trees) {
                    //System.out.println(ti.location + " -> " + currentTreeTarget + " : " + ti.location.distanceTo(currentTreeTarget));
                    if (ti.team.equals(rc.getTeam())) continue;
                    if (ti.location.distanceTo(currentTreeTarget) < 2.5 || (toBeCut != null && ti.location.distanceTo(currentTreeTarget) < toBeCut.location.distanceTo(currentTreeTarget))) {
                        toBeCut = ti;
                    }
                }
                if (rc.canChop(currentTreeTarget)) {
                    rc.chop(currentTreeTarget);
                    hasChopped = true;
                } else if (toBeCut != null) {
                    if (!hasMoved && movement.findPath(currentTreeTarget, null)) {
                        hasMoved = true;
                        myLocation = rc.getLocation();
                    }
                    if (rc.canChop(currentTreeTarget)) {
                        rc.chop(currentTreeTarget);
                        hasChopped = true;
                    }
                } else {
                    if (!hasMoved && movement.findPath(currentTreeTarget, null)) {
                        hasMoved = true;
                        myLocation = rc.getLocation();
                    }
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
            }


            TreeInfo choppable = null;
            for (TreeInfo ti : trees) {
                if (ti.team == rc.getTeam().opponent() && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > ti.location.distanceTo(myLocation))) {
                    nextEnemy = ti.location;
                    enemyRadius = ti.radius;
                }
                if (ti.containedBullets > 0 && rc.canShake(ti.location)) {
                    rc.shake(ti.location);
                }
            }

            myLocation = rc.getLocation();
            /*if (!hasStriked && nextLumberjack != null && nextLumberjack.distanceTo(myLocation) < MIN_LUMBERJACK_DIST && !hasMoved) {
                if (LJ_tryMove(nextLumberjack.directionTo(myLocation))) {
                    myLocation = rc.getLocation();
                    hasMoved = true;
                }
            }*/
            if (!hasMoved) {
                if (nextEnemy != null) {
                    if (movement.findPath(nextEnemy.add(nextEnemy.directionTo(myLocation), RobotType.LUMBERJACK.bodyRadius + enemyRadius + 0.0001f), null)) {
                        myLocation = rc.getLocation();
                    }
                    hasMoved = true;
                }
            }
            if (!hasMoved && !rc.hasAttacked() && !hasChopped && choppable == null) {

                System.out.println("Moving randomly");
                while (lastRandomLocation.distanceTo(myLocation) < 0.6 * RobotType.SCOUT.sensorRadius || !rc.onTheMap(myLocation.add(myLocation.directionTo(lastRandomLocation), 4)) || !movement.findPath(lastRandomLocation, null)) {
                    lastRandomLocation = myLocation.add(randomDirection(), 20);
                }
                hasMoved = true;
            }
            //try to strike again
            if (!hasStriked && !longrange) hasStriked = tryStrike();
            if (!hasChopped) hasChopped = tryChop();

            if (choppable != null && !rc.hasAttacked() && rc.canChop(choppable.location)) {
                rc.chop(choppable.location);
            }

            if (rc.getRoundNum() - frame > 0 && !longrange) {
                System.out.println("Lumberjack took " + (rc.getRoundNum() - frame) + " frames at " + frame + " using longrange " + longrange);
            }

        } catch (Exception e) {
            System.out.println("Lumberjack Exception");
            e.printStackTrace();
        }
    }

    private static int getWeight(RobotType rt) {
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

    private boolean tryStrike() throws GameActionException {
        if (rc.canStrike()) {
            int enemies, friendlies;
            enemies = friendlies = 0;
            for (RobotInfo ri : nearbyRobots) {
                if (ri.location.distanceTo(myLocation) < RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + ri.type.bodyRadius + 0.001f) {
                    if (ri.getTeam().equals(rc.getTeam())) {
                        friendlies += getWeight(ri.type);
                    } else {
                        enemies += getWeight(ri.type);
                    }
                }
            }
            if (1.1f * enemies > friendlies) {
                rc.strike();
                return true;
            }
        }
        return false;
    }

    private boolean tryChop() throws GameActionException {
        for (TreeInfo ti : trees) {
            if (!ti.getTeam().equals(rc.getTeam()) && rc.canChop(ti.location)) {
                rc.chop(ti.location);
                return true;
            }
        }
        return false;

    }
}
