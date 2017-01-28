package johnny4_seeding;

import battlecode.common.*;
import johnny4_seeding.Util;

public class TreeStorage {

    static RobotController rc;
    static MapLocation[] knownTrees = new MapLocation[47];
    static float[] treeHealth = new float[knownTrees.length];
    static boolean[] _updated = new boolean[knownTrees.length];
    static int[] lastUpdate = new int[knownTrees.length];
    static boolean[] ownTree = new boolean[knownTrees.length];
    static int lastWater;

    static int ownTrees = 0;
    static int otherTrees = 0;
    static int storedTrees = 0;
    static int toWater = -1; //remember last result, so it doesnt change

    static RobotInfo[] gardeners = new RobotInfo[40];

    public static void plantedTree(TreeInfo t) {
        knownTrees[t.getID() % knownTrees.length] = t.location;
        treeHealth[t.getID() % knownTrees.length] = t.health;
        _updated[t.getID() % knownTrees.length] = true;
        ownTree[t.getID() % knownTrees.length] = true;
        lastUpdate[t.getID() % knownTrees.length] = rc.getRoundNum();
        if (Util.DEBUG)
            System.out.println("New tree at " + knownTrees[t.getID() % knownTrees.length] + " with " + t.health + " hp");
    }


    public static void updateTrees(TreeInfo trees[]) throws GameActionException {

        int time = Clock.getBytecodeNum();
        int time1 = Clock.getBytecodeNum();
        int frame = rc.getRoundNum();
        for (int i = 0; i < knownTrees.length; i++) {
            _updated[i] = false;
            if (Util.DEBUG) {
                if (treeHealth[i] > 0) {
                    System.out.println(knownTrees[i] + "(" + ownTree[i] + "): " + treeHealth[i] + " -> " + (treeHealth[i] - GameConstants.BULLET_TREE_DECAY_RATE * (frame - lastUpdate[i])));
                }
            }
            treeHealth[i] -= GameConstants.BULLET_TREE_DECAY_RATE * (frame - lastUpdate[i]);
            lastUpdate[i] = frame;
        }

        int time2 = Clock.getBytecodeNum();
        boolean unknownTree;
        for (TreeInfo t : trees) {
            if (t.getTeam().equals(rc.getTeam()) && t.maxHealth == GameConstants.BULLET_TREE_MAX_HEALTH) {
                knownTrees[t.getID() % knownTrees.length] = t.location;
                treeHealth[t.getID() % knownTrees.length] = t.health;
                _updated[t.getID() % knownTrees.length] = true;
            }
        }

        storedTrees = 0;
        ownTrees = otherTrees = 0;
        int time3 = Clock.getBytecodeNum();
        for (int i = 0; i < knownTrees.length; i++) {
            if (treeHealth[i] > 0) {
                if (!_updated[i] && rc.canSenseLocation(knownTrees[i])) {
                    treeHealth[i] = 0;
                    ownTree[i] = false;
                    if (Util.DEBUG) rc.setIndicatorDot(knownTrees[i], 255, 0, 0);
                } else {
                    storedTrees++;
                    if (ownTree[i]) {
                        ownTrees++;
                        if (Util.DEBUG)
                            rc.setIndicatorDot(knownTrees[i], 255, 255, (int) (255 * treeHealth[i] / GameConstants.BULLET_TREE_MAX_HEALTH));
                    } else {
                        otherTrees++;
                        if (Util.DEBUG)
                            rc.setIndicatorDot(knownTrees[i], 255, 100, (int) (255 * treeHealth[i] / GameConstants.BULLET_TREE_MAX_HEALTH));
                    }
                }
            } else {
                ownTree[i] = false;
            }
        }
        int time4 = Clock.getBytecodeNum();
        time = Clock.getBytecodeNum() - time;
        if (time > 1000) {
            if (Util.DEBUG) System.out.println("Updating trees took " + time);
            if (Util.DEBUG) System.out.println(time1 + " " + time2 + " " + time3 + " " + time4);
        }
    }

    public static void tryWater() throws GameActionException {

        if (rc.getRoundNum() == lastWater) return;
        for (int i = 0; i < knownTrees.length; i++) {
            if (treeHealth[i] > 0 && rc.canWater(knownTrees[i]) && treeHealth[i] < GameConstants.BULLET_TREE_MAX_HEALTH - GameConstants.WATER_HEALTH_REGEN_RATE) {
                rc.water(knownTrees[i]);
                treeHealth[i] = Math.min(GameConstants.BULLET_TREE_MAX_HEALTH, treeHealth[i] + GameConstants.WATER_HEALTH_REGEN_RATE);
                lastUpdate[i] = rc.getRoundNum();
                if (Util.DEBUG)
                    System.out.println("Watered tree at " + knownTrees[i] + " back to " + treeHealth[i] + "/" + GameConstants.BULLET_TREE_MAX_HEALTH + " hp.");
                lastWater = rc.getRoundNum();
                return;
            }
        }
    }

    public static MapLocation waterTree(RobotInfo[] nearbyRobots) throws GameActionException {

        int time = Clock.getBytecodeNum();
        int gcnt = 0;
        Team myTeam = rc.getTeam();
        int frame = rc.getRoundNum();
        for (int i = 0; i < nearbyRobots.length; i++) {
            if (nearbyRobots[i].getTeam().equals(myTeam) && nearbyRobots[i].type == RobotType.GARDENER) {
                gardeners[gcnt++] = nearbyRobots[i];
            }
        }
        float minDist = 100000f;
        MapLocation myLocation = rc.getLocation();
        float dist;
        int best = -1;
        for (int i = 0; i < knownTrees.length; i++) {
            if (treeHealth[i] > 0 && treeHealth[i] < GameConstants.BULLET_TREE_MAX_HEALTH - GameConstants.WATER_HEALTH_REGEN_RATE) {
                if (frame > lastWater && rc.canWater(knownTrees[i])) {
                    rc.water(knownTrees[i]);
                    treeHealth[i] = Math.min(GameConstants.BULLET_TREE_MAX_HEALTH, treeHealth[i] + GameConstants.WATER_HEALTH_REGEN_RATE);
                    lastWater = frame;
                    lastUpdate[i] = rc.getRoundNum();
                    if (Util.DEBUG)
                        System.out.println("Watered tree at " + knownTrees[i] + " back to " + treeHealth[i] + "/" + GameConstants.BULLET_TREE_MAX_HEALTH + " hp.");
                }
                dist = /*knownTrees[i].distanceTo(myLocation) **/ (treeHealth[i]);
                for (int g = 0; g < gcnt; g++) {
                    if (gardeners[g].location.distanceTo(knownTrees[i]) - 1 < dist) {
                        dist = 10000;
                    }
                }
                if (dist < minDist) {
                    best = i;
                    minDist = dist;
                }
            }
        }
        if (toWater > 0 && treeHealth[toWater] > 0 && treeHealth[toWater] < GameConstants.BULLET_TREE_MAX_HEALTH - GameConstants.WATER_HEALTH_REGEN_RATE) {
            //best = toWater;
        }
        time = Clock.getBytecodeNum() - time;
        if (time > 1000) {
            if (Util.DEBUG) System.out.println("Getting nearest waterable location took " + time);
        }
        toWater = best;
        if (best >= 0) {
            return knownTrees[best];
        } else {
            if (Util.DEBUG) System.out.println("Couldn't find tree to water");
            return null;
        }

    }
}
