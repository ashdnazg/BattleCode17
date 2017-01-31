package johnny4;

import battlecode.common.*;

public class BuildPlanner {

    static RobotController rc;
    static RobotInfo[] nearbyRobots;
    static TreeInfo[] nearbyTrees;
    static final int MAX_TREES_PER_GARDENER = 3;

    static float money = 0;
    static int nearbyProtectors = 0;
    static int nearbyLumberjacks = 0;
    static int nearbySoldiers = 0;
    static int nearbyGardeners = 0;
    static int nearbyBulletTrees = 0;
    static int nearbyNonBulletTrees = 0;
    static int nearbyEnemies = 0;
    static RobotInfo nextEnemy = null;

    static int ownScouts;
    static int ownLumberjacks;
    static int ownSoldiers;
    static int lastOwnGardeners;
    static int ownGardeners;
    static int enemyScouts;
    static int enemyLumberjacks;
    static int enemySoldiers;
    static int enemyGardeners;
    static int nearbyArchons;
    static int frame;
    static int totalEnemies;
    static boolean alarm;
    static float myTrees;
    static float closestDanger;
    static int graceRounds;
    static float startArchonDist;
    static int lastScoutRound = 0;
    static boolean allOrNothing;
    static boolean closestArchon;
    static MapLocation nextEnemyFar = null;
    static boolean gardenerStuckified = false;
    static boolean gardenerCantBuild = false;

    public static void init() {
        frame = rc.getRoundNum();
        MapLocation myLocation = rc.getLocation();
        startArchonDist = 1e10f;

        if (frame > 10) {
            graceRounds = 0;
            return;
        }
        float curDist;
        for (MapLocation myArch : rc.getInitialArchonLocations(rc.getTeam())) {
            for (MapLocation archonPos : Map.enemyArchonPos) {
                curDist = archonPos.distanceTo(myArch);
                if (curDist < startArchonDist) {
                    closestArchon = myArch.distanceTo(myLocation) < 2;
                    startArchonDist = curDist;
                }
            }
        }
        graceRounds = (int) (startArchonDist / RobotType.SCOUT.strideRadius) + 11;
        System.out.println("grace rounds:" + graceRounds);
    }

