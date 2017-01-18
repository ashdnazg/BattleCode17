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
    final float MIN_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
    RobotInfo guardener = null;
    int guardenerID = -1;
    RobotInfo[] nearbyRobots = new RobotInfo[0];
    MapLocation lastRandomLocation;
    TreeInfo[] trees;
    boolean fakeTask = true;
    MapLocation myLocation;
    final static float MIN_GUARDENER_DIST = RobotType.LUMBERJACK.sensorRadius + 0;
    final static float MIN_SCOUT_SHOOT_RANGE = 5.0f;

    public Lumberjack(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.enemy = rc.getTeam().opponent();
        this.movement = new Movement(rc);

        boolean hasGuardener = false;
        for (RobotInfo ri : rc.senseNearbyRobots()) {
            if (ri.getTeam().equals(rc.getTeam())) {
                if (ri.type == RobotType.GARDENER) {
                    guardener = ri;
                    guardenerID = ri.ID;
                }
                if (ri.type == RobotType.LUMBERJACK || ri.type == RobotType.SOLDIER) {
                    hasGuardener = true;
                }
            }
        }

        myLocation = rc.getLocation();
        float dist = 1e10f;
        float curDist;
        for (MapLocation archonPos : map.enemyArchonPos) {
            curDist = archonPos.distanceTo(myLocation);
            if (curDist < dist) {
                lastRandomLocation = archonPos;
                dist = curDist;
            }
        }

        if (hasGuardener || dist < 45f) {
            guardener = null;
            guardenerID = -1;
        }
    }

    public void run() {
        while (true) {
            int frame = rc.getRoundNum();
            tick();
            if (frame != rc.getRoundNum()) {
                BYTECODE();
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
                if (mp != null && (guardener == null || mp.distanceTo(guardener.location) < MIN_GUARDENER_DIST)) {
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
            guardener = null;
            boolean hasGuardener = false;
            for (RobotInfo ri : nearbyRobots) {
                if (ri.getTeam().equals(rc.getTeam())) {
                    if (guardenerID == ri.ID) {
                        guardener = ri;
                    } else if (ri.type == RobotType.GARDENER) {
                        guardener = ri;
                        guardenerID = ri.ID;
                    }
                    if (ri.type == RobotType.SOLDIER) {
                        hasGuardener = true;
                    }
                }
                if (ri.getTeam().equals(rc.getTeam().opponent()) && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > ri.location.distanceTo(myLocation))) {
                    nextEnemy = ri.location;
                    enemyRadius = ri.type.bodyRadius;
                }
            }
            if (guardener == null || hasGuardener) {
                guardenerID = -1;
                guardener = null;
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
                    if (ti.containedBullets > 0 && rc.canShake(ti.location)) {
                        rc.shake(ti.location);
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
                if (Util.DEBUG) System.out.println("Using long range target");
                longrange = true;
                nextEnemy = map.getTarget(0, myLocation);
                if (nextEnemy != null && nextEnemy.distanceTo(myLocation) < 0.6 * RobotType.LUMBERJACK.sensorRadius) {
                    Radio.deleteEnemyReport(nextEnemy);
                }
            }
            boolean hasStriked = tryStrike();
            boolean hasChopped = false;
            boolean hasMoved = false;
            if (Util.DEBUG)
                System.out.println("STATUS enemy: " + nextEnemy + " tree: " + currentTreeTarget + " alarm: " + alarm);


            if ((longrange || nextEnemy == null || nextEnemy.distanceTo(myLocation) > 5) && !hasChopped) hasChopped = tryChop();

            if (guardener != null) {
                if (myLocation.distanceTo(guardener.location) > MIN_GUARDENER_DIST) {
                    movement.findPath(guardener.location, null); //protect your padawan
                    hasMoved = true;
                    if (Util.DEBUG) System.out.println("Returning to guardener");
                }
                if (nextEnemy != null && nextEnemy.distanceTo(guardener.location) > MIN_GUARDENER_DIST && nextEnemy.distanceTo(myLocation) > MIN_SCOUT_SHOOT_RANGE) {
                    nextEnemy = null;
                }
            }

            if (!alarm && currentTreeTarget != null) {
                if (Util.DEBUG) System.out.println("Chopping tree at " + currentTreeTarget);
                if (Util.DEBUG) rc.setIndicatorDot(currentTreeTarget, 0, 255, 255);
                //if (Util.DEBUG) System.out.println("Going for " + currentTreeTarget);

                TreeInfo toBeCut = null;
                for (TreeInfo ti : trees) {
                    //if (Util.DEBUG) System.out.println(ti.location + " -> " + currentTreeTarget + " : " + ti.location.distanceTo(currentTreeTarget));
                    if (ti.team.equals(rc.getTeam())) continue;
                    if (ti.location.distanceTo(currentTreeTarget) < 1.5 + ti.radius || (toBeCut != null && ti.location.distanceTo(currentTreeTarget) < toBeCut.location.distanceTo(currentTreeTarget))) {
                        toBeCut = ti;
                    }
                }

                if (toBeCut != null) {
                    if (rc.canChop(toBeCut.location)) {
                        rc.chop(toBeCut.location);
                        hasChopped = true;
                    }
                    if (!hasMoved && movement.findPath(toBeCut.location.add(toBeCut.location.directionTo(myLocation), toBeCut.radius + RobotType.LUMBERJACK.bodyRadius + 0.001f), null)) {
                        myLocation = rc.getLocation();
                    }
                    hasMoved = true;
                    if (rc.canChop(toBeCut.location)) {
                        rc.chop(toBeCut.location);
                        hasChopped = true;
                        if (Util.DEBUG) System.out.println("Chopped tobecut after moving");
                    }
                } else {
                    if (Util.DEBUG) System.out.println("No corresponding tree found");
                    if (!hasMoved && movement.findPath(currentTreeTarget, null)) {
                        myLocation = rc.getLocation();
                    }
                    hasMoved = true;
                }
                if (myLocation.distanceTo(currentTreeTarget) < 4 || toBeCut != null && toBeCut.location.distanceTo(currentTreeTarget) < 4 && myLocation.distanceTo(toBeCut.location) - toBeCut.radius < 3) {
                    if (treeInSenseSince > frame)
                        treeInSenseSince = frame;
                } else {
                    treeInSenseSince = 100000;
                }
                if (frame - treeInSenseSince > 10 && toBeCut == null) {
                    radio.reportTreeCut(currentTreeTarget);
                    if (Util.DEBUG) System.out.println("Cut tree at " + currentTreeTarget);
                    currentTreeTarget = null;
                    treeInSenseSince = 100000;
                }
            }


            TreeInfo choppable = null;
            for (TreeInfo ti : trees) {
                if (ti.team.equals(rc.getTeam().opponent()) && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > ti.location.distanceTo(myLocation))) {
                    nextEnemy = ti.location;
                    enemyRadius = ti.radius;
                    if (Util.DEBUG) System.out.println("Targeting enemy tree");
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
                    if (Util.DEBUG) System.out.println("Moving to next enemy at " + nextEnemy);
                    if (movement.findPath(nextEnemy.add(nextEnemy.directionTo(myLocation), RobotType.LUMBERJACK.bodyRadius + enemyRadius + GameConstants.BULLET_SPAWN_OFFSET - 0.001f), null)) {
                        myLocation = rc.getLocation();
                    }
                    hasMoved = true;
                }
            }
            if (!hasMoved && !rc.hasAttacked() && !hasChopped && choppable == null && guardener == null) {

                if (Util.DEBUG) System.out.println("Moving randomly");
                while (lastRandomLocation.distanceTo(myLocation) < 0.6 * RobotType.SCOUT.sensorRadius || !rc.onTheMap(myLocation.add(myLocation.directionTo(lastRandomLocation), 4)) || !movement.findPath(lastRandomLocation, null)) {
                    lastRandomLocation = myLocation.add(randomDirection(), 20);
                }
                hasMoved = true;
            }
            if (!hasMoved && guardener != null) {
                movement.findPath(guardener.location, null); //protect your padawan
                hasMoved = true;
                if (Util.DEBUG) System.out.println("Idle, Returning to guardener");
            }
            //try to strike again
            if (!hasStriked && !longrange) hasStriked = tryStrike();
            if (!hasChopped) hasChopped = tryChop();

            if (choppable != null && !rc.hasAttacked() && rc.canChop(choppable.location)) {
                rc.chop(choppable.location);
            }

            if (rc.getRoundNum() - frame > 0 && !longrange) {
                if (Util.DEBUG)
                    System.out.println("Lumberjack took " + (rc.getRoundNum() - frame) + " frames at " + frame + " using longrange " + longrange);
            }

        } catch (
                Exception e
                )

        {
            if (Util.DEBUG) System.out.println("Lumberjack Exception");
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
            boolean madeContact = false;
            for (RobotInfo ri : nearbyRobots) {
                if (ri.location.distanceTo(myLocation) < RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + ri.type.bodyRadius + 0.001f) {
                    if (ri.getTeam().equals(rc.getTeam())) {
                        friendlies += getWeight(ri.type);
                    } else {
                        enemies += getWeight(ri.type);
                        madeContact = madeContact || ri.type != RobotType.SCOUT;
                    }
                }
            }
            if (madeContact) {
                Radio.reportContact();
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
