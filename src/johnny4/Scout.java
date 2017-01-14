package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Scout {

    RobotController rc;
    Map map;
    Radio radio;
    Movement movement;
    final boolean isShaker;
    final boolean isRoamer;

    public Scout(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.movement = new Movement(rc);
        isShaker = rc.getID() % 5 == 0;
        isRoamer = rc.getID() % 2 == 0;
        for (int i = 0; i < visitedBroadcasts.length; i++) {
            visitedBroadcasts[i] = new MapLocation(0, 0);
        }
        lastRandomLocation = map.archonPos[(int) (map.archonPos.length * rand())];
    }

    public void run() {
        while (true) {
            tick();
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
                    if ((ut == RobotType.GARDENER) && (civMinDist > r.location.distanceTo(myLocation) || lastCivilian != null && r.location.distanceTo(lastCivilian) < 3)) {
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
                if (!isRoamer) {
                    if (lastCivilian != null && lastCivilian.distanceTo(myLocation) > 0.8f * RobotType.SCOUT.sensorRadius) {
                        nextCivilian = lastCivilian;
                    } else {
                        nextCivilian = map.getTarget(myLocation, 2, 10, 0.8f * RobotType.SCOUT.sensorRadius);
                        lastCivilian = null;
                    }
                    if (nextCivilian == null) {
                        //nextCivilian = map.getTarget(myLocation, 2, 30, 3.5f * RobotType.SCOUT.sensorRadius);
                        if (nextCivilian == null) {
                            System.out.println("no target");
                        }
                    }
                } else {
                    nextCivilian = map.getTarget(myLocation, 2, 20, 0.8f * RobotType.SCOUT.sensorRadius);
                    if (nextCivilian == null) {
                        if (lastCivilian != null && lastCivilian.distanceTo(myLocation) > 0.8f * RobotType.SCOUT.sensorRadius) {
                            nextCivilian = lastCivilian;
                        } else {
                            lastCivilian = null;
                            MapLocation[] broadcasts = rc.senseBroadcastingRobotLocations();
                            if (broadcasts.length > 0) {
                                for (MapLocation bc : broadcasts) {
                                    boolean invalid = false;
                                    for (MapLocation known : visitedBroadcasts) {
                                        if (known.distanceTo(bc) < 6) {
                                            invalid = true;
                                        }
                                    }
                                    if (invalid) continue;
                                    if (nextCivilian == null || nextCivilian.distanceTo(myLocation) < bc.distanceTo(myLocation)) {
                                        nextCivilian = bc;
                                    }
                                }
                                if (nextCivilian != null) {
                                    visitedBroadcasts[rollingBroadcastIndex++] = nextCivilian;
                                    rollingBroadcastIndex %= visitedBroadcasts.length;
                                }
                                System.out.println("Going to broadcaster at " + nextCivilian);
                            }
                        }
                    }
                }
            }
            if (nextCivilianInfo != null && lastCivilianInfo != null) {
                nextCivilian = predict(nextCivilianInfo, lastCivilianInfo);
            }
            lastCivilian = nextCivilian;
            lastCivilianInfo = nextCivilianInfo;


            if (nextEnemy == null && nextCivilian == null) {
                longRangeEnemy = true;
                nextEnemy = map.getTarget(myLocation, 0, 9, 0.8f * RobotType.SCOUT.sensorRadius);
                if (nextEnemy == null) {
                    nextEnemy = map.getTarget(myLocation, 3, 80);
                }
            }
            float dist = 100000f;
            if (nextEnemy != null) {
                dist = myLocation.distanceTo(nextEnemy);
            }

            boolean safe = false;
            boolean cantfire = false;
            if (!safe) {
                for (TreeInfo ti : trees) {
                    if (nextEnemy != null && ti.location.distanceTo(nextEnemy) - ti.radius < myLocation.distanceTo(nextEnemy) - RobotType.SCOUT.bodyRadius && Math.abs(nextEnemy.directionTo(myLocation).degreesBetween(nextEnemy.directionTo(ti.location))) < 20) {
                        safe = true;
                    }
                    if (myLocation.distanceTo(ti.location) + RobotType.SCOUT.bodyRadius < ti.radius || nextCivilian != null && myLocation.distanceTo(nextCivilian) - GameConstants.BULLET_SPAWN_OFFSET > ti.location.distanceTo(nextCivilian) && Math.abs(nextCivilian.directionTo(myLocation).degreesBetween(nextCivilian.directionTo(ti.location))) < 10) {
                        cantfire = true;
                        System.out.println("Can't fire from here");
                    }
                }
            }
            boolean hasMoved = false;
            movement.evadeBullets = !safe;
            if (!safe) {
            } else {
                System.out.println("Scout safe, dont evade");
            }
            float lumberDist = 10000f;


            //Movement
            if (toShake != null && dist > 5) {
                //System.out.println("Shaking " + toShake.getLocation());
                if (!hasMoved && !movement.findPath(toShake.getLocation(), null)) {
                    toShake = null;
                } else {
                    hasMoved = true;
                    myLocation = rc.getLocation();
                }
                if (rc.canShake(toShake.getID())) {
                    rc.shake(toShake.getID());
                    System.out.println("Shaken " + toShake.getLocation());
                    toShake = null;
                }
            }
            if (!hasMoved) {
                toShake = null;
                if (nearbyAllies > 5 + rc.getID() % 5) { // Don't stay in clusterfucks, go somewhere else
                    //System.out.println("Too many allies.");
                }
                if (nextCivilian != null && nearbyAllies < 5 + rc.getID() % 5) {
                    //System.out.println("attacking " + nextCivilian + " : " + longRangeCiv);

                    float attackDistance = 3.1f; //Distance from which to start firing at enemies
                    if (!longRangeCiv && nextCivilianInfo.moveCount <= 0) {
                        attackDistance += 4;
                    }
                    if (nextCivilian.distanceTo(myLocation) - civSize > attackDistance) {
                        if (!hasMoved && !movement.findPath(nextCivilian, null)) {
                        } else {
                            hasMoved = true;
                            myLocation = rc.getLocation();
                        }
                    }
                    if (nextCivilian.distanceTo(myLocation) - civSize < attackDistance) {
                        boolean hasFired = longRangeCiv || cantfire; //Don't shoot at units outside vision
                        Direction fireDir = null;
                        if (!hasFired && checkLineOfFire(myLocation, nextCivilian, trees, nearbyRobots, RobotType.SCOUT.bodyRadius)) {
                            if (rc.canFireSingleShot()) {
                                fireDir = myLocation.directionTo(nextCivilian);
                                rc.fireSingleShot(fireDir);
                                hasFired = true;
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
                        if (!hasFired && checkLineOfFire(myLocation, nextCivilian, trees, nearbyRobots, RobotType.SCOUT.bodyRadius)) {
                            if (rc.canFireSingleShot()) {
                                fireDir = myLocation.directionTo(nextCivilian);
                                rc.fireSingleShot(fireDir);
                            }
                        }
                    }
                } else { //No civilian, search the map
                    if (!hasMoved) {
                        while (lastRandomLocation.distanceTo(myLocation) < 0.6 * RobotType.SCOUT.sensorRadius || !movement.findPath(lastRandomLocation, null)) {
                            lastRandomLocation = myLocation.add(randomDirection(), 20);
                        }
                    }
                }
                if (Clock.getBytecodesLeft() < 1000) {
                    System.out.println("Aborting scout at " + myLocation + " early");
                    return;
                }
                for (TreeInfo t : trees) {
                    if (t.getContainedBullets() > 0 && rc.canShake(t.location)) {
                        rc.shake(t.location);
                        System.out.println("Shaken " + t.getLocation() + " gaining " + t.getContainedBullets() + " bullets (not shaker)");
                    }
                    if (t.containedRobot != null) {
                        radio.requestTreeCut(t);
                        //System.out.println("Requesting christmas tree to be cut!");
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
                    System.out.println("Scout took " + (rc.getRoundNum() - frame) + " frames at " + frame + " : " + longRangeCiv + " " + longRangeEnemy);
                }
            }

        } catch (Exception e) {
            System.out.println("Scout Exception");
            e.printStackTrace();
        }
    }

}
