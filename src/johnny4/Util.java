package johnny4;

import battlecode.common.*;

import java.util.Random;

import static johnny4.Radio.*;

public class Util {

    static RobotController rc;
    static Random rnd;
    static final boolean DEBUG = true;
    static final float PENTAD_ARC_PLUSMINUS = GameConstants.PENTAD_SPREAD_DEGREES * 2 / 180f * 3.14159265358979323f;
    static final float TRIAD_ARC_PLUSMINUS = GameConstants.TRIAD_SPREAD_DEGREES / 180f * 3.14159265358979323f;

    static boolean tooManyTrees = false;


    /**
     * Returns a random Direction
     *
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction(rand() * 2 * (float) Math.PI);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) {
        try {
            return tryMove(dir, 35, 3);
        } catch (Exception ex) {
            return false;
        }
    }

    static float rand() {
        if (rnd == null) {
            rnd = new Random(rc.getID() * rc.getRoundNum());
        }
        return rnd.nextFloat();
    }


    static void BYTECODE() {
        try {
            System.out.println("BYTECODE OVERFLOW");
            rc.setIndicatorLine(rc.getLocation().add(Direction.getNorth(), 30), rc.getLocation().add(Direction.getSouth(), 30), 255, 0, 0);
            rc.setIndicatorLine(rc.getLocation().add(Direction.getEast(), 30), rc.getLocation().add(Direction.getWest(), 30), 255, 0, 0);
        } catch (Exception ex) {
        }
    }
    static void BYTECODE2() {
        try {
            System.out.println("BYTECODE OVERFLOW");
            rc.setIndicatorLine(rc.getLocation().add(Direction.getNorth(), 30), rc.getLocation().add(Direction.getSouth(), 30), 255, 255, 0);
            rc.setIndicatorLine(rc.getLocation().add(Direction.getEast(), 30), rc.getLocation().add(Direction.getWest(), 30), 255, 255, 0);
        } catch (Exception ex) {
        }
    }
    static void EXCEPTION() {
        try {
            System.out.println("EXCEPTION");
            rc.setIndicatorLine(rc.getLocation().add(Direction.getNorth(), 30), rc.getLocation().add(Direction.getSouth(), 30), 0, 255, 0);
            rc.setIndicatorLine(rc.getLocation().add(Direction.getEast(), 30), rc.getLocation().add(Direction.getWest(), 30), 0, 255, 0);
        } catch (Exception ex) {
        }
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir           The intended direction of movement
     * @param degreeOffset  Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while (currentCheck <= checksPerSide) {
            // Try the offset of the left side
            try {
                Direction d = dir.rotateLeftDegrees(degreeOffset * currentCheck);
                if (rc.canMove(d)) {
                    rc.move(d);
                    return true;
                }
                // Try the offset on the right side
                d = dir.rotateRightDegrees(degreeOffset * currentCheck);
                if (rc.canMove(d)) {
                    rc.move(d);
                    return true;
                }
            } catch (Exception ex) {
                ex.printStackTrace();EXCEPTION();
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    static private float[] px = new float[25];
    static private float[] py = new float[25];
    static private float[] pr = new float[25];
    static private boolean[] pg = new boolean[25];


    static float getMaximumArcOfFire(MapLocation start, Direction toTarget, RobotInfo robots[], TreeInfo trees[]) throws GameActionException {
        Team myTeam = rc.getTeam();
        Team enemyTeam = myTeam.opponent();
        float maxPlusMinus = 3.14159265358979323f;
        for (int i = 0; i < robots.length; i++) {
            if (robots[i].team.equals(enemyTeam) || robots[i].type == RobotType.ARCHON && robots[i].health > 30) continue;
            maxPlusMinus = Math.min(maxPlusMinus, Math.abs(start.directionTo(robots[i].location).radiansBetween(toTarget)) - (robots[i].type.bodyRadius + 1) / (start.distanceTo(robots[i].location)));
        }/*
        for (int i = 0; i < trees.length; i++) {
            if (!trees[i].team.equals(myTeam)) continue;
            maxPlusMinus = Math.min(maxPlusMinus, Math.abs(start.directionTo(trees[i].location).radiansBetween(toTarget)));
        }*/
        return maxPlusMinus;
    }

    static boolean checkLineOfFire(MapLocation start, MapLocation target, TreeInfo[] trees, RobotInfo robots[], float shooterRadius) throws GameActionException {
        float cx = 0.5f * (start.x + target.x);
        float cy = 0.5f * (start.y + target.y);//first 2 variables have fast access
        double rs = 0.5 * (start.distanceTo(target) - shooterRadius);
        float x, y, r;
        int cnt = 0;
        int clock = Clock.getBytecodeNum();
        Team enemy = rc.getTeam().opponent();
        int i;
        RobotInfo robot;
        TreeInfo tree;
        if (rc.getTeamBullets() < 110) {
            for (i = 0; i < trees.length; i++) {
                if (cnt >= px.length || i > 4) break;
                tree = trees[i];
                x = tree.location.x;
                y = tree.location.y;
                r = tree.radius;
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) < (rs + r) * (rs + r)) {
                    px[cnt] = x;
                    py[cnt] = y;
                    pr[cnt] = r + 0.0001f;
                    pg[cnt++] = false;
                }
            }
        }
        for (i = 0; i < robots.length; i++) {
            if (cnt >= px.length || i > 4) break;
            robot = robots[i];
            x = robot.location.x;
            y = robot.location.y;
            r = robot.type.bodyRadius;
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) < (rs + r) * (rs + r)) {
                px[cnt] = x;
                py[cnt] = y;
                pr[cnt] = r + 0.0001f;
                pg[cnt++] = robot.team.equals(enemy);
            }
        }

        double toTrgX = target.x - start.x;
        double toTrgY = target.y - start.y;
        double mag = Math.sqrt(toTrgX * toTrgX + toTrgY * toTrgY);
        toTrgX /= mag;
        toTrgY /= mag;
        double dx, dy, distParallel, distPerpendicular, perpx, perpy;
        double mindist = 100000d;
        boolean outcome = true;
        if (DEBUG) rc.setIndicatorLine(start, target, 0, 255, 255);
        for (i = 0; i < cnt; i++) {
            dx = px[i] - start.x;
            dy = py[i] - start.y;
            distParallel = toTrgX * dx + toTrgY * dy;
            perpx = dx - distParallel * toTrgX;
            perpy = dy - distParallel * toTrgY;
            distPerpendicular = (perpx * perpx + perpy * perpy);
            if (distParallel > shooterRadius + GameConstants.BULLET_SPAWN_OFFSET && distPerpendicular < pr[i] * pr[i] && distParallel < mindist) {
                if (DEBUG) rc.setIndicatorDot(new MapLocation(px[i], py[i]), 0, 255, 255);
                if (DEBUG) System.out.println("Blocked at " + new MapLocation(px[i], py[i]) + " " + pg[i]);
                mindist = distParallel;
                outcome = pg[i];
            }
        }
        if (cnt >= px.length) {
            if (Util.DEBUG) System.out.println("Exhausted array length");
        }
        if (mindist > 99999d) {
            if (Util.DEBUG) System.out.println("No collision found");
            if (cnt >= px.length) {
                return false;
            }
        }
        clock = Clock.getBytecodeNum() - clock;
        if (clock > 300) {
            if (Util.DEBUG)
                System.out.println("Check LOF took " + clock + " evaluating " + cnt + " / " + (robots.length + trees.length) + " outcome " + outcome);
        }
        return outcome;
    }


