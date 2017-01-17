package johnny4;

import battlecode.common.*;

public class BuildPlanner {

    static RobotController rc;
    static RobotInfo[] nearbyRobots;
    static TreeInfo[] nearbyTrees;
    static final int MAX_TREES_PER_GARDENER = 3;

    static float money = 0;
    static int nearbyProtectors = 0;
    static int nearbyGardeners = 0;
    static int nearbyBulletTrees = 0;
    static int nearbyEnemies = 0;
    static RobotInfo nextEnemy = null;

    static int ownScouts;
    static int ownLumberjacks;
    static int ownSoldiers;
    static int ownGardeners;
    static int enemyScouts;
    static int enemyLumberjacks;
    static int enemySoldiers;
    static int enemyGardeners;
    static int frame;
    static int totalEnemies;
    static boolean alarm;
    static float myTrees;
    static float closestDanger;
    static int graceRounds;
    static float startArchonDist;
    static int lastScoutRound = 0;

    public static void init() {
        frame = rc.getRoundNum();
        if (frame > 10) {
            graceRounds = 0;
            return;
        }

        MapLocation myLocation = rc.getLocation();
        startArchonDist = 1e10f;
        float curDist;
        for (MapLocation archonPos: Map.enemyArchonPos) {
            curDist = archonPos.distanceTo(myLocation);
            if (curDist < startArchonDist) {
                startArchonDist = curDist;
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
        nearbyProtectors = nearbyGardeners = nearbyBulletTrees = 0;
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
                    case SOLDIER:
                        nearbyProtectors++;
                        break;
                    case GARDENER:
                        nearbyGardeners++;
                        break;
                }
                continue;
            }
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
        Team myTeam = rc.getTeam();
        for (TreeInfo t : nearbyTrees) {
            if (t.getTeam().equals(myTeam)) {
                nearbyBulletTrees++;
            }
        }
        alarm = Radio.getAlarm();
        int ownCounts[] = Radio.countAllies();
        ownScouts = ownCounts[Radio.typeToInt(RobotType.SCOUT)];
        ownLumberjacks = ownCounts[Radio.typeToInt(RobotType.LUMBERJACK)];
        ownSoldiers = ownCounts[Radio.typeToInt(RobotType.SOLDIER)];
        ownGardeners = ownCounts[Radio.typeToInt(RobotType.GARDENER)];
        int enemyCounts[] = Radio.countEnemies();
        totalEnemies = enemyCounts[0] + enemyCounts[1] + enemyCounts[2] + enemyCounts[3] + enemyCounts[4] + enemyCounts[5];
        enemyScouts = enemyCounts[Radio.typeToInt(RobotType.SCOUT)];
        enemyLumberjacks = enemyCounts[Radio.typeToInt(RobotType.LUMBERJACK)];
        enemySoldiers = enemyCounts[Radio.typeToInt(RobotType.SOLDIER)];
        enemyGardeners = enemyCounts[Radio.typeToInt(RobotType.GARDENER)];
        myTrees = TreeStorage.ownTrees + TreeStorage.otherTrees * (nearbyGardeners == 0 ? 1 : 0.5f );
    }

    public static boolean buildTree() throws GameActionException {
        if (myTrees >= MAX_TREES_PER_GARDENER || money < GameConstants.BULLET_TREE_COST) {
            return false;
        }
        if (graceRounds > 40) {
            return true;
        }

        return ((frame - lastScoutRound) > 100) || (nearbyProtectors > nearbyEnemies);
    }


