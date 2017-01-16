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
    boolean isAggro;
    boolean initialized = false;

    public Scout(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.movement = new Movement(rc);
        isRoamer = rc.getID() % 2 == 0;
        for (int i = 0; i < visitedBroadcasts.length; i++) {
            visitedBroadcasts[i] = new MapLocation(0, 0);
        }
        lastRandomLocation = map.archonPos[(int) (map.archonPos.length * rand())];
    }

    public void run() {
        while (true) {
            int frame = rc.getRoundNum();
            tick();
            if (frame != rc.getRoundNum()) {
                if (Util.DEBUG) System.out.println("BYTECODE OVERFLOW");
            }
            Clock.yield();
        }
    }

    MapLocation lastRandomLocation;
    TreeInfo toShake = null;
    MapLocation lastCivilian = null;
    RobotInfo lastCivilianInfo = null;
    BulletInfo[] bullets;
    MapLocation[] visitedBroadcasts = new MapLocation[10];
    int rollingBroadcastIndex = 0;


    float circleDir = 0f;

    protected void tick() {
        try {
            preTick();
            if (!initialized) {
                isAggro = radio.countAllies(RobotType.SCOUT) >= 2 && rand() > 0.8f;
                isShaker = radio.countAllies(RobotType.SCOUT) <= 2  && rand() > 0.3f || rand() < 0.2f;
                initialized = true;
                if (isAggro){
                    Movement.MIN_ENEMY_DIST = 4.5f;
                }
            }
            if (Util.DEBUG) System.out.println("This scout is shaker: " + isShaker + ", roamer: " + isRoamer + " and aggro: " + isAggro);
            int frame = rc.getRoundNum();
            radio.frame = frame;
            MapLocation myLocation = rc.getLocation();

            MapLocation nextEnemy = null;
            MapLocation nextCivilian = null;
            RobotInfo nextCivilianInfo = null;
            float civSize = 0;
            float civMinDist = 10000f;
            boolean longRangeCiv = false;
            boolean longRangeEnemy = false;
            int nearbyAllies = 0;

            //Sensing/Radio

            RobotInfo nearbyRobots[] = map.sense();
            TreeInfo trees[] = senseBiggestTrees();
            bullets = rc.senseNearbyBullets();
            movement.init(nearbyRobots, trees, bullets);

            //Target selection
            for (RobotInfo r : nearbyRobots) {

                RobotType ut = r.getType();
                if (!r.getTeam().equals(rc.getTeam())) {
                    if ((ut == RobotType.GARDENER || isAggro && ut != RobotType.ARCHON) && (civMinDist > r.location.distanceTo(myLocation) || lastCivilian != null && r.location.distanceTo(lastCivilian) < 3)) {
                        nextCivilian = r.location;
                        nextCivilianInfo = r;
                        civMinDist = (lastCivilian != null && r.location.distanceTo(lastCivilian) < 3) ? 0f : (r.location.distanceTo(myLocation));
                        civSize = ut.bodyRadius;
                    }
                    if ((ut == RobotType.LUMBERJACK || ut == RobotType.SOLDIER || ut == RobotType.TANK) && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > r.location.distanceTo(myLocation)) && r.moveCount + r.attackCount > 0) {
                        nextEnemy = r.location;
                    }
                } else {
                    nearbyAllies++;
                }
            }
            if (nextCivilian == null) {
                longRangeCiv = true;
                if (Util.DEBUG) System.out.println("Using long range civilian " + Clock.getBytecodeNum());
                if (frame % 3 == 0) {
                    map.generateFarTargets(myLocation, 1000, 0);
                }
                nextCivilian = map.getTarget(isAggro ? 1 : 2, myLocation);
                if (nextCivilian != null && nextCivilian.distanceTo(myLocation) < 0.6 * RobotType.SCOUT.sensorRadius) {
                    Radio.deleteEnemyReport(nextCivilian);
                }
                //if (nextCivilian == null) nextCivilian = map.getTarget(3, myLocation);
            }
            if (nextCivilianInfo != null && lastCivilianInfo != null) {
                nextCivilian = predict(nextCivilianInfo, lastCivilianInfo);
            }
            lastCivilian = nextCivilian;
            lastCivilianInfo = nextCivilianInfo;


            if (nextEnemy == null && nextCivilian == null) {
                longRangeEnemy = true;
                /*nextEnemy = map.getTarget(myLocation, 0, 9, 0.8f * RobotType.SCOUT.sensorRadius);
                if (nextEnemy == null) {
                    nextEnemy = map.getTarget(myLocation, 3, 80);
                }*/
            }
            float dist = 100000f;
            if (nextEnemy != null) {
                dist = myLocation.distanceTo(nextEnemy);
            }

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
            if (Util.DEBUG) System.out.println("Starting movement " + Clock.getBytecodeNum());
            if (toShake != null && dist > 5) {
                //if (Util.DEBUG) System.out.println("Shaking " + toShake.getLocation());
                if (!hasMoved && !movement.findPath(toShake.getLocation(), null)) {
                    toShake = null;
                } else {
                    hasMoved = true;
                    myLocation = rc.getLocation();
                }
                if (rc.canShake(toShake.getID())) {
                    rc.shake(toShake.getID());
                    if (Util.DEBUG) System.out.println("Shaken " + toShake.getLocation());
                    toShake = null;
                }
            }
            if (!hasMoved) {
                toShake = null;
                if (nearbyAllies > 5 + rc.getID() % 5) { // Don't stay in clusterfucks, go somewhere else
                    //if (Util.DEBUG) System.out.println("Too many allies.");
                }
                if (nextCivilian != null && nearbyAllies < 5 + rc.getID() % 5) {
                    //if (Util.DEBUG) System.out.println("attacking " + nextCivilian + " : " + longRangeCiv);

                    float attackDistance = 3.3f; //Distance from which to start firing at enemies
                    if (!longRangeCiv && nextCivilianInfo.moveCount <= 0) {
                        attackDistance += 4;
                    }
                    if (nextCivilian.distanceTo(myLocation) - civSize > attackDistance || longRangeCiv) {
                        if (!hasMoved && !movement.findPath(nextCivilian, null)) {
                        } else {
                            hasMoved = true;
                            myLocation = rc.getLocation();
                        }
                    }
                    if (!longRangeCiv && Math.min(nextCivilian.distanceTo(myLocation), nextCivilianInfo.location.distanceTo(myLocation)) - civSize < attackDistance) {
                        boolean hasFired = longRangeCiv || cantfire; //Don't shoot at units outside vision
                        Direction fireDir = null;
                        if (!hasFired) {
                            if (checkLineOfFire(myLocation, nextCivilian, trees, nearbyRobots, RobotType.SCOUT.bodyRadius) && rc.canFireSingleShot()) {
                                fireDir = myLocation.directionTo(nextCivilian);
                                rc.fireSingleShot(fireDir);
                                hasFired = true;
                            } else {
                                if (Util.DEBUG) System.out.println("No LOS");
                            }
                        }
                        if (!hasMoved && !longRangeCiv) { //Try to hide behind tree
                            TreeInfo best = null;
                            float mindist = 100000;
                            for (TreeInfo ti : trees) {
                                if (ti.location.distanceTo(nextCivilian) - ti.radius - ((ti.getID() % 17) / 170f) < mindist) {
                                    mindist = ti.location.distanceTo(nextCivilian) - ti.radius - ((ti.getID() % 17) / 170f);
                                    best = ti;
                                }
                            }
                            if (mindist < attackDistance - nextCivilianInfo.moveCount * nextCivilianInfo.type.strideRadius) {
                                MapLocation nextDanger = nextEnemy == null ? nextCivilian : nextEnemy;
                                MapLocation pos = best.location.add(best.location.directionTo(nextDanger), best.radius - RobotType.SCOUT.bodyRadius - GameConstants.BULLET_SPAWN_OFFSET / 2);
                                if (myLocation.equals(pos)) {
                                    hasMoved = true;
                                } else if (movement.findPath(pos, fireDir)) {
                                    hasMoved = true;
                                    myLocation = rc.getLocation();
                                }
                            }
                        }
                        if (!hasMoved) { // Alternatively circle towards enemy
                            if (movement.findPath(nextCivilian, fireDir)) {
                                hasMoved = true;
                                myLocation = rc.getLocation();
                            }
                        }
                        if (!hasFired) {
                            if (checkLineOfFire(myLocation, nextCivilian, trees, nearbyRobots, RobotType.SCOUT.bodyRadius) && rc.canFireSingleShot()) {
                                fireDir = myLocation.directionTo(nextCivilian);
                                rc.fireSingleShot(fireDir);
                                hasFired = true;
                            } else {
                                if (Util.DEBUG) System.out.println("No LOS");
                            }
                        }
                    }else{
                        if (Util.DEBUG) System.out.println("Enemy out of range");
                    }
                } else { //No civilian, search the map
                    if (!hasMoved) {
                        while (lastRandomLocation.distanceTo(myLocation) < 0.6 * RobotType.SCOUT.sensorRadius || !rc.onTheMap(myLocation.add(myLocation.directionTo(lastRandomLocation), 4)) || !movement.findPath(lastRandomLocation, null)) {
                            lastRandomLocation = myLocation.add(randomDirection(), 100);
                        }
                    }
                }
                if (Clock.getBytecodesLeft() < 1000) {
                    if (Util.DEBUG) System.out.println("Aborting scout at " + myLocation + " early");
                    return;
                }
                for (TreeInfo t : trees) {
                    if (t.getContainedBullets() > 0 && rc.canShake(t.location)) {
                        rc.shake(t.location);
                        if (Util.DEBUG) System.out.println("Shaken " + t.getLocation() + " gaining " + t.getContainedBullets() + " bullets (not shaker)");
                    }
                    if (t.containedRobot != null) {
                        radio.requestTreeCut(t);
                        //if (Util.DEBUG) System.out.println("Requesting christmas tree to be cut!");
                    }
                    if (Clock.getBytecodesLeft() < 100) return;
                }
                if (toShake == null && isShaker) {
                    for (TreeInfo t : trees) {
                        if (t.getContainedBullets() > 10) {
                            toShake = t;
                            break;
                        }
                    }
                }
                if (rc.getRoundNum() - frame > 0 && frame % 8 != 0 && (longRangeCiv == false && longRangeEnemy == false)) {
                    if (Util.DEBUG) System.out.println("Scout took " + (rc.getRoundNum() - frame) + " frames at " + frame + " : " + longRangeCiv + " " + longRangeEnemy);
                }
            }

        } catch (Exception e) {
            if (Util.DEBUG) System.out.println("Scout Exception");
            e.printStackTrace();
        }
    }

}
