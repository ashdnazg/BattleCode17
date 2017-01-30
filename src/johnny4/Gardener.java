package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Gardener {

    final int ATTACK_COOLDOWN_TIME = 10; //frames until resuming normal work

    RobotController rc;
    Radio radio;
    Movement movement;

    int treesPlanted = 0;
    Map map;
    Direction[] buildDirs;
    boolean[] buildDirValid;
    Team myTeam;
    Team enemyTeam;
    float health;
    int roundsSinceAttack;
    BulletInfo bullets[];
    int lastTreePlant = -100;
    final float MIN_CONSTRUCTION_MONEY;
    MapLocation lastRandomLocation;
    final int spawnFrame;
    MapLocation walkingTarget;
    static boolean active = true;
    boolean _active = true;
    int disabledSince = 10000;

    public Gardener(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.movement = new Movement(rc);
        this.buildDirs = new Direction[12];
        this.buildDirValid = new boolean[buildDirs.length];
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
        spawnFrame = rc.getRoundNum();
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
        if (robotType == RobotType.SOLDIER && rc.getTeamBullets() > RobotType.TANK.bulletCost && rc.getRoundNum() > 250 && tryBuild(RobotType.TANK))
            return true;
        BuildPlanner.gardenerCantBuild = false;
        if (robotType == null) return false;
        for (int i = 0; i < buildDirs.length; i++) {
            if ((buildDirValid[i] || robotType == RobotType.LUMBERJACK || robotType == RobotType.SCOUT) && rc.canBuildRobot(robotType, buildDirs[i])) {
                rc.buildRobot(robotType, buildDirs[i]);
                Radio.reportBuild(robotType);
                lastBuild = rc.getRoundNum();
                return true;
            }
        }
        if (DEBUG) System.out.println("Failed to build " + robotType);
        if (!(robotType == RobotType.LUMBERJACK || robotType == RobotType.SCOUT)) {
            BuildPlanner.gardenerCantBuild = true;
        }
        return false;
    }

    boolean inPosition = false;
    int noBuildPosSince = 10000;
    int lastBuild = -1000;
    int freeDir = -1;
    int directionModulus = 0;

    protected void tick() {
        try {
            preTick();
            if (active) Radio.reportActiveGardener();
            if (DEBUG) System.out.println("Active gardener: " + active);
            //Sensing
            roundsSinceAttack++;
            int frame = rc.getRoundNum();
            bullets = rc.senseNearbyBullets();
            RobotInfo[] nearbyRobots = map.sense();
            TreeInfo[] trees = senseClosestTrees();
            MapLocation myLocation = rc.getLocation();
            float money = rc.getTeamBullets();

            int nearbyEnemies = 0;
            int nearbyAllies = 0;
            int nearbyAllyFighters = 0;
            for (RobotInfo r : nearbyRobots) {
                if (r.getTeam().equals(rc.getTeam().opponent())) nearbyEnemies++;
                else if (r.type == RobotType.SOLDIER || r.type == RobotType.TANK || r.type == RobotType.LUMBERJACK) {
                    nearbyAllies++;
                    if (r.type == RobotType.SOLDIER || r.type == RobotType.TANK) {
                        nearbyAllyFighters++;
                    }
                } else if (walkingTarget == null && r.type == RobotType.ARCHON) {
                    walkingTarget = myLocation.add(r.location.directionTo(myLocation), 10);
                }
            }
            if (nearbyEnemies > 0 && nearbyAllyFighters == 0) {
                Radio.setAlarm();
            }

            // Check build dirs
            Map.generateFarTargets(rc, myLocation, 1000, 0);
            MapLocation nextEnemy = Map.getTarget(0, myLocation);
            boolean unplugged = true;
            if (nextEnemy != null && nextEnemy.distanceTo(myLocation) > 17 && nearbyAllies > 1) {
                if (DEBUG) System.out.println("Plukked");
                unplugged = false;
            }
            if (nearbyEnemies == 0) {
                for (int i = 0; i < buildDirs.length; i++) {
                    buildDirValid[i] = !rc.isCircleOccupied(myLocation.add(buildDirs[i], 3.5f), 1.499f) && unplugged && rc.onTheMap(myLocation.add(buildDirs[i], 5f));
                    if (DEBUG && buildDirValid[i]) rc.setIndicatorDot(myLocation.add(buildDirs[i], 2f), 90, 255, 90);
                    if (DEBUG && !buildDirValid[i]) rc.setIndicatorDot(myLocation.add(buildDirs[i], 2f), 180, 0, 0);
                }
            } else {
                for (int i = 0; i < buildDirs.length; i++) {
                    buildDirValid[i] = !rc.isCircleOccupied(myLocation.add(buildDirs[i], 2f), 0.999f) && rc.onTheMap(myLocation.add(buildDirs[i], 3f));
                    if (DEBUG && buildDirValid[i]) rc.setIndicatorDot(myLocation.add(buildDirs[i], 2f), 90, 255, 90);
                    if (DEBUG && !buildDirValid[i]) rc.setIndicatorDot(myLocation.add(buildDirs[i], 2f), 180, 0, 0);
                }
            }
            boolean freePos = false;
            if (freeDir < 0 || !buildDirValid[freeDir] && frame - lastBuild > 30) {
                int nFreeDir = -1;
                Direction dir = myLocation.directionTo(Map.enemyArchonPos[(int) (rand() * Map.enemyArchonPos.length)]);
                for (int i = 0; i < buildDirs.length; i++) {
                    if ((nFreeDir < 0 || Math.abs(dir.degreesBetween(buildDirs[i])) < Math.abs(dir.degreesBetween(buildDirs[nFreeDir]))) && buildDirValid[i])
                        nFreeDir = i;
                }
                if (freeDir < 0) {
                    freeDir = (int) (rand() * buildDirs.length);
                }
                if (nFreeDir >= 0 && buildDirValid[nFreeDir]) {
                    freeDir = nFreeDir;
                } else {
                    freePos = true; //no space to build
                }
            }
            if (DEBUG && buildDirValid[freeDir])
                rc.setIndicatorDot(myLocation.add(buildDirs[freeDir], 2f), 190, 255, 190);
            if (freePos) {
                if (noBuildPosSince > frame) noBuildPosSince = frame;
                if (frame - noBuildPosSince > 69 && frame % 23 == 0 && Radio.countAllies(RobotType.GARDENER) > 5) {
                    if (DEBUG && freePos) System.out.println("No build pos, going full eco");
                } else {
                    freePos = false;
                }
            } else {
                noBuildPosSince = 10000;
            }
            freePos |= !Util.fireAllowed; // in endgame build all hexes

            int wouldBeTreeDir = -1;
            active = true;
            if (!_active) {
                disabledSince = Math.min(frame, disabledSince);
                if (frame - disabledSince > 20) active = false;
            } else {
                disabledSince = 100000;
            }
            if (rc.getBuildCooldownTurns() <= 0 && money > GameConstants.BULLET_TREE_COST) {
                _active = false;
                for (int i = 0; i < buildDirs.length; i++) {
                    if ((treesPlanted == 0 || i % 2 == directionModulus) && rc.canPlantTree(buildDirs[i])) {
                        if (treesPlanted == 0 && (freeDir - i) % 2 != 0) continue;
                        if (!freePos && Math.abs(i - freeDir) <= 1) {
                            continue; //reserved spot
                        }
                        wouldBeTreeDir = i;
                        _active = buildDirValid[freeDir];
                        break;
                    }
                }
            }

            // Trees
            if (money > MIN_CONSTRUCTION_MONEY && BuildPlanner.buildTree() && inPosition && buildDirValid[freeDir] && wouldBeTreeDir >= 0) {
                //request to cut annoying trees
                for (TreeInfo t : trees) {
                    if (t.team == myTeam)
                        continue;
                }
                lastTreePlant = frame;
                rc.plantTree(buildDirs[wouldBeTreeDir]);
                treesPlanted++;
                directionModulus = wouldBeTreeDir % 2;
            }

            TreeInfo[] tis = rc.senseNearbyTrees(3.0f);
            int lowestHealthTree = -1;
            float lowestHealth = 1e10f;
            for (TreeInfo ti : tis) {
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
            if (money > RobotType.SCOUT.bulletCost && rc.getBuildCooldownTurns() == 0) {
                RobotType toBuild = BuildPlanner.getUnitToBuild();
                System.out.println("I want to build " + toBuild);
                tryBuild(toBuild);
            }


            // Positioning
            if (treesPlanted == 0) {
                MapLocation nextEnemyFarDangerous = Map.getTarget(4, myLocation);
                MapLocation nextEnemyFarScout = Map.getTarget(8, myLocation);
                if (nextEnemyFarDangerous != null && nextEnemyFarScout != null) {
                    if (nextEnemyFarDangerous.distanceTo(myLocation) < nextEnemyFarScout.distanceTo(myLocation)) {
                        nextEnemyFarScout = null;
                    } else {
                        nextEnemyFarDangerous = null;
                    }
                }
                Movement.MIN_OBSTACLE_DIST = 0;
                if (nextEnemyFarDangerous != null && nextEnemyFarDangerous.distanceTo(myLocation) < 12) {
                    walkingTarget = myLocation.add(nextEnemyFarDangerous.directionTo(myLocation), 5);
                } else if (nextEnemyFarScout != null && nextEnemyFarScout.distanceTo(myLocation) < 8.5f) {
                    walkingTarget = myLocation.add(nextEnemyFarScout.directionTo(myLocation), 3);
                } else {
                    if (frame - spawnFrame > 16) {
                        walkingTarget = myLocation;
                        Movement.MIN_OBSTACLE_DIST = 3;
                    }
                }
                Movement.init(nearbyRobots, trees, bullets);
                inPosition = !movement.findPath(walkingTarget, null) || frame - spawnFrame > 31 && rc.getTreeCount() < 4;
                if (DEBUG) System.out.println("Gardener positioning");
            }

        } catch (Exception e) {
            if (Util.DEBUG) System.out.println("Gardener Exception");
            e.printStackTrace();
        }
    }
}