    public static boolean hireGardener() throws GameActionException {
        money = rc.getTeamBullets();
        boolean haveMoney = money > ((nextEnemy == null) ? RobotType.GARDENER.bulletCost : RobotType.GARDENER.bulletCost + RobotType.SOLDIER.bulletCost);
        if (!haveMoney) {
            return false;
        }

        if (Util.DEBUG) System.out.println("numGardeners: " + ownGardeners);
        if (Util.DEBUG) System.out.println("nearby Gardeners: " + nearbyGardeners);
        if (Util.DEBUG) System.out.println("nearby Trees: " + nearbyBulletTrees);
        if (ownGardeners == 0 || nearbyBulletTrees > 1 && nearbyGardeners == 0) {
            return true;
        }

        if (frame % ((Radio.countAllies(RobotType.ARCHON) - 1) * Math.log(nearbyGardeners * 2 + 1) * 5 + 1) > 0) {
            if (Util.DEBUG) System.out.println("Waiting for other Archon, Chance: " + (Math.log(Archon.gardenersSpawned * 2 + 1) * 10 + 1));
            return false;
        }

        int activeGardeners = Radio.countActiveGardeners();
        if (Util.DEBUG) System.out.println("activeGardeners: " + activeGardeners);
        if (activeGardeners == 0 && ownSoldiers + ownLumberjacks >= ownGardeners) {
            return true;
        }

        boolean rich = money > 120;
        if (Util.DEBUG) System.out.println("rich: " + rich);
        if (rich && ownGardeners <= (ownSoldiers + ownLumberjacks) / 2 + rc.getRoundNum() / 150) {
            return true;
        }

        return false;
    }


    public static RobotType getUnitToBuild() throws GameActionException {

        boolean canSoldier = money > RobotType.SOLDIER.bulletCost;
        boolean canLumberjack = money > RobotType.LUMBERJACK.bulletCost;
        boolean canScout = money > RobotType.SCOUT.bulletCost;
        boolean rich = money > 160 && rc.getRoundNum() > 80;
        boolean enemyWasScouted = totalEnemies > 2;
        boolean allOrNothing = frame < 200 && startArchonDist < 40;

        //if (nearbyProtectors > 4) return null; //dont overcrowd

        if (Util.DEBUG) System.out.println("own scouts: " + ownScouts);
        if (Util.DEBUG) System.out.println("own soldiers: " + ownSoldiers);
        if (Util.DEBUG) System.out.println("own gardeners: " + ownGardeners);
        if (Util.DEBUG) System.out.println("own lumberjacks: " + ownLumberjacks);
        if (Util.DEBUG) System.out.println("enemy scouts: " + enemyScouts);
        if (Util.DEBUG) System.out.println("enemy soldiers: " + enemySoldiers);
        if (Util.DEBUG) System.out.println("enemy gardeners: " + enemyGardeners);
        if (Util.DEBUG) System.out.println("enemy lumberjacks: " + enemyLumberjacks);
        if (Util.DEBUG) System.out.println("nearby trees: " + nearbyTrees.length);


        boolean needSoldiers = (ownSoldiers * 1.5f + ownLumberjacks < (enemySoldiers + 0.5 * enemyLumberjacks)) || rich;
        boolean noScouts = ownScouts == 0;

        boolean needLumberJacks = (Radio.countTreeCutRequests() > 0 && ownLumberjacks == 0) && (ownLumberjacks < (ownSoldiers / 3)) || (ownLumberjacks < Math.min(Radio.countTreeCutRequests(), ownSoldiers + 1 + (rich ? 10 : 0)));
        boolean needScouts = (ownScouts < 2 + ((ownLumberjacks + ownSoldiers) / 2)) || ownScouts < enemyScouts;

        if (noScouts && canScout) {
            return RobotType.SCOUT;
        }

        if (allOrNothing) {
            if (ownSoldiers < 2) {
                return RobotType.SOLDIER;
            }
            if (ownLumberjacks == 0) {
                return RobotType.LUMBERJACK;
            }
        }


        if (allOrNothing || (nearbyProtectors < 1) && (frame - lastScoutRound) < 100) { //in danger
            if (needLumberJacks && canLumberjack) {
                return RobotType.LUMBERJACK;
            } else if (needSoldiers && canSoldier) {
                return RobotType.SOLDIER;
            } else if (nextEnemy != null && nextEnemy.type == RobotType.SCOUT && canLumberjack) {
                return (nearbyTrees.length < 7) ? RobotType.LUMBERJACK : RobotType.SCOUT;
            } else if (canSoldier) {
                return RobotType.SOLDIER;
            }
        }

        if (alarm && !rich && nextEnemy == null) return null;


        if (enemyWasScouted && needScouts && canScout) {
            return RobotType.SCOUT;
        }

        if (enemyWasScouted && needSoldiers && canSoldier) {
            return RobotType.SOLDIER;
        }
        if (needLumberJacks && canLumberjack) {
            return RobotType.LUMBERJACK;
        }


        return null;
    }
}
