package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Gardener {

    RobotController rc;
    Radio radio;

    Map map;
    Direction[] treeDirs;
    Team myTeam;
    Team enemyTeam;
    int lastWatered;
    RobotType lastBuilt;
    RobotType lastThreat = RobotType.SCOUT;
    float health;
    int roundsSinceAttack;
    BulletInfo bullets[];
    boolean isFleeing;
    boolean waitingForScout;
    int plantedTrees = 0;
    Grid grid;
    Movement movement;
    TreeStorage treeStorage;

    public Gardener(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.grid = new SimpleHexagonalClusters(rc);
        this.movement = new Movement(rc);
        this.treeStorage = new TreeStorage(rc);
        this.treeDirs = new Direction[6];
        float angle = (float) Math.PI / 3.0f;
        for (int i = 0; i < 6; i++) {
            this.treeDirs[i] = new Direction(angle * i);
        }
        this.lastWatered = 0;
        this.myTeam = rc.getTeam();
        this.enemyTeam = rc.getTeam().opponent();
        this.lastBuilt = RobotType.LUMBERJACK;
        this.health = rc.getHealth();
        this.roundsSinceAttack = 999999;
        this.waitingForScout = rc.getRoundNum() < 100;
    }

    public void run() {
        while (true) {
            tick();
            Clock.yield();
        }
    }

    public void build(RobotType robotType, Direction dir) throws GameActionException {
        rc.buildRobot(robotType, dir);
        Radio.reportBuild(robotType);
        lastBuilt = robotType;
    }

    protected void tick() {
        try {
            preTick();

            //Sensing
            roundsSinceAttack++;
            int frame = rc.getRoundNum();
            radio.frame = frame;
            bullets = rc.senseNearbyBullets();
            MapLocation myLocation = rc.getLocation();


            RobotInfo[] nearbyRobots = map.sense();
            TreeInfo[] trees = senseBiggestTrees();
            MapLocation nextEnemy = null;
            int nearbyProtectors = 0;
            for (RobotInfo r : nearbyRobots) {
                if (!r.getTeam().equals(rc.getTeam()) && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > r.location.distanceTo(myLocation)) &&
                        (r.type == RobotType.SCOUT || r.type == RobotType.LUMBERJACK || r.type == RobotType.SOLDIER || r.type == RobotType.TANK) && (r.attackCount + r.moveCount > 0 || r.health >= 0.95 * r.type.maxHealth)) {
                    nextEnemy = r.location;
                    lastThreat = r.type;
                }
                if (r.getTeam().equals(rc.getTeam()) && (r.type == RobotType.LUMBERJACK || r.type == RobotType.SOLDIER || r.type == RobotType.TANK) && (r.attackCount + r.moveCount > 0 || r.health >= 0.95 * r.type.maxHealth)) {
                    nearbyProtectors++;
                }
            }
            float newHealth = rc.getHealth();
            if (newHealth < health || nextEnemy != null) {
                roundsSinceAttack = 0;
                radio.setAlarm();
            }
            health = newHealth;

            boolean alarm = radio.getAlarm();
            boolean inDanger = roundsSinceAttack < 10 || isFleeing;
            boolean rich = rc.getTeamBullets() > 1.2f * RobotType.SOLDIER.bulletCost;


            RobotType scouted = null;
            if (waitingForScout) {
                if (frame > 100)
                    waitingForScout = false;
                MapLocation target = map.getTarget(myLocation, 4, 50, 0);
                if (target != null && target.distanceTo(myLocation) < 42) {
                    scouted = Radio.intToType(map._type);
                    System.out.println("Scouted " + scouted + " rush");
                }
            }


            //Take care of trees
            movement.init(nearbyRobots, trees, bullets);
            treeStorage.updateTrees(trees);
            boolean hasMoved = false;

            if (frame % 9 == 0) {
                treeStorage.updateTrees(trees);
            } else {

                if (!isFleeing) {

                    if (!inDanger && (!alarm || rich)) {
                        //Try building new trees
                        if (rc.getTeamBullets() > GameConstants.BULLET_TREE_COST && Math.min(plantedTrees, treeStorage.storedTrees) < 6 && (radio.getUnitCounter() >= 4 || plantedTrees <= frame / 200) && frame > 10) {
                            MapLocation treeloc = grid.getNearestPlantableLocation(myLocation, null);
                            if (treeloc != null) {
                                MapLocation walkloc = grid.getNearestWalkableLocation(treeloc);
                                hasMoved = movement.findPath(walkloc, null);
                                myLocation = rc.getLocation();
                                rc.setIndicatorDot(walkloc, 0, 0, 255);
                                rc.setIndicatorDot(treeloc, 0, 255, 0);
                                if ((myLocation.distanceTo(walkloc) < 0.01) && rc.canPlantTree(myLocation.directionTo(treeloc))) {
                                    rc.plantTree(myLocation.directionTo(treeloc));
                                    plantedTrees++;
                                }
                            }
                        }

                        //Try watering existing trees
                        if (!hasMoved) {
                            MapLocation tree = treeStorage.waterTree(nearbyRobots);
                            if (tree != null && movement.findPath(tree.add(tree.directionTo(myLocation), 2), null)) {
                                hasMoved = true;
                                myLocation = rc.getLocation();
                            }
                        }
                        treeStorage.tryWater(); //Try to water any trees in range
                    }
                }
            }
            if (true) return;

            int counts[] = radio.countAllies();
            int ownScouts = counts[Radio.typeToInt(RobotType.SCOUT)];
            int ljCount = counts[Radio.typeToInt(RobotType.LUMBERJACK)];
            int soldierCount = counts[Radio.typeToInt(RobotType.SOLDIER)];
            for (int i = 0; i < 6; i++) {
                // Check for soldier on purpose to allow the Archon to build gardeners
                if ((rc.canBuildRobot(RobotType.SOLDIER, treeDirs[i]) || (rc.canBuildRobot(RobotType.SCOUT, treeDirs[i]) && ownScouts < radio.getArchonCounter())) &&
                        (!alarm || rich || inDanger) && rc.isBuildReady() && nearbyProtectors < 3 && (frame > 100 || scouted != null || lastBuilt == RobotType.LUMBERJACK && ownScouts < radio.getArchonCounter())) {
                    if (waitingForScout && scouted != null) {
                        if (!rc.canBuildRobot(RobotType.SOLDIER, treeDirs[i])) break;

                        RobotType response;
                        if (scouted.equals(RobotType.LUMBERJACK)) {
                            response = RobotType.LUMBERJACK;
                        } else {
                            response = RobotType.SOLDIER;
                        }
                        build(response, treeDirs[i]);
                        break;
                    }
                    if (inDanger) {
                        if (!rc.canBuildRobot(RobotType.SOLDIER, treeDirs[i])) break;
                        RobotType response;
                        if (lastThreat.equals(RobotType.SCOUT) && ljCount < 7) {
                            response = RobotType.LUMBERJACK;
                        } else {
                            response = RobotType.SOLDIER;
                        }
                        build(response, treeDirs[i]);
                        break;
                    }
                    if (ownScouts > 1 && lastBuilt != RobotType.LUMBERJACK && rc.canBuildRobot(RobotType.SOLDIER, treeDirs[i]) && ljCount < 1) {
                        build(RobotType.LUMBERJACK, treeDirs[i]);
                        break;
                    }
                    if (ownScouts < 3) {
                        build(RobotType.SCOUT, treeDirs[i]);
                        break;
                    } else if (ownScouts < 9 && lastBuilt == RobotType.SOLDIER) {
                        build(RobotType.SCOUT, treeDirs[i]);
                        break;
                    } else {
                        if (ljCount < soldierCount && ljCount < 7) {
                            build(RobotType.LUMBERJACK, treeDirs[i]);
                        } else if (soldierCount < ljCount && soldierCount < 22) {
                            build(RobotType.SOLDIER, treeDirs[i]);
                        }
                        break;
                    }
                }
            }


            if (nextEnemy == null) {
                if (isFleeing == true) {
                    System.out.println("Gardener escaped");
                }
                isFleeing = false;
            } else if (nextEnemy.distanceTo(myLocation) < 5) {
                isFleeing = true;
            }

            if (isFleeing && !hasMoved) {
                if (LJ_tryMove(nextEnemy.directionTo(myLocation), 20, 6)) {
                    hasMoved = true;
                    myLocation = rc.getLocation();
                } else {
                    //Welp
                    isFleeing = false;
                    System.out.println("Gardener stuck, resigning");
                }
            }

            if (rc.getRoundNum() - frame > 0) {
                System.out.println("Gardener took " + (rc.getRoundNum() - frame) + " frames at " + frame);
            }

        } catch (Exception e) {
            System.out.println("Gardener Exception");
            e.printStackTrace();
        }
    }

    private boolean canMove(Direction dir) {
        MapLocation nloc = rc.getLocation().add(dir, rc.getType().strideRadius);
        float br = rc.getType().bodyRadius;
        for (BulletInfo bi : bullets) {
            if (bi.location.distanceTo(nloc) < br) {
                return false;
            }
        }
        return rc.canMove(dir);
    }

    private boolean canMove(Direction dir, float dist) {
        try {
            MapLocation nloc = rc.getLocation().add(dir, dist);
            float br = rc.getType().bodyRadius;
            for (BulletInfo bi : bullets) {
                if (bi.location.distanceTo(nloc) < br) {
                    return false;
                }
            }
            return rc.canMove(dir, dist);
        } catch (Exception ex) {

            System.out.println("canMove exception with args " + dir + ": " + dist);
            ex.printStackTrace();
            return false;
        }
    }

    boolean LJ_tryMove(Direction dir) {
        try {
            return LJ_tryMove(dir, 42, 2);
        } catch (Exception ex) {
            return false;
        }
    }

    boolean LJ_tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        if (canMove(dir)) {
            rc.move(dir);
            return true;
        }
        boolean moved = false;
        int currentCheck = 1;

        while (currentCheck <= checksPerSide) {
            try {
                Direction d = dir.rotateLeftDegrees(degreeOffset * currentCheck);
                if (canMove(d)) {
                    rc.move(d);
                    return true;
                }
                d = dir.rotateRightDegrees(degreeOffset * currentCheck);
                if (canMove(d)) {
                    rc.move(d);
                    return true;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            currentCheck++;
        }
        return false;
    }
}
