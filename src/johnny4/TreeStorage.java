package johnny4;

import battlecode.common.*;

public class TreeStorage {

    static RobotController rc;
    static MapLocation[] knownTrees = new MapLocation[50];
    static float[] treeHealth = new float[knownTrees.length];
    static boolean[] _updated = new boolean[knownTrees.length];
    static int[] lastUpdate = new int[knownTrees.length];
    static int lastWater;

    public TreeStorage(RobotController rc) {
        this.rc = rc;
        for (int i = 0; i < treeHealth.length; i++) {
            treeHealth[i] = -1f;
        }
    }

    public void addTree(TreeInfo tree) {
        float maxDist = -1f;
        MapLocation myLocation = rc.getLocation();
        float dist;
        int best = -1;
        for (int i = 0; i < knownTrees.length; i++) {
            if (treeHealth[i] < 0) {
                treeHealth[i] = tree.health;
                knownTrees[i] = tree.location;
                lastUpdate[i] = rc.getRoundNum();
                _updated[i] = true;
                System.out.println("Added tree at " + knownTrees[i] + " index " + i + " with health " + treeHealth[i]);
                return;
            }
            dist = knownTrees[i].distanceTo(myLocation);
            if (dist > maxDist) {
                best = i;
                maxDist = dist;
            }
        }
        if (best >= tree.location.distanceTo(myLocation)) {
            System.out.println("Overwriting tree at " + knownTrees[best]);
            treeHealth[best] = tree.health;
            knownTrees[best] = tree.location;
            lastUpdate[best] = rc.getRoundNum();
            _updated[best] = true;
        } else {
            System.out.println("Couldn't add tree due to full storage");
        }
    }

    int storedTrees = 0;

    public void updateTrees(TreeInfo trees[]) {

        int time = Clock.getBytecodeNum();
        int time1 = Clock.getBytecodeNum();
        int frame = rc.getRoundNum();
        for (int i = 0; i < knownTrees.length; i++) {
            _updated[i] = false;
            treeHealth[i] -= GameConstants.BULLET_TREE_DECAY_RATE * (frame - lastUpdate[i]);
            lastUpdate[i] = frame;
            //System.out.println(i + ": " + treeHealth[i] + " at " + knownTrees[i]);
        }

        int time2 = Clock.getBytecodeNum();
        boolean unknownTree;
        for (TreeInfo t : trees) {
            if (t.getTeam().equals(rc.getTeam()) && t.maxHealth == GameConstants.BULLET_TREE_MAX_HEALTH) {
                unknownTree = true;
                //for (int i = 0; i < knownTrees.length; i++) {
                    //if (treeHealth[i] > 0 && t.location.equals(knownTrees[i])) {
                        //System.out.println("Updated tree at " + knownTrees[i] + " " + treeHealth[i] + " -> " + t.health);
                        knownTrees[t.getID() % knownTrees.length] = t.location;
                        treeHealth[t.getID() % knownTrees.length] = t.health;
                        _updated[t.getID() % knownTrees.length] = true;
                        unknownTree = false;
                    //}
                //}
                //if (unknownTree) addTree(t);
            }
        }

        storedTrees = 0;
        int time3 = Clock.getBytecodeNum();
        for (int i = 0; i < knownTrees.length; i++) {
            if (treeHealth[i] > 0) {
                if (!_updated[i] && rc.canSenseLocation(knownTrees[i])) {
                    treeHealth[i] = -1;
                } else {
                    storedTrees++;
                }
            }
        }
        int time4 = Clock.getBytecodeNum();
        time = Clock.getBytecodeNum() - time;
        if (time > 1000) {
            System.out.println("Updating trees took " + time);
            System.out.println(time1 + " " + time2 + " " + time3 + " " + time4);
        }
    }

    public void tryWater() throws GameActionException {

        if (rc.getRoundNum() == lastWater) return;
        for (int i = 0; i < knownTrees.length; i++) {
            if (treeHealth[i] > 0 && rc.canWater(knownTrees[i]) && treeHealth[i] < GameConstants.BULLET_TREE_MAX_HEALTH - GameConstants.WATER_HEALTH_REGEN_RATE) {
                rc.water(knownTrees[i]);
                treeHealth[i] = Math.min(GameConstants.BULLET_TREE_MAX_HEALTH, treeHealth[i] + GameConstants.WATER_HEALTH_REGEN_RATE);
                System.out.println("Watered tree at " + knownTrees[i] + " back to " + treeHealth[i] + "/" + GameConstants.BULLET_TREE_MAX_HEALTH + " hp.");
                lastWater = rc.getRoundNum();
                return;
            }
        }
    }

    int toWater = -1; //remember last result, so it doesnt change


    static final RobotInfo[] gardeners = new RobotInfo[20];

    public MapLocation waterTree(RobotInfo[] nearbyRobots) throws GameActionException {

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
                    System.out.println("Watered tree at " + knownTrees[i] + " back to " + treeHealth[i] + "/" + GameConstants.BULLET_TREE_MAX_HEALTH + " hp.");
                }
                dist = knownTrees[i].distanceTo(myLocation) * (-1 / treeHealth[i] / treeHealth[i] + 1);
                for (int g = 0; g < gcnt; g++) {
                    if (gardeners[g].location.distanceTo(knownTrees[i]) + 1 < dist) {
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
            System.out.println("Getting nearest waterable location took " + time);
        }
        toWater = best;
        if (best >= 0) {
            return knownTrees[best];
        } else {
            System.out.println("Couldn't find tree to water");
            return null;
        }

    }
}
