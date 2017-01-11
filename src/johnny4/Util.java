package johnny4;

import battlecode.common.*;

public class Util {

    static RobotController rc;

    /**
     * Returns a random Direction
     *
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float) Math.random() * 2 * (float) Math.PI);
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
                if (rc.canMove(dir.rotateLeftDegrees(degreeOffset * currentCheck))) {
                    rc.move(dir.rotateLeftDegrees(degreeOffset * currentCheck));
                    return true;
                }
                // Try the offset on the right side
                if (rc.canMove(dir.rotateRightDegrees(degreeOffset * currentCheck))) {
                    rc.move(dir.rotateRightDegrees(degreeOffset * currentCheck));
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

    static private float[] px = new float[15];
    static private float[] py = new float[15];
    static private float[] pr = new float[15];
    static private boolean[] pg = new boolean[15];
    static boolean checkLineOfFire(MapLocation start, MapLocation target, TreeInfo[] trees, RobotInfo robots[], float shooterRadius){
        float cx = 0.5f * (start.x + target.x);
        float cy = 0.5f * (start.y + target.y);//first 2 variables have fast access
        double rs = Math.sqrt((cx - target.x) * (cx - target.x) + (cy - target.y) * (cy - target.y));
        float x,y,r;
        int cnt = 0;
        Team enemy = rc.getTeam().opponent();
        for (TreeInfo t : trees){
            if (cnt >= px.length) break;
            x = t.location.x;
            y = t.location.y;
            r = t.radius;
            if (Math.sqrt((x-cx)*(x-cx) + (y-cy)*(y-cy)) < rs + r){
                px[cnt] = x;
                py[cnt] = y;
                pr[cnt] = r;
                pg[cnt++] = false;
            }
        }
        for (RobotInfo rb : robots){
            if (cnt >= px.length) break;
            x = rb.location.x;
            y = rb.location.y;
            r = rb.type.bodyRadius;
            if (Math.sqrt((x-cx)*(x-cx) + (y-cy)*(y-cy)) < rs + r){
                px[cnt] = x;
                py[cnt] = y;
                pr[cnt] = r;
                pg[cnt++] = rb.getTeam() == enemy;
            }
        }
        if (cnt >= px.length){
            System.out.println("Exhausted array length");
        }

        double toTrgX = target.x - start.x;
        double toTrgY = target.y - start.y;
        double mag = Math.sqrt(toTrgX*toTrgX + toTrgY*toTrgY);
        toTrgX /= mag;
        toTrgY /= mag;
        double dx,dy,distParallel, distPerpendicular, perpx, perpy;
        for (int i = 0; i < cnt; i++){
            dx = px[i] - start.x;
            dy = py[i] - start.y;
            distParallel = toTrgX * dx + toTrgY * dy;
            perpx = dx - distParallel * toTrgX;
            perpy = dy - distParallel * toTrgY;
            distPerpendicular = (perpx * perpx + perpy * perpy);
            if (distParallel > shooterRadius + GameConstants.BULLET_SPAWN_OFFSET && distPerpendicular < pr[i] * pr[i]){
                return pg[i];
            }
        }
        System.out.println("No collision found");
        return true;
    }

    static private BulletInfo getMostDangerousBullet(MapLocation myLocation, BulletInfo[] bullets){
        BulletInfo closest = null;
        for (BulletInfo bi : bullets) {
            if (willCollideWithMe(myLocation, bi) && (closest == null || closest.location.distanceTo(myLocation) > bi.location.distanceTo(myLocation))) {
                closest = bi;
            }
        }
        return closest;
    }

    static boolean tryEvade() {
        try {
            boolean moved = false;
            int clock = Clock.getBytecodeNum();

            MapLocation myLocation = rc.getLocation();
            BulletInfo[] bullets = rc.senseNearbyBullets();
            BulletInfo closest = getMostDangerousBullet(myLocation, bullets);
            if (closest != null) {
                Direction dir = closest.dir;
                boolean leftSafe = getMostDangerousBullet(myLocation.add(dir.rotateLeftDegrees(90), rc.getType().strideRadius), bullets) == null;
                boolean rightSafe = leftSafe ? true : (getMostDangerousBullet(myLocation.add(dir.rotateRightDegrees(90), rc.getType().strideRadius), bullets) == null);
                if (rc.canMove(dir.rotateLeftDegrees(90)) && (leftSafe || !rightSafe)) {
                    rc.move(dir.rotateLeftDegrees(90));
                    moved = true;
                } else if (rc.canMove(dir.rotateRightDegrees(90))) {
                    rc.move(dir.rotateRightDegrees(90));
                    moved = true;
                }
            }
            clock = Clock.getBytecodeNum() - clock;
            if (clock > 1000) {
                System.out.println("Evade took " + clock);
            }
            if (moved){
                System.out.println("Evaded bullet");
            }
            return moved;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
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
}
