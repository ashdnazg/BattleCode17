package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Scout {

    RobotController rc;
    Map map;
    Radio radio;
    Movement movement;
    boolean isShaker;
    final boolean isRoamer;
    boolean isSpotter;
    boolean initialized = false;
    boolean chaseAllScouts = true;
    float lastHP;
    MapLocation[] archonPositions;
    int[] lastArchonCheck;

    public Scout(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.movement = new Movement(rc);
        isRoamer = rc.getID() % 2 == 0;
        for (int i = 0; i < visitedBroadcasts.length; i++) {
            visitedBroadcasts[i] = new MapLocation(0, 0);
        }

        stoppedScoutAttack = rc.getRoundNum();
        MapLocation myLocation = rc.getLocation();
        lastHP = rc.getHealth();
        float dist = 1e10f;
        float curDist;
        archonPositions = map.enemyArchonPos;
        lastArchonCheck = new int[archonPositions.length];
        for (int i = 0; i < archonPositions.length; i++) {
            lastArchonCheck[i] = -1000;
        }
        for (MapLocation archonPos : map.enemyArchonPos) {
            curDist = archonPos.distanceTo(myLocation);
            if (curDist < dist) {
                lastRandomLocation = archonPos;
                dist = curDist;
            }
        }
        lastCivContact = rc.getRoundNum();
        myID = rc.getID();
        setSpotter(true);
        chaseAllScouts = rand() > 10.7f;
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

    MapLocation lastRandomLocation;
    TreeInfo toShake = null;
    MapLocation lastCivilian = null;
    RobotInfo lastCivilianInfo = null;
    RobotInfo lastEnemyInfo = null;
    BulletInfo[] bullets;
    MapLocation[] visitedBroadcasts = new MapLocation[10];
    int rollingBroadcastIndex = 0;
    int lastCivContact = 0;
    int stoppedScoutAttack = 0;
    int attackStartTime = 100000;
    double initialHP = 100000;
    final int myID;


    void setSpotter(boolean value) {
        if (isSpotter == value) return;

        if (DEBUG) System.out.println("Converting to spotter " + value);
        isSpotter = value;
        if (isSpotter) {
            Movement.MIN_ENEMY_DIST = 11f;
            Movement.MIN_ENEMY_SCOUT_DIST = 6f;
            Movement.MIN_ENEMY_LUMBERJACK_DIST = 5f;
        }
        if (!isSpotter) {
            Movement.MIN_ENEMY_DIST = 5.5f;
            Movement.MIN_ENEMY_SCOUT_DIST = 0f;
            Movement.MIN_ENEMY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + RobotType.LUMBERJACK.strideRadius;
        }
    }

    protected void tick() {
        try {
            preTick();
            if (!initialized) {
                setSpotter((radio.countAllies(RobotType.SCOUT) >= 2 || rc.getRoundNum() > 200) && rand() < 0.8f || true);
                isShaker = radio.countAllies(RobotType.SCOUT) <= 2;
                initialized = true;
            }
            if (Util.DEBUG)
                System.out.println("This scout is shaker: " + isShaker + ", roamer: " + isRoamer + " and spotter: " + isSpotter + " scouttacker: " + chaseAllScouts);
            if (Util.DEBUG)
                System.out.println("mindist: " + Movement.MIN_ENEMY_DIST);
            int frame = rc.getRoundNum();
            radio.frame = frame;
            MapLocation myLocation = rc.getLocation();

            MapLocation nextEnemy = null;
            MapLocation nextArchon = null;
            MapLocation nextCivilian = null;
            RobotInfo nextCivilianInfo = null;
            RobotInfo nextEnemyInfo = null;
            RobotInfo nextFighter = null;
            RobotInfo nextFriendlyFighter = null;
            float civSize = 0;
            float civMinDist = 10000f;
            boolean longRangeCiv = false;
            boolean longRangeEnemy = false;
            int nearbyAllies = 0;
            int nearbyAlliedGardeners = 0;
            int nearbyAlliedFighters = 0;
            int nearbyEnemyFighters = 0;
            if (frame - lastCivContact > 90 && !isSpotter) {
                //setSpotter(true);
            }

            //Sensing/Radio

            RobotInfo nearbyRobots[] = map.sense();
            TreeInfo trees[] = senseBiggestTrees();
            bullets = rc.senseNearbyBullets();
            movement.init(nearbyRobots, trees, bullets);

            //Target selection
            for (RobotInfo r : nearbyRobots) {

                RobotType ut = r.getType();
                if (r.getTeam().equals(rc.getTeam())) {
                    nearbyAllies++;
                    if ((ut == RobotType.LUMBERJACK || ut == RobotType.SOLDIER || ut == RobotType.TANK) && r.moveCount + r.attackCount > 0 && r.location.distanceTo(myLocation) < 16) {
                        nearbyAlliedFighters++;
                    }
                    if ((ut == RobotType.GARDENER)) {
                        nearbyAlliedGardeners++;
                    }
                } else if ((ut == RobotType.LUMBERJACK || ut == RobotType.SOLDIER || ut == RobotType.TANK || ut == RobotType.SCOUT) && (r.moveCount + r.attackCount > 0 || r.health / r.type.maxHealth > 0.9f)) {
                    nearbyEnemyFighters++;
                } else if (ut == RobotType.ARCHON && (nextArchon == null || nextArchon.distanceTo(myLocation) > r.location.distanceTo(myLocation))) {
                    nextArchon = r.location;
                }
            }
            boolean mustProtectGardener = nearbyAlliedGardeners > 0 && nearbyAlliedFighters <= 0 && nearbyEnemyFighters > 0;
            if (DEBUG) System.out.println("nearbyAlliedGardeners: " + nearbyAlliedGardeners);
            if (DEBUG) System.out.println("nearbyAlliedFighters: " + nearbyAlliedFighters);
            if (lastCivilianInfo != null && lastCivilianInfo.type == RobotType.SCOUT && lastCivilian.distanceTo(myLocation) < 5 && lastHP > rc.getHealth() && lastHP < 0.7 * RobotType.SCOUT.maxHealth) {
                chaseAllScouts = false; //this aint working out
                //setSpotter(false);
                stoppedScoutAttack = frame;
                if (DEBUG) System.out.println("Stopping scout attack");
            }/*
            if (!chaseAllScouts && (frame - stoppedScoutAttack > 100 && lastHP > 0.5 * RobotType.SCOUT.maxHealth || mustProtectGardener)) {
                chaseAllScouts = true;
                if (DEBUG) System.out.println("Restarting scout attack");
            }*/
            for (int i = 0; i < archonPositions.length; i++) {
                if (archonPositions[i].distanceTo(myLocation) < 0.5 * RobotType.SCOUT.sensorRadius) {
                    lastArchonCheck[i] = frame;
                    if (nextArchon != null) archonPositions[i] = nextArchon;
                }
            }
            for (RobotInfo r : nearbyRobots) {

                RobotType ut = r.getType();
                if (!r.getTeam().equals(rc.getTeam())) {
                    if ((ut == RobotType.GARDENER && (!isSpotter || nearbyEnemyFighters == 0) || rc.getHealth() / RobotType.SCOUT.maxHealth > 0.6 && r.getHealth() < 10 * (frame + 1500f) / 1500 && ut != RobotType.SCOUT && nearbyAlliedFighters == 0 || ut == RobotType.SCOUT && (chaseAllScouts)) &&
                            (civMinDist > r.location.distanceTo(myLocation) || lastCivilian != null && r.location.distanceTo(lastCivilian) < 3)) {
                        nextCivilian = r.location;
                        nextCivilianInfo = r;
                        civMinDist = (lastCivilian != null && r.location.distanceTo(lastCivilian) < 3) ? 0f : (r.location.distanceTo(myLocation));
                        civSize = ut.bodyRadius;
                    }
                    if ((ut == RobotType.LUMBERJACK || ut == RobotType.SOLDIER || ut == RobotType.SCOUT || ut == RobotType.TANK) && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > r.location.distanceTo(myLocation)) && r.moveCount + r.attackCount > 0) {
                        nextEnemy = r.location;
                        nextEnemyInfo = r;
                    }
                    if ((ut == RobotType.LUMBERJACK || ut == RobotType.SOLDIER || ut == RobotType.TANK) && (nextFighter == null || nextFighter.location.distanceTo(myLocation) > r.location.distanceTo(myLocation))/* && r.moveCount + r.attackCount > 0*/) {
                        nextFighter = r;
                    }
                }
                if (r.getTeam().equals(rc.getTeam())) {
                    if ((ut == RobotType.SOLDIER || ut == RobotType.TANK) && (nextFriendlyFighter == null || nextFriendlyFighter.location.distanceTo(myLocation) > r.location.distanceTo(myLocation)) && r.moveCount + r.attackCount > 0) {
                        nextFriendlyFighter = r;
                    }
                }
            }
            if (lastCivilianInfo != null && (nextCivilian == null || lastCivilianInfo.ID != nextCivilianInfo.ID)) {
                setSpotter(!isSpotter); //revert pathfinding constants
                setSpotter(!isSpotter);
            }
            if (nextCivilianInfo != null && (lastCivilianInfo == null || lastCivilianInfo.ID != nextCivilianInfo.ID)) {
                if (nextCivilianInfo != null) {
                    attackStartTime = frame;
                    initialHP = nextCivilianInfo.health;
                }
            }
            if (nextCivilianInfo != null && nextCivilianInfo.type == RobotType.SCOUT) {
                Movement.MIN_ENEMY_SCOUT_DIST = 0;
                if (frame - attackStartTime > 20 && (initialHP - nextCivilianInfo.health) < 0.5 * (frame - attackStartTime)) {
                    if (Util.DEBUG) System.out.println("Attack ineffective ");
                    chaseAllScouts = false;
                    stoppedScoutAttack = frame;
                    //setSpotter(false);
                }
            }
            if (nextCivilian == null && (!isSpotter || nextFighter == null)) {
                longRangeCiv = true;
                if (Util.DEBUG) System.out.println("Using long range civilian " + Clock.getBytecodeNum());
                if (frame % 3 == 0) {
                    Map.generateFarTargets(map.rc, myLocation, 1000, 0);
                }
                nextCivilian = Map.getTarget(isSpotter ? 4 : (chaseAllScouts ? 5 : 2), myLocation);
                if (nextCivilian != null && nextCivilian.distanceTo(myLocation) < 0.6 * RobotType.SCOUT.sensorRadius) {
                    Radio.deleteEnemyReport(nextCivilian);
                }
                if (nextCivilian == null) {
                    for (int i = 0; i < archonPositions.length; i++) {
                        if (frame - lastArchonCheck[i] > 100) {
                            if (Util.DEBUG) System.out.println("Going for archon " + Clock.getBytecodeNum());
                            nextCivilian = archonPositions[i];
                            break;
                        }
                    }
                }
            } else {
                lastCivContact = frame;
                if (rand() < 0.01 && isSpotter) {
                    //setSpotter(false);
                }
            }
            if (nextCivilianInfo != null && lastCivilianInfo != null) {
                nextCivilian = predict(nextCivilianInfo, lastCivilianInfo, 0);
            }
            if (nextEnemyInfo != null && lastEnemyInfo != null) {
                nextEnemy = predict(nextEnemyInfo, lastEnemyInfo, 0);
            }
            lastCivilian = nextCivilian;
            lastCivilianInfo = nextCivilianInfo;
            lastEnemyInfo = nextEnemyInfo;


            if (Util.DEBUG) System.out.println("Checking safe " + Clock.getBytecodeNum());

            boolean safe = false;
            boolean cantfire = false;
            if (!safe) {
                for (TreeInfo ti : trees) {
                    if (nextEnemy != null && ti.location.distanceTo(nextEnemy) - ti.radius < myLocation.distanceTo(nextEnemy) - RobotType.SCOUT.bodyRadius && Math.abs(nextEnemy.directionTo(myLocation).degreesBetween(nextEnemy.directionTo(ti.location))) < 20) {
                        safe = true;
                    }
                    if (myLocation.distanceTo(ti.location) + RobotType.SCOUT.bodyRadius + GameConstants.BULLET_SPAWN_OFFSET < ti.radius || nextCivilian != null && myLocation.distanceTo(nextCivilian) - RobotType.SCOUT.bodyRadius - GameConstants.BULLET_SPAWN_OFFSET > ti.location.distanceTo(nextCivilian) - ti.radius && Math.abs(nextCivilian.directionTo(myLocation).degreesBetween(nextCivilian.directionTo(ti.location))) < 10) {
                        cantfire = true;
                        if (Util.DEBUG) System.out.println("Can't fire from here");
                    }
                }
            }
            boolean hasMoved = false;
            movement.evadeBullets = !safe;
            if (!safe) {
            } else {
                if (Util.DEBUG) System.out.println("Scout safe, dont evade");
            }
            float lumberDist = 10000f;


            //Movement
            if (Util.DEBUG)
                System.out.println("Starting movement " + Clock.getBytecodeNum());
            if ( isShaker) {
                for (TreeInfo t : trees) {
                    if (t.getContainedBullets() > 1.0 * Math.max(0, t.location.distanceTo(myLocation) - t.radius)) {
                        if (DEBUG)
                            System.out.println("Added tree at " + t.location + " which contains " + t.containedBullets + " bullets");
                        toShake = t;
                        break;
                    }
                }
            }
            if (toShake != null) {
                if (Util.DEBUG) System.out.println("Shaking " + toShake.getLocation());
                boolean clearShake = false;
                if (!hasMoved && !movement.findPath(toShake.getLocation(), null)) {
                    clearShake = true;
                } else {
                    hasMoved = true;
                    myLocation = rc.getLocation();
                }
                if (toShake != null && rc.canShake(toShake.getID())) {
                    rc.shake(toShake.getID());
                    if (Util.DEBUG) System.out.println("Shaken " + toShake.getLocation());
                    clearShake = true;
                }
                if (clearShake) {
                    toShake = null;
                    cache = new TreeInfo[0];
                    cache2 = new TreeInfo[0];
                }
            }
            boolean hasFired = false; //Don't shoot at units outside vision
            if (!hasMoved) {
                toShake = null;
                if (nearbyAllies > 5 + rc.getID() % 5) { // Don't stay in clusterfucks, go somewhere else
                    if (Util.DEBUG) System.out.println("Too many allies.");
                }
                if (nextCivilian != null && nearbyAllies < 5 + rc.getID() % 5) {
                    if (Util.DEBUG) System.out.println("attacking civilian " + nextCivilian + " : " + longRangeCiv);

                    float attackDistance = 3.3f; //Distance from which to start firing at enemies
                    float dist = nextCivilian.distanceTo(myLocation) - civSize;
                    if (!longRangeCiv) {
                        if (Util.DEBUG) System.out.println("Enemy at " + nextCivilianInfo.location);
                        if (nextCivilianInfo.type == RobotType.SCOUT) {
                            attackDistance = 1.05f;
                        } else {
                            attackDistance = 5f;
                        }
                        if (nextCivilianInfo.moveCount <= 0) {
                            attackDistance += 4;
                        }
                        dist = Math.min(nextCivilian.distanceTo(myLocation), nextCivilianInfo.location.distanceTo(myLocation)) - civSize;
                    }
                    if (dist > attackDistance || longRangeCiv) {
                        if (!hasMoved && !movement.findPath(longRangeCiv ? nextCivilian : nextCivilianInfo.location.add(nextCivilianInfo.location.directionTo(myLocation), 2.005f), null)) {
                        } else {
                            myLocation = rc.getLocation();
                        }
                        hasMoved = true;
                    }
                    if (!longRangeCiv/* && nextCivilianInfo.getID() < myID*/) {
                        dist = Math.min(nextCivilian.distanceTo(myLocation), nextCivilianInfo.location.distanceTo(myLocation)) - civSize;
                    }
                    if (!longRangeCiv && dist < attackDistance) {
                        if (Util.DEBUG) System.out.println("Civilian in range " + dist + " size " + civSize);
                        Direction fireDir = null;
                        if (!hasFired && false) {//don't fire before moving
                            if (checkLineOfFire(myLocation, nextCivilian, trees, nearbyRobots, RobotType.SCOUT.bodyRadius) && Util.fireAllowed && rc.canFireSingleShot()) {
                                fireDir = myLocation.directionTo(nextCivilian);
                                rc.fireSingleShot(fireDir);
                                hasFired = true;
                            } else {
                                if (Util.DEBUG) System.out.println("No LOS");
                            }
                        }
                        if (!hasMoved && !longRangeCiv && nextCivilianInfo.type != RobotType.SCOUT) { //Try to hide behind tree
                            TreeInfo best = null;
                            float mindist = 100000;
                            for (TreeInfo ti : trees) {
                                if (ti.location.distanceTo(nextCivilian) - ti.radius - ((ti.getID() % 17) / 170f) < mindist && ti.radius > 0.8) {
                                    mindist = ti.location.distanceTo(nextCivilian) - ti.radius - ((ti.getID() % 17) / 170f);
                                    best = ti;
                                }
                            }
                            if (mindist < attackDistance - nextCivilianInfo.moveCount * nextCivilianInfo.type.strideRadius) {
                                MapLocation nextDanger = nextEnemy == null ? nextCivilian : nextEnemy;
                                MapLocation pos = best.location.add(best.location.directionTo(nextDanger), best.radius - RobotType.SCOUT.bodyRadius - GameConstants.BULLET_SPAWN_OFFSET / 2);
                                if (DEBUG) System.out.println("Going into tree");
                                if (myLocation.equals(pos)) {
                                    hasMoved = true;
                                } else if (movement.findPath(pos, fireDir)) {
                                    hasMoved = true;
                                    myLocation = rc.getLocation();
                                }
                            }
                        }
                        if (!hasMoved) { // Alternatively circle towards enemy
                            if (movement.findPath(nextCivilianInfo.location.add(nextCivilianInfo.location.directionTo(myLocation), 2.005f), fireDir)) {
                                hasMoved = true;
                                myLocation = rc.getLocation();
                            }
                        }
                        if (!hasFired && !longRangeCiv && !cantfire && (Radio.countAllies(RobotType.GARDENER) > 0 && rc.getTreeCount() > 1 || nearbyAlliedFighters == 0)) {
                            if (checkLineOfFire(myLocation, nextCivilian, trees, nearbyRobots, RobotType.SCOUT.bodyRadius) && Util.fireAllowed && rc.canFireSingleShot()) {
                                fireDir = myLocation.directionTo(nextCivilian);
                                rc.fireSingleShot(fireDir);
                                hasFired = true;
                                Movement.lastLOS = frame;
                                if (Util.DEBUG) System.out.println("Fire!");
                            } else {
                                if (Util.DEBUG) System.out.println("No LOS");
                            }
                        }
                    } else {
                        if (Util.DEBUG) System.out.println("Enemy out of range");
                    }
                } else { //No civilian, search the map
                    if (!hasMoved) {
                        if (isSpotter && nextFighter != null) {
                            if (nextFriendlyFighter != null) {
                                if (Util.DEBUG)
                                    System.out.println("blubbing " + nextFighter.type + " at " + nextFighter.location);
                                movement.findPath(nextFriendlyFighter.location, null);
                            } else {
                                if (Util.DEBUG)
                                    System.out.println("Stalking " + nextFighter.type + " at " + nextFighter.location);
                                movement.findPath(nextFighter.location, null);
                            }
                        } else {
                            while (lastRandomLocation.distanceTo(myLocation) < 0.6 * RobotType.SCOUT.sensorRadius || !rc.onTheMap(myLocation.add(myLocation.directionTo(lastRandomLocation), 4)) || !movement.findPath(lastRandomLocation, null)) {
                                lastRandomLocation = myLocation.add(randomDirection(), 100);
                            }
                        }
                    }
                }
            }

            if (Util.DEBUG) System.out.println("Scout late " + Clock.getBytecodeNum());
            if (Clock.getBytecodesLeft() < 1000) {
                if (Util.DEBUG) System.out.println("Aborting scout at " + myLocation + " early");
                BYTECODE2();
                return;
            }
            if (Util.DEBUG)
                System.out.println(!hasFired + " && " + nextEnemy + " && " + !longRangeEnemy + " && "  + " < 12");
            if (!hasFired && nextEnemy != null && !longRangeEnemy && (nextEnemy.distanceTo(myLocation) < 12 && (Radio.countAllies(RobotType.GARDENER) > 0 && rc.getTreeCount() > 1 || nextEnemy.distanceTo(myLocation) < 4))) {
                if ((checkLineOfFire(myLocation, nextEnemy, trees, nearbyRobots, RobotType.SCOUT.bodyRadius) || nextEnemy.distanceTo(myLocation) < 4.5) && Util.fireAllowed && rc.canFireSingleShot()) {
                    hasFired = true;
                    rc.fireSingleShot(myLocation.directionTo(nextEnemy));
                    Movement.lastLOS = frame;
                    if (Util.DEBUG) System.out.println("Returning fire");
                } else {
                    if (Util.DEBUG) System.out.println("No LOS on enemy");
                }
            }
            if (Util.DEBUG) System.out.println("Scout late2 " + Clock.getBytecodeNum() + " trees: " + trees.length);
            for (TreeInfo t : trees) {
                if (t.getContainedBullets() > 0 && rc.canShake(t.location)) {
                    rc.shake(t.location);
                    if (toShake != null && t.ID == toShake.ID) {
                        toShake = null;
                        cache = new TreeInfo[0];
                        cache2 = new TreeInfo[0];
                    }
                    if (Util.DEBUG)
                        System.out.println("Shaken " + t.getLocation() + " gaining " + t.getContainedBullets() + " bullets (not shaker)");
                }
                if (Clock.getBytecodesLeft() < 100) {
                    BYTECODE2();
                    return;
                }
            }
            if (Util.DEBUG) System.out.println("Scout late3 " + Clock.getBytecodeNum());
            lastHP = rc.getHealth();
            if (rc.getRoundNum() - frame > 0 && frame % 8 != 0 && (longRangeCiv == false && longRangeEnemy == false)) {
                if (Util.DEBUG)
                    System.out.println("Scout took " + (rc.getRoundNum() - frame) + " frames at " + frame + " : " + longRangeCiv + " " + longRangeEnemy);
            }


        } catch (Exception e) {
            if (Util.DEBUG) System.out.println("Scout Exception");
            e.printStackTrace();
        }
    }

}