static int predictionFactor = 0;

    static MapLocation predict(RobotInfo enemy, RobotInfo lastEnemy, float extratime) throws GameActionException {
        MapLocation nextEnemy = enemy.location;
        float enemyRadius = enemy.type.bodyRadius;
        float bulletspeed = rc.getType().bulletSpeed;
        MapLocation myLocation = rc.getLocation();
        if (lastEnemy != null && lastEnemy.location.distanceTo(enemy.location) < enemy.type.strideRadius + 0.2f) {

            float ux = enemy.location.x - lastEnemy.location.x;
            float uy = enemy.location.y - lastEnemy.location.y;

            float ABx = enemy.location.x - myLocation.x;
            float ABy = enemy.location.y - myLocation.y;

            float ABmag = (float) Math.sqrt(ABx * ABx + ABy * ABy);
            ABx /= ABmag;
            ABy /= ABmag;

            float uDotAB = ABx * ux + ABy * uy;
            float ujx = uDotAB * ABx;
            float ujy = uDotAB * ABy;

            float uix = ux - ujx;
            float uiy = uy - ujy;

            // float vix = uix;
            // float viy = uiy;

            float viMag = (float) Math.sqrt(uix * uix + uiy * uiy);
            float vjMag = (float) Math.sqrt(bulletspeed * bulletspeed - viMag * viMag);

            float vjx = ABx * vjMag;
            float vjy = ABy * vjMag;

            // float vx = vjx + vix;
            // float vy = vjy + viy;


            float time = vjy - ujy > 0.01f ? ((enemy.location.y - myLocation.y) / (vjy - ujy) + extratime) : ((enemy.location.x - myLocation.x) / (vjx - ujx) + extratime);
            // This factor is important!
            time *= (ABmag - enemyRadius) / ABmag;
            time *= (3 - predictionFactor) / 3.0f;
            predictionFactor = (predictionFactor + 1) % 4;

            float step = time * 0.5f;
            float initialStep = step;
            MapLocation testSpot;
            float sensorRadius = rc.getType().sensorRadius;

            Team myTeam = rc.getTeam();
            MapLocation lastGood = null;

            nextEnemy = new MapLocation(enemy.location.x + ux * time, enemy.location.y + uy * time);
            while (step > 0.5f) {
                if (nextEnemy.distanceTo(myLocation) >= sensorRadius) {
                    testSpot = myLocation.add(myLocation.directionTo(nextEnemy), sensorRadius - 0.01f);
                } else {
                    testSpot = nextEnemy;
                }
                if (!rc.onTheMap(testSpot)) {
                    time -= step;
                    step *= 0.5f;
                    nextEnemy = new MapLocation(enemy.location.x + ux * time, enemy.location.y + uy * time);
                    continue;
                } else {
                    TreeInfo ti = rc.senseTreeAtLocation(testSpot);
                    if (ti != null) {
                        time -= step;
                        step *= 0.5f;
                        nextEnemy = new MapLocation(enemy.location.x + ux * time, enemy.location.y + uy * time);
                        continue;
                    }
                    RobotInfo ri = rc.senseRobotAtLocation(testSpot);
                    if (ri != null && ri.team == myTeam) {
                        time -= step;
                        step *= 0.5f;
                        nextEnemy = new MapLocation(enemy.location.x + ux * time, enemy.location.y + uy * time);
                        continue;
                    }
                    if (step != initialStep) {
                        time += step;
                        step *= 0.5f;
                        lastGood = nextEnemy;
                        nextEnemy = new MapLocation(enemy.location.x + ux * time, enemy.location.y + uy * time);
                        continue;
                    } else {
                        lastGood = nextEnemy;
                        break;
                    }
                }
            }
            if (lastGood != null) {
                nextEnemy = lastGood;
            }

            if (Util.DEBUG) System.out.println("Time: " + time);
            if (Util.DEBUG) rc.setIndicatorLine(lastEnemy.location, enemy.location, 255, 255, 0);
            if (Util.DEBUG) rc.setIndicatorLine(enemy.location, nextEnemy, 255, 100, 0);
        }
        if (Util.DEBUG) rc.setIndicatorDot(nextEnemy, 255, 100, 0);
        return nextEnemy;
    }


    static TreeInfo[] temp = new TreeInfo[8];
    static TreeInfo[] cache = new TreeInfo[0];
    static boolean resense = false;

    static TreeInfo[] senseBiggestTrees() throws GameActionException {
        if (rc.getRoundNum() % 3 == 0 || cache.length == 0 || resense) {
            resense = false;
            int time = Clock.getBytecodeNum();
            int cnt = 0;
            TreeInfo trees[] = rc.senseNearbyTrees();
            TreeInfo ti;
            int len = Math.min(15, trees.length);
            int maxCnt = temp.length;
            int cnt2 = 0;

            boolean shaken = false;
            for (int i = 0; i < len && Clock.getBytecodeNum() - time < 1000; i++) {
                ti = trees[i];
                if (ti.radius > 0.1 && cnt < maxCnt) {
                    temp[cnt++] = ti;
                    if (ti.containedBullets > 0 || ti.containedRobot != null) cnt2++;
                } else if ((ti.containedBullets > 0|| ti.containedRobot != null) && cnt2 < maxCnt) {
                    temp[cnt2++] = ti;
                }
                if (ti.containedRobot != null) {
                    Radio.requestTreeCut(ti);
                }
                if (!shaken && ti.containedBullets > 0 && rc.canShake(ti.location)) {
                    rc.shake(ti.location);
                    resense = true;
                }
            }
            if (DEBUG) {
                for (int i = 0; i < cnt; i++) {
                    rc.setIndicatorDot(temp[i].location, 80, 200, 80);
                }
            }
            cache = new TreeInfo[cnt];
            System.arraycopy(temp, 0, cache, 0, cnt);
            time = Clock.getBytecodeNum() - time;
            Radio.cleanupClosestTreeToCut(rc.getLocation());
            if (time > 300) {
                if (Util.DEBUG) System.out.println("Sensing trees took " + time);

            }
            tooManyTrees = cache.length >= temp.length || time > 1000;

            if (Util.DEBUG) System.out.println("Reached maximum tree count: " + tooManyTrees);
        }
        return cache;
    }

    static TreeInfo[] temp2 = new TreeInfo[8];
    static TreeInfo[] cache2 = new TreeInfo[0];

    static TreeInfo[] senseClosestTrees() throws GameActionException {
        if (rc.getRoundNum() % 3 == 0 || cache2.length == 0|| resense) {
            resense = false;
            int time = Clock.getBytecodeNum();
            int cnt = 0;
            TreeInfo trees[] = rc.senseNearbyTrees();
            int length = Math.min(15, trees.length);
            int maxCnt = temp2.length;
            TreeInfo ti;
            boolean shaken = false;
            for (int i = 0; i < length && Clock.getBytecodeNum() - time < 1300; i++) {
                ti = trees[i];
                if (cnt < maxCnt) {
                    temp2[cnt++] = ti;
                }
                if (ti.containedRobot != null) {
                    Radio.requestTreeCut(ti);
                }
                if (!shaken && ti.containedBullets > 0 && rc.canShake(ti.location)) {
                    rc.shake(ti.location);
                    resense = true;
                }
            }
            cache2 = new TreeInfo[cnt];
            System.arraycopy(temp2, 0, cache2, 0, cnt);
            time = Clock.getBytecodeNum() - time;
            Radio.cleanupClosestTreeToCut(rc.getLocation());
            tooManyTrees = cache2.length >= temp2.length || time > 1300;
            if (time > 300) {
                if (Util.DEBUG) System.out.println("Sensing trees took " + time);
            }
            if (Util.DEBUG) System.out.println("Reached maximum tree count: " + tooManyTrees);

        }
        return cache2;
    }


    static RobotInfo[] temp3 = new RobotInfo[8];
    static RobotInfo[] cache3 = new RobotInfo[0];

    static RobotInfo[] senseClosestRobots() throws GameActionException {
        int time = Clock.getBytecodeNum();
        int cnt = 0;
        RobotInfo robots[] = rc.senseNearbyRobots();
        RobotInfo ri;

        int len = robots.length;
        int maxCnt = temp3.length;
        int cnt2 = 0;
        for (int i = 0; i < len && Clock.getBytecodeNum() - time < 800; i++) {
            ri = robots[i];
            if (cnt < maxCnt) {
                temp3[cnt++] = ri;
            } else if (ri.type == RobotType.GARDENER && cnt2 < maxCnt / 2) {
                temp3[cnt2++] = ri;
            }
        }
        cache3 = new RobotInfo[cnt];
        System.arraycopy(temp3, 0, cache3, 0, cnt);
        time = Clock.getBytecodeNum() - time;
        if (time > 300) {
            if (Util.DEBUG) System.out.println("Sensing robots took " + time);
        }
        if (cache3.length >= temp3.length) {
            if (Util.DEBUG) System.out.println("Reached maximum robots count");
        }
        return cache3;
    }

    /**
     * A slightly more complicated example function, this returns true if the given bullet is on a collision
     * course with the current robot. Doesn't take into account objects between the bullet and this robot.
     *
     * @param bullet The bullet in question
     * @return True if the line of the bullet's path intersects with this robot's current position.
     */

    static float distToRobot, theta, parallel;

    static float willCollideWithMe(MapLocation myLocation, BulletInfo bullet) {

        // Get relevant bullet information

        // Calculate bullet relations to this robot
        distToRobot = bullet.location.distanceTo(myLocation);
        theta = bullet.dir.radiansBetween(bullet.location.directionTo(myLocation));

        if (distToRobot < rc.getType().bodyRadius) return 0f;
        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI / 2) {
            return -1f;
        }
        parallel = (float) Math.abs(distToRobot * Math.sin(theta)) / rc.getType().bodyRadius;

        //System.out.println(myLocation + ": " + parallel + " -> " + (parallel * 2 + distToRobot));
        if ((parallel <= 1)) {
            return parallel * 2 + rc.getLocation().distanceTo(bullet.location);
        }
        return -1f;
    }

    static boolean fireAllowed = true;
    static final float q = 2987.5f / 3000f;
    static final float q_1 = q - 1.0f;
    static final float log_q = (float) Math.log(q);


    static void preTick() throws GameActionException {
        // Find out if we're the first unit to run this round
        Radio.frame = rc.getRoundNum();
        Radio.keepAlive();

        // VP stuff is here so only the first unit processes them
        int income = rc.getTreeCount();
        float currentVPCost = rc.getVictoryPointCost();
        float currentVPIncome = income / currentVPCost;
        int requiredVPs = (1000 - rc.getTeamVictoryPoints());

        if (rc.getTeamBullets() / currentVPCost >= requiredVPs) {
            rc.donate(((int) (rc.getTeamBullets() / currentVPCost)) * currentVPCost);
        } else if (!fireAllowed) {
            if (Util.DEBUG) System.out.println("There are " + Radio.countActiveGardeners() + " active gardeners");
            float bulletsToDonate = rc.getTeamBullets() - Radio.countActiveGardeners() * GameConstants.BULLET_TREE_COST;
            if (bulletsToDonate > 0.0f) {
                rc.donate(((int) (bulletsToDonate / currentVPCost)) * currentVPCost);
            }
        } else {
            float bulletsToDonate = (rc.getTeamBullets() - Radio.countActiveGardeners() * GameConstants.BULLET_TREE_COST) / currentVPCost;
            if (bulletsToDonate > 0.0f) {
                requiredVPs -= ((int) (bulletsToDonate / currentVPCost)) * currentVPCost;
                float logArg = (requiredVPs * (q_1) + currentVPIncome) / currentVPIncome;
                if (logArg > 0.0f) {
                    float turnsToWin = (float) (Math.log(logArg) / log_q);
                    if (Util.DEBUG) System.out.println("Winning expected in " + turnsToWin + " rounds");
                    if (Util.DEBUG) System.out.println("currentVPIncome " + currentVPIncome);
                    if (Util.DEBUG) System.out.println("up " + Math.log((requiredVPs * (q_1) + currentVPIncome) / currentVPIncome));
                    if (Util.DEBUG) System.out.println("down " + log_q);
                    if (turnsToWin < 200.0f) {
                        if (Util.DEBUG) System.out.println("Victory expected 200");
                        rc.donate(((int) (bulletsToDonate / currentVPCost)) * currentVPCost);
                        fireAllowed = false;
                    } else if (turnsToWin < 400.0f && !Radio.getLandContact()) {
                        if (Util.DEBUG) System.out.println("Victory expected 400");
                        rc.donate(((int) (bulletsToDonate / currentVPCost)) * currentVPCost);
                        fireAllowed = false;
                    }
                }
            }
        }
        if (rc.getRoundLimit() - rc.getRoundNum() < 200) {
            if (Util.DEBUG) System.out.println("GameEnd expected 200");
            fireAllowed = false;
        } else if (rc.getRoundLimit() - rc.getRoundNum()  < 400 && !Radio.getLandContact()) {
            if (Util.DEBUG) System.out.println("GameEnd expected 400");
            fireAllowed = false;
        }
        // if () {
            // rc.donate(((int) (rc.getTeamBullets() / currentVPCost)) * currentVPCost);
            // fireAllowed = false;
        // }
    }
}
