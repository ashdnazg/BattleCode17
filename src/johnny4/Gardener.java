package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Gardener {

    final int ATTACK_COOLDOWN_TIME = 10; //frames until resuming normal work

    RobotController rc;
    Radio radio;

    Map map;
    Direction[] buildDirs;
    Team myTeam;
    Team enemyTeam;
    float health;
    int roundsSinceAttack;
    BulletInfo bullets[];
    MapLocation escapeLocation;
    final float MIN_CONSTRUCTION_MONEY;
    MapLocation lastRandomLocation;
    RobotInfo nearestProtector;
    int lastProtectorContact = -1000;

    public Gardener(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.buildDirs = new Direction[12];
        float angle = (float) Math.PI / 6.0f;
        for (int i = 0; i < 12; i++) {
            this.buildDirs[i] = new Direction(i * (float) Math.PI / 6.0f);
        }
        this.myTeam = rc.getTeam();
        this.enemyTeam = rc.getTeam().opponent();
        this.health = rc.getHealth();
        this.roundsSinceAttack = 999999;
        this.MIN_CONSTRUCTION_MONEY = Math.min(GameConstants.BULLET_TREE_COST, RobotType.SOLDIER.bulletCost);
        lastRandomLocation = rc.getLocation();

        BuildPlanner.rc = rc;
        if (Util.DEBUG) System.out.println("Finished Constructing gardener: " + Clock.getBytecodeNum());
        BuildPlanner.init();
        mustFindSpaceSince = rc.getRoundNum();
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

    public boolean tryBuild(RobotType robotType) throws GameActionException {
        if (robotType == null) return false;
        for (int i = 0; i < buildDirs.length; i++) {
            if (rc.canBuildRobot(robotType, buildDirs[i])) {
                rc.buildRobot(robotType, buildDirs[i]);
                Radio.reportBuild(robotType);
                return true;
            }
        }
        if (DEBUG) System.out.println("Failed to build " + robotType);
        return false;
    }

    int mustFindSpaceSince;

    protected void tick() {
        try {
            preTick();
            //Sensing
            roundsSinceAttack++;
            int frame = rc.getRoundNum();
            bullets = rc.senseNearbyBullets();
            RobotInfo[] nearbyRobots = map.sense();
            TreeInfo[] trees = senseClosestTrees();
            MapLocation myLocation = rc.getLocation();
            float money = rc.getTeamBullets();

            // Trees
            if (money > MIN_CONSTRUCTION_MONEY && BuildPlanner.buildTree()) {
                //request to cut annoying trees
                for (TreeInfo t : trees) {
                    if (t.team == myTeam)
                        continue;
                }
                boolean freePos = false;
                for (int i = 0; i < buildDirs.length; i++) {
                    if (rc.canPlantTree(buildDirs[i])) {
                        if (!freePos) {
                            freePos = true;
                            continue;
                        }
                        rc.plantTree(buildDirs[i]);
                        break;
                    }
                }
            }

            TreeInfo[] tis = rc.senseNearbyTrees(3.0f);
            int lowestHealthTree = -1;
            float lowestHealth = 1e10f;
            for (TreeInfo ti: tis) {
                if (ti.team == Team.NEUTRAL && ti.ID % 12 == frame % 12) {
                    radio.requestTreeCut(ti);
                } else if (ti.team == myTeam && ti.health < lowestHealth && rc.canWater(ti.ID)) {
                    lowestHealth = ti.health;
                    lowestHealthTree = ti.ID;
                }
            }
            if (lowestHealthTree >= 0) {
                rc.water(lowestHealthTree);
            }


            // Units
            BuildPlanner.update(nearbyRobots, trees);
            if (money > RobotType.SCOUT.bulletCost) {
                RobotType toBuild = BuildPlanner.getUnitToBuild();
                System.out.println("I want to build " + toBuild);
                tryBuild(toBuild);
            }
        } catch (Exception e) {
            if (Util.DEBUG) System.out.println("Gardener Exception");
            e.printStackTrace();
        }
    }
}