    public static void update(RobotInfo[] nearbyRobots, TreeInfo[] nearbyTrees) throws GameActionException {
        frame = rc.getRoundNum();
        --graceRounds;

        BuildPlanner.nearbyRobots = nearbyRobots;
        BuildPlanner.nearbyTrees = nearbyTrees;
        nearbyProtectors = nearbyGardeners = nearbyBulletTrees = nearbyLumberjacks = nearbyArchons = nearbyEnemies = nearbyNonBulletTrees = nearbySoldiers = 0;
        money = rc.getTeamBullets();
        nextEnemy = null;
        MapLocation myLocation = rc.getLocation();
        boolean friendly;
        closestDanger = 1e10f;
        float curDist;
        for (RobotInfo r : nearbyRobots) {
            friendly = r.team == rc.getTeam();
            if (friendly) {
                switch (r.type) {
                    case LUMBERJACK:
                        nearbyLumberjacks++;
                        nearbyProtectors++;
                        break;
                    case SOLDIER:
                        nearbySoldiers++;
                        nearbyProtectors++;
                        break;
                    case GARDENER:
                        nearbyGardeners++;
                        break;
                    case ARCHON:
                        nearbyArchons++;
                        break;
                }
                continue;
            } else {
                nearbyEnemies++;
                curDist = r.location.distanceTo(myLocation);
                if (nextEnemy == null || closestDanger > curDist) {
                    switch (r.type) {
                        case SCOUT:
                            lastScoutRound = frame;
                        case LUMBERJACK:
                        case SOLDIER:
                        case TANK:
                            nextEnemy = r;
                            closestDanger = curDist;
                            break;
                    }

                }
            }
        }
        myTrees = 0;
        Team myTeam = rc.getTeam();
        int stuckingTrees = 0;
        for (TreeInfo t : nearbyTrees) {
            if (t.team.equals(myTeam) && myLocation.distanceTo(t.location) < 2.0f) {
                myTrees++;
            }
            if (t.team.equals(myTeam)) {
                nearbyBulletTrees++;
            } else {
                nearbyNonBulletTrees++;
            }
            if (!t.team.equals(myTeam) && myLocation.distanceTo(t.location) < 5f + t.radius && rc.getType() == RobotType.GARDENER) {
                stuckingTrees++;
                Radio.requestTreeCut(t);
            }
        }
        gardenerStuckified = !Gardener.active && stuckingTrees >= 3;
        nextEnemyFar = nextEnemy != null ? nextEnemy.location : Map.getTarget(Map.ARCHON, Map.GARDENER, Map.LUMBERJACK, Map.SCOUT, Map.SOLDIER, Map.TANK, 4, myLocation);
        if (nextEnemyFar != null) closestDanger = Math.min(closestDanger, nextEnemyFar.distanceTo(myLocation));
        if (frame % 5 == 0) {
            Map.generateFarTargets(rc, myLocation, 50, 0);
        }
        alarm = Radio.getAlarm();
        int ownCounts[] = Radio.countAllies();
        ownScouts = ownCounts[Radio.typeToInt(RobotType.SCOUT)];
        ownLumberjacks = ownCounts[Radio.typeToInt(RobotType.LUMBERJACK)];
        ownSoldiers = ownCounts[Radio.typeToInt(RobotType.SOLDIER)] + ownCounts[Radio.typeToInt(RobotType.TANK)] * 2;
        ownGardeners = Math.max(ownCounts[Radio.typeToInt(RobotType.GARDENER)], lastOwnGardeners);
        lastOwnGardeners = ownCounts[Radio.typeToInt(RobotType.GARDENER)];
        int enemyCounts[] = Radio.countEnemies();
        totalEnemies = enemyCounts[0] + enemyCounts[1] + enemyCounts[2] + enemyCounts[3] + enemyCounts[4] + enemyCounts[5];
        enemyScouts = enemyCounts[Radio.typeToInt(RobotType.SCOUT)];
        enemyLumberjacks = enemyCounts[Radio.typeToInt(RobotType.LUMBERJACK)];
        enemySoldiers = enemyCounts[Radio.typeToInt(RobotType.SOLDIER)];
        enemyGardeners = enemyCounts[Radio.typeToInt(RobotType.GARDENER)];
        allOrNothing = frame < 180 && startArchonDist < 33 || frame < 200 - ownSoldiers * 50 && Radio.getLandContact();
        if (Util.DEBUG) System.out.println("own scouts: " + ownScouts);
        if (Util.DEBUG) System.out.println("own soldiers: " + ownSoldiers);
        if (Util.DEBUG) System.out.println("own gardeners: " + ownGardeners);
        if (Util.DEBUG) System.out.println("own lumberjacks: " + ownLumberjacks);
        if (Util.DEBUG) System.out.println("enemy scouts: " + enemyScouts);
        if (Util.DEBUG) System.out.println("enemy soldiers: " + enemySoldiers);
        if (Util.DEBUG) System.out.println("enemy gardeners: " + enemyGardeners);
        if (Util.DEBUG) System.out.println("enemy lumberjacks: " + enemyLumberjacks);
        if (Util.DEBUG) System.out.println("nearby trees: " + nearbyTrees.length);
        if (Util.DEBUG) System.out.println("nearby bullettrees: " + nearbyBulletTrees);
        if (Util.DEBUG) System.out.println("nearby lumberjacks: " + nearbyLumberjacks);
        if (Util.DEBUG) System.out.println("nearby non bullettrees: " + nearbyNonBulletTrees);
        if (Util.DEBUG) System.out.println("nearby gardeners: " + nearbyGardeners);
        if (Util.DEBUG) System.out.println("tree cutting requests: " + Radio.countTreeCutRequests());
        if (Util.DEBUG) System.out.println("grace rounds: " + graceRounds);
        if (Util.DEBUG) System.out.println("gardener stuck: " + gardenerStuckified);
        if (Util.DEBUG) System.out.println("all or nothing: " + allOrNothing);
        if (Util.DEBUG) System.out.println("land contact: " + Radio.getLandContact());
    }

    public static boolean buildTree() throws GameActionException {
        if (money < GameConstants.BULLET_TREE_COST || ownSoldiers < 2 && money < GameConstants.BULLET_TREE_COST + 20 * (Map.enemyArchonPos.length - 1) || nextEnemy != null && nearbyProtectors == 0) {
            return false;
        }

        if (!Util.fireAllowed){
            return true;
        }

        if (graceRounds > 30) {
            return true;
        }

        if (ownSoldiers + ownLumberjacks < 2 && closestDanger < 30  || ownSoldiers < 1 || allOrNothing && (ownSoldiers < enemySoldiers || frame < 27)) {
            return false;
        }

        /*if (ownScouts == 0 && !Radio.getLandContact() && totalEnemies == 0) {
            return false;
        }*/

        if (rc.getTreeCount() > 2 * ownSoldiers + frame / 300 && money < 120 && ownSoldiers * 1.2f + ownLumberjacks * 0.2 < (enemySoldiers + 0.3 * enemyLumberjacks)) {
            return false;
        }

        return true;
    }


    public static boolean hireGardener() throws GameActionException {
        boolean haveMoney = money > ((nextEnemy == null || nearbyProtectors > 0) ? RobotType.GARDENER.bulletCost : RobotType.GARDENER.bulletCost + RobotType.SOLDIER.bulletCost);
        if (!haveMoney || nearbyGardeners + nearbyProtectors / 2 > 4 || !Util.fireAllowed) {
            return false;
        }
        if (Radio.countActiveGardeners() > 0) {
            if (Util.DEBUG) System.out.println("There are still " + Radio.countActiveGardeners() + " active gardeners");
            return false;
        }

        if (ownGardeners == 0 && ((money > 110 && frame > 4) || closestArchon)) {
            return true;
        }

        if (!allOrNothing && (!Radio.getLandContact() || frame > 300) && (money > 140 || ownSoldiers > rc.getTreeCount() || true) && frame > 4 + Math.min(Archon.gardenersHired, ownGardeners) * 120 && (nearbyGardeners - 1) * 2.5 <= nearbyBulletTrees) {
            return true;
        }

        if (ownGardeners > 0 && ((rc.getTreeCount()+ 3) / ownGardeners > 3) && (!Radio.getAlarm() || money > 101 + rc.getTreeCount())) {
            return true;
        }

        return false;
    }


