package johnny4_sprint;

import battlecode.common.*;
import johnny4_sprint.Radio;

import java.util.Random;

import static johnny4_sprint.Radio.*;

public class Util {

    static RobotController rc;
    static Random rnd;
    static final boolean DEBUG = false;

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
                ex.printStackTrace();
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


    static boolean checkLineOfFire(MapLocation start, MapLocation target, TreeInfo[] trees, RobotInfo robots[], float shooterRadius) throws GameActionException{
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
        for (i = 0; i < trees.length; i++) {
            if (cnt >= px.length) break;
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
        for (i = 0; i < robots.length; i++) {
            if (cnt >= px.length) break;
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
            if (johnny4_sprint.Util.DEBUG) System.out.println("Exhausted array length");
        }
        if (mindist > 99999d) {
            if (johnny4_sprint.Util.DEBUG) System.out.println("No collision found");
            if (cnt >= px.length) {
                return false;
            }
        }
        clock = Clock.getBytecodeNum() - clock;
        if (clock > 300) {
            if (johnny4_sprint.Util.DEBUG)
                System.out.println("Check LOF took " + clock + " evaluating " + cnt + " / " + (robots.length + trees.length) + " outcome " + outcome);
        }
        return outcome;
    }


    static MapLocation predict(RobotInfo enemy, RobotInfo lastEnemy) throws GameActionException {
        MapLocation nextEnemy = enemy.location;
        if (lastEnemy != null && lastEnemy.getID() == enemy.getID()) {
            float dx = enemy.location.x - lastEnemy.location.x;
            float dy = enemy.location.y - lastEnemy.location.y;
            float time = (rc.getLocation().distanceTo(enemy.location) - enemy.type.bodyRadius - rc.getType().bodyRadius) / rc.getType().bulletSpeed;
            if (johnny4_sprint.Util.DEBUG) System.out.println(time + ": " + dx + "|" + dy);
            if (johnny4_sprint.Util.DEBUG) System.out.println("From " + lastEnemy.location + " to " + enemy.location);
            nextEnemy = new MapLocation(enemy.location.x + dx * time, enemy.location.y + dy * time);
            if (johnny4_sprint.Util.DEBUG) rc.setIndicatorLine(lastEnemy.location, enemy.location, 255, 255, 0);
            if (johnny4_sprint.Util.DEBUG) rc.setIndicatorLine(enemy.location, nextEnemy, 255, 100, 0);
        }
        if (johnny4_sprint.Util.DEBUG) rc.setIndicatorDot(nextEnemy, 255, 100, 0);
        return nextEnemy;
    }


    static TreeInfo[] temp = new TreeInfo[8];
    static TreeInfo[] cache = new TreeInfo[0];

    static TreeInfo[] senseBiggestTrees() throws GameActionException {
        if (rc.getRoundNum() % 3 == 0 || cache.length == 0) {
            int time = Clock.getBytecodeNum();
            int cnt = 0;
            TreeInfo trees[] = rc.senseNearbyTrees();
            if (trees.length > 50) {
                trees = rc.senseNearbyTrees(4);
            }
            TreeInfo ti;
            int len = trees.length;
            int maxCnt = temp.length;
            int cnt2 = 0;

            for (int i = 0; i < len; i++) {
                ti = trees[i];
                if (ti.radius > 0.9 && cnt < maxCnt) {
                    temp[cnt++] = ti;
                } else if (ti.containedBullets > 0 && cnt2 < maxCnt) {
                    temp[cnt2++] = ti;
                }
                if (ti.containedRobot != null) {
                    Radio.requestTreeCut(ti);
                }
            }
            cache = new TreeInfo[cnt];
            System.arraycopy(temp, 0, cache, 0, cnt);
            time = Clock.getBytecodeNum() - time;
            if (time > 300) {
                if (johnny4_sprint.Util.DEBUG) System.out.println("Sensing trees took " + time);
            }
            if (cache.length >= temp.length) {
                if (johnny4_sprint.Util.DEBUG) System.out.println("Reached maximum tree count");
            }
        }
        return cache;
    }

    static TreeInfo[] temp2 = new TreeInfo[8];
    static TreeInfo[] cache2 = new TreeInfo[0];

    static TreeInfo[] senseClosestTrees() throws GameActionException {
        if (rc.getRoundNum() % 3 == 0 || cache2.length == 0) {
            int time = Clock.getBytecodeNum();
            int cnt = 0;
            TreeInfo trees[] = rc.senseNearbyTrees();
            int length = trees.length;
            int maxCnt = temp2.length;
            TreeInfo ti;
            for (int i = 0; i < length; i++) {
                ti = trees[i];
                if (cnt < maxCnt) {
                    temp2[cnt++] = ti;
                }
                if (ti.containedRobot != null) {
                    Radio.requestTreeCut(ti);
                }
            }
            cache2 = new TreeInfo[cnt];
            System.arraycopy(temp2, 0, cache2, 0, cnt);
            time = Clock.getBytecodeNum() - time;
            if (time > 300) {
                if (johnny4_sprint.Util.DEBUG) System.out.println("Sensing trees took " + time);
            }
            if (cache2.length >= temp2.length) {
                if (johnny4_sprint.Util.DEBUG) System.out.println("Reached maximum tree count");
            }
        }
        return cache2;
    }



    static RobotInfo[] temp3 = new RobotInfo[8];
    static RobotInfo[] cache3 = new RobotInfo[0];

    static RobotInfo[] senseClosestEnemies() throws GameActionException {
        int time = Clock.getBytecodeNum();
        int cnt = 0;
        RobotInfo robots[] = rc.senseNearbyRobots();
        RobotInfo ri;

        int len = robots.length;
        int maxCnt = temp3.length;
        int cnt2 = 0;
        for (int i = 0; i < len; i++) {
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
            if (johnny4_sprint.Util.DEBUG) System.out.println("Sensing robots took " + time);
        }
        if (cache3.length >= temp3.length) {
            if (johnny4_sprint.Util.DEBUG) System.out.println("Reached maximum robots count");
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

    static boolean willCollideWithMe(MapLocation myLocation, BulletInfo bullet) {

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI / 2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float) Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }

    static boolean fireAllowed = true;

    static void preTick() throws GameActionException {

        // Find out if we're the first unit to run this round
        Radio.frame = rc.getRoundNum();
        Radio.keepAlive();

        if (rc.getTeamBullets() >= 10000f) {
            rc.donate(10000f);
        }
        int totalUnits = 3 * Radio.allyCounts[0] +  Radio.allyCounts[1]+ Radio.allyCounts[2]+ Radio.allyCounts[3]+ 2* Radio.allyCounts[4]+ Radio.allyCounts[5];
        if ((float)rc.getRoundNum() / GameConstants.GAME_DEFAULT_ROUNDS > 1f - 0.005f * totalUnits) {
            rc.donate(((int)rc.getTeamBullets()) / 10 * 10);
            fireAllowed = false;
        }
    }
}