    public static RobotType getUnitToBuild() throws GameActionException {

        boolean canSoldier = money > RobotType.SOLDIER.bulletCost;
        boolean canLumberjack = money > RobotType.LUMBERJACK.bulletCost;
        boolean canScout = money > RobotType.SCOUT.bulletCost;
        boolean rich = money > 160 && frame > 80;
        boolean enemyWasScouted = totalEnemies > 2;

        if (nearbyProtectors + nearbyArchons >= 6 || !Util.fireAllowed) return null; //dont overcrowd


        boolean needSoldiers = ownSoldiers < 1 + (0+ownGardeners) / 2 || (ownSoldiers * 1.2f + ownLumberjacks * 0.2 < (enemySoldiers + 1 + 0.3 * enemyLumberjacks)) || (Radio.getLandContact() || nextEnemyFar != null && nextEnemyFar.distanceTo(rc.getLocation()) < 30) && ownSoldiers < 2 + Math.min(2, frame / 150) + ownGardeners / 2;
        if (!Radio.getLandContact() && ownSoldiers >= 1) {
            //needSoldiers = false;
        }
        boolean noScouts = ownScouts == 0 && frame > 110;
        boolean needLumberJacks = (((Radio.countTreeCutRequests() > 0 && ownLumberjacks == 0) && (ownLumberjacks < ((ownSoldiers + 1) / 3))) || (!needSoldiers && ownLumberjacks < Radio.countTreeCutRequests() && (ownLumberjacks < 1 && ownSoldiers > 1 || ownLumberjacks < 2 && frame > 180))) && (!Radio.getLandContact() || frame > 200) ||
                gardenerStuckified && (nearbyLumberjacks == 0 || money > 140 && !needSoldiers && nearbyLumberjacks < 3) && nearbyNonBulletTrees > 0;
        boolean needScouts = ownScouts < (ownSoldiers * 2 + ownLumberjacks + 1) / 4 && ownScouts < 3;
        if (!Radio.getLandContact() && ownLumberjacks >= 1 && nextEnemyFar != null && nextEnemyFar.distanceTo(rc.getLocation()) < 30) {
            needLumberJacks = false;
            if (Util.DEBUG) System.out.println("Too dangerous for lumberjacks");
        }

        if (Util.DEBUG) System.out.println("needSoldiers: " + needSoldiers);
        if (Util.DEBUG) System.out.println("noScouts: " + noScouts);
        if (Util.DEBUG) System.out.println("needLumberJacks: " + needLumberJacks);
        if (Util.DEBUG) System.out.println("needScouts: " + needScouts);

        if (noScouts && canScout && !Radio.getLandContact() && totalEnemies == 0 && startArchonDist > 17 && ownSoldiers + ownLumberjacks > 0) {
            return RobotType.SCOUT;
        }

        if (allOrNothing) {
            if (Util.DEBUG) System.out.println("all or nothing");
            if ((ownSoldiers < 2) && (ownLumberjacks > 0 || !gardenerStuckified) || needSoldiers && (nearbySoldiers < 2 || nextEnemy != null || nearbyLumberjacks > 0)) {
                return RobotType.SOLDIER;
            }
            if (ownLumberjacks == 0 && gardenerStuckified || (needLumberJacks && (frame > 160 || nearbySoldiers >= 2 && nextEnemy == null && frame > 80))) {
                return RobotType.LUMBERJACK;
            }
            return null;
        }


        if (allOrNothing || nearbyEnemies > 0 && nearbyProtectors < 2) {
            if (Util.DEBUG) System.out.println("Responding to threat");
            if (needSoldiers && canSoldier) {
                return RobotType.SOLDIER;
            } else if (canSoldier) {
                return (!gardenerStuckified || nearbyLumberjacks > 0 || !needLumberJacks) ? RobotType.SOLDIER : RobotType.LUMBERJACK;
            }
        }

        //if (alarm && !rich && nextEnemy == null) return null;


        if (needSoldiers && canSoldier && (nearbyLumberjacks > 0 || !gardenerStuckified) && (nearbyProtectors + nearbyArchons < 3 || nearbySoldiers == 0)) {
            return RobotType.SOLDIER;
        }

        if (needLumberJacks && canLumberjack) {
            return RobotType.LUMBERJACK;
        }

        if (needScouts && canScout) {
            return RobotType.SCOUT;
        }
        if (Util.DEBUG) System.out.println("nothign to build");

        return null;
    }
}
