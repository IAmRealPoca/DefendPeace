package AI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import CommandingOfficers.Commander;
import CommandingOfficers.CommanderAbility;
import Engine.GameAction;
import Engine.GameActionSet;
import Engine.Path;
import Engine.UnitActionFactory;
import Engine.Utils;
import Engine.XYCoord;
import Engine.Combat.BattleSummary;
import Engine.Combat.CombatEngine;
import Engine.Combat.StrikeParams;
import Engine.UnitActionLifecycles.WaitLifecycle;
import Terrain.GameMap;
import Terrain.Location;
import Terrain.TerrainType;
import Units.Unit;
import Units.UnitModel;
import Units.WeaponModel;

public class AIUtils
{
  /**
   * Finds all actions available to unit, and organizes them by location.
   * @param unit The unit under consideration.
   * @param gameMap The world in which the Unit lives.
   * @return a Map of XYCoord to ArrayList<GameActionSet>. Each XYCoord will have a GameActionSet for
   * each type of action the unit can perform from that location.
   */
  public static Map<XYCoord, ArrayList<GameActionSet> >
                getAvailableUnitActions(Unit unit, GameMap gameMap)
  {
    boolean includeOccupiedDestinations = false;
    return getAvailableUnitActions(unit, gameMap, includeOccupiedDestinations);
  }

  /**
   * Finds all actions available to unit, and organizes them by location.
   * @param unit The unit under consideration.
   * @param gameMap The world in which the Unit lives.
   * @param includeOccupiedDestinations Whether to include destinations underneath our other units.
   * @return a Map of XYCoord to ArrayList<GameActionSet>. Each XYCoord will have a GameActionSet for
   * each type of action the unit can perform from that location.
   */
  public static Map<XYCoord, ArrayList<GameActionSet> >
                getAvailableUnitActions(Unit unit, GameMap gameMap, boolean includeOccupiedDestinations)
  {
    Map<XYCoord, ArrayList<GameActionSet> > actions = new HashMap<XYCoord, ArrayList<GameActionSet> >();

    // Find the possible destinations.
    ArrayList<XYCoord> destinations = Utils.findPossibleDestinations(unit, gameMap, includeOccupiedDestinations);

    for( XYCoord coord : destinations )
    {
      // Figure out how to get here.
      Path movePath = Utils.findShortestPath(unit, coord, gameMap);

      // Figure out what I can do here.
      ArrayList<GameActionSet> actionSets = unit.getPossibleActions(gameMap, movePath, includeOccupiedDestinations);

      // Add it to my collection.
      actions.put(coord, actionSets);
    }

    return actions;
  }

  /**
   * Finds all actions available to unit, and organizes them by type instead of by location.
   * Assumes caller isn't interested in moving into units' current spaces
   * @param unit The unit under consideration.
   * @param gameMap The world in which the Unit lives.
   * @return a Map of ActionType to ArrayList<GameAction>.
   */
  public static Map<UnitActionFactory, ArrayList<GameAction> > getAvailableUnitActionsByType(Unit unit, GameMap gameMap)
  {
    boolean includeOccupiedDestinations = false;
    return getAvailableUnitActionsByType(unit, gameMap, includeOccupiedDestinations);
  }
  /**
   * Finds all actions available to unit, and organizes them by type instead of by location.
   * Assumes caller isn't interested in moving into units' current spaces
   * @param unit The unit under consideration.
   * @param gameMap The world in which the Unit lives.
   * @return a Map of ActionType to ArrayList<GameAction>.
   */
  public static Map<UnitActionFactory, ArrayList<GameAction> > getAvailableUnitActionsByType(Unit unit, GameMap gameMap, boolean includeOccupiedDestinations)
  {
    // Create the ActionType-indexed map, and ensure we don't have any null pointers.
    Map<UnitActionFactory, ArrayList<GameAction> > actionsByType = new HashMap<UnitActionFactory, ArrayList<GameAction> >();
    for( UnitActionFactory atype : unit.model.possibleActions )
    {
      actionsByType.put(atype, new ArrayList<GameAction>());
    }

    // First collect the actions by location.
    Map<XYCoord, ArrayList<GameActionSet> > actionsByLoc = getAvailableUnitActions(unit, gameMap, includeOccupiedDestinations);

    // Now re-map them by type, irrespective of location.
    for( ArrayList<GameActionSet> actionSets : actionsByLoc.values() )
    {
      for( GameActionSet actionSet : actionSets )
      {
        UnitActionFactory type = actionSet.getSelected().getType();

        // Add these actions to the correct map bucket.
        actionsByType.get(type).addAll(actionSet.getGameActions());
      }
    }
    return actionsByType;
  }

  /**
   * Find locations of every property that is not owned by myCo or an ally.
   * @param myCo Properties of this Commander and allies will be ignored.
   * @param gameMap The map to search for properties.
   * @return An ArrayList with the XYCoord of every property that fills the bill.
   */
  public static ArrayList<XYCoord> findNonAlliedProperties(Commander myCo, GameMap gameMap)
  {
    ArrayList<XYCoord> props = new ArrayList<XYCoord>();
    for( int x = 0; x < gameMap.mapWidth; ++x )
    {
      for( int y = 0; y < gameMap.mapHeight; ++y )
      {
        Location loc = gameMap.getLocation(x, y);
        if( loc.isCaptureable() && myCo.isEnemy(loc.getOwner()) )
        {
          props.add(new XYCoord(x, y));
        }
      }
    }
    return props;
  }

  /**
   * Find the locations of all non-allied units.
   * @param myCo Units owned by myCo or allies are skipped.
   * @param gameMap The map to search for units.
   * @return An ArrayList with the XYCoord of every unit not allied with myCo.
   */
  public static ArrayList<XYCoord> findEnemyUnits(Commander myCo, GameMap gameMap)
  {
    ArrayList<XYCoord> unitLocs = new ArrayList<XYCoord>();
    for( int x = 0; x < gameMap.mapWidth; ++x )
    {
      for( int y = 0; y < gameMap.mapHeight; ++y )
      {
        Location loc = gameMap.getLocation(x, y);
        if( loc.getResident() != null && myCo.isEnemy(loc.getResident().CO) )
        {
          unitLocs.add(new XYCoord(x, y));
        }
      }
    }
    return unitLocs;
  }

  /**
   * Creates a map of COs to the units they control, based on what can be seen in the passed-in map.
   * Units owned by allies are ignored - ours can be trivially accessed via Commander.units, and we don't control ally behavior.
   */
  public static Map<Commander, ArrayList<Unit> > getEnemyUnitsByCommander(Commander myCommander, GameMap gameMap)
  {
    Map<Commander, ArrayList<Unit> > unitMap = new HashMap<Commander, ArrayList<Unit> >();

    for( int x = 0; x < gameMap.mapWidth; ++x )
      for( int y = 0; y < gameMap.mapHeight; ++y )
      {
        Unit resident = gameMap.getLocation(x, y).getResident();
        if( (null != resident) && (myCommander.isEnemy(resident.CO)) )
        {
          if( !unitMap.containsKey(resident.CO) ) unitMap.put(resident.CO, new ArrayList<Unit>());
          unitMap.get(resident.CO).add(resident);
        }
      }

    return unitMap;
  }

  /** Overload of {@link #moveTowardLocation(Unit, XYCoord, GameMap, Set)} **/
  public static GameAction moveTowardLocation(Unit unit, XYCoord destination, GameMap gameMap )
  {
    return moveTowardLocation(unit, destination, gameMap, null);
  }

  /**
   * Create and return a GameAction.WaitAction that will move unit towards destination, around
   * any intervening obstacles. If no possible route exists, return false.
   * @param unit The unit we want to move.
   * @param destination Where we eventually want the unit to be.
   * @param gameMap The map on which the unit is moving.
   * @return A GameAction to bring the unit closer to the destination, or null if no available move exists.
   * @param excludeDestinations A list of coordinates of Map locations we don't want to move to.
   */
  public static GameAction moveTowardLocation(Unit unit, XYCoord destination, GameMap gameMap, Set<XYCoord> excludeDestinations )
  {
    GameAction move = null;

    // Find the full path that would get this unit to the destination, regardless of how long. 
    Path path = Utils.findShortestPath(unit, destination, gameMap, true);
    boolean includeOccupiedSpaces = false;
    ArrayList<XYCoord> validMoves = Utils.findPossibleDestinations(unit, gameMap, includeOccupiedSpaces); // Find the valid moves we can make.

    if( path.getPathLength() > 0 && validMoves.size() > 0 ) // Check that the destination is reachable at least in theory.
    {
      path.snip(unit.model.movePower+1); // Trim the path so we go the right immediate direction.
      if( null != excludeDestinations) validMoves.removeAll(excludeDestinations);
      if( !validMoves.isEmpty() )
      {
        Utils.sortLocationsByDistance(path.getEndCoord(), validMoves); // Sort moves based on intermediate destination.
        move = new WaitLifecycle.WaitAction(unit, Utils.findShortestPath(unit, validMoves.get(0), gameMap)); // Move to best option.
      }
    }
    return move;
  }

  /**
   * Return a list of all friendly territories at which a unit can resupply and repair.
   * @param unit
   * @return
   */
  public static ArrayList<XYCoord> findRepairDepots(Unit unit)
  {
    ArrayList<XYCoord> stations = new ArrayList<XYCoord>();
    for( XYCoord xyc : unit.CO.ownedProperties ) // TODO: Revisit if we ever get a CO that repairs on non-owned or non-properties
    {
      Location loc = unit.CO.myView.getLocation(xyc);
      if( unit.model.canRepairOn(loc) )
      {
        stations.add(loc.getCoordinates());
      }
    }
    return stations;
  }
  
  /**
   * Find a usable ability that has all specified flags, and add it to the provided queue.
   */
  public static CommanderAbility queueCromulentAbility(Queue<GameAction> q, Commander co, int flags)
  {
    CommanderAbility retVal = null;
    ArrayList<CommanderAbility> abilities = co.getReadyAbilities();
    for( CommanderAbility ab : abilities )
    {
      if( flags == (ab.AIFlags & flags) )
      {
        retVal = ab;
      }
    }
    if (null != q && null != retVal)
      q.offer(new GameAction.AbilityAction(retVal));
    return retVal;
  }

  /**
   * Evaluates an attack action based on caller-provided logic
   * @param unit The attacking unit
   * @param action The action to evaluate
   * @param map The user's current game knowledge
   * @param combatScorer Evaluates combat with a unit
   * @param demolishScorer Evaluates targeting terrain
   * @return
   */
  public static double scoreAttackAction(Unit unit, GameAction action, GameMap map,
                                         Function<BattleSummary, Double> combatScorer,
                                         BiFunction<TerrainType, StrikeParams, Double> demolishScorer)
  {
    double score = 0;
    Location targetLoc = map.getLocation(action.getTargetLocation());
    Unit targetUnit = targetLoc.getResident();
    if( null != targetUnit )
    {
      BattleSummary results = CombatEngine.simulateBattleResults(unit, targetUnit, map, action.getMoveLocation());
      score = combatScorer.apply(results);
    }
    else
    {
      StrikeParams params = CombatEngine.calculateTerrainDamage(unit,
          Utils.findShortestPath(unit, action.getMoveLocation(), map), targetLoc, map);
      score = demolishScorer.apply(targetLoc.getEnvironment().terrainType, params);
    }

    return score;
  }

  /**
   * @return The area and severity of threat from the unit, against the specified target type
   */
  public static Map<XYCoord, Double> findThreatPower(GameMap gameMap, Unit unit, UnitModel target)
  {
    XYCoord origin = new XYCoord(unit.x, unit.y);
    Map<XYCoord, Double> shootableTiles = new HashMap<XYCoord, Double>();
    boolean includeOccupiedDestinations = true; // We assume the enemy knows how to manage positioning within his turn
    ArrayList<XYCoord> destinations = Utils.findPossibleDestinations(unit, gameMap, includeOccupiedDestinations);
    for( WeaponModel wep : unit.model.weapons )
    {
      double damage = (null == target)? 1 : wep.getDamage(target) * unit.getHPFactor();
      if( damage > 0 )
      {
        if( !wep.canFireAfterMoving )
        {
          for (XYCoord xyc : Utils.findLocationsInRange(gameMap, origin, wep.minRange, wep.maxRange))
          {
            double val = damage;
            if (shootableTiles.containsKey(xyc))
              val = Math.max(val, shootableTiles.get(xyc));
            shootableTiles.put(xyc, val);
          }
        }
        else
        {
          for( XYCoord dest : destinations )
          {
            for (XYCoord xyc : Utils.findLocationsInRange(gameMap, dest, wep.minRange, wep.maxRange))
            {
              double val = damage;
              if (shootableTiles.containsKey(xyc))
                val = Math.max(val, shootableTiles.get(xyc));
              shootableTiles.put(xyc, val);
            }
          }
        }
      }
    }
    return shootableTiles;
  }

  /**
   * @return The range at which the CO in question might be able to attack after moving.
   */
  public static int findMaxStrikeWeaponRange(Commander co)
  {
    int range = 0;
    for( UnitModel um : co.unitModels )
    {
      for( WeaponModel wm : um.weapons )
      {
        if( wm.canFireAfterMoving )
          range = Math.max(range, wm.maxRange);
      }
    }
    return range;
  }

  /**
   * @return Whether a friendly CO is currently in the process of acquiring the specified coordinates
   */
  public static boolean isCapturing(GameMap map, Commander co, XYCoord coord)
  {
    Unit unit = map.getLocation(coord).getResident();
    if( null == unit || co.isEnemy(unit.CO) )
      return false;
    return unit.getCaptureProgress() > 0;
  }

  /**
   * @return Whether a friendly CO can build from the specified coordinates
   */
  public static boolean isFriendlyProduction(GameMap map, Commander co, XYCoord coord)
  {
    Commander owner = map.getLocation(coord).getOwner();
    if( null == owner || co.isEnemy(owner) )
      return false;
    return owner.unitProductionByTerrain.containsKey(map.getEnvironment(coord).terrainType);
  }

  /**
   * Keeps track of a commander's production facilities. When created, it will automatically catalog
   * all available facilities, and all units that can be built. It is then easy to ask whether it is
   * possible to build a given type of unit, or find a location to do so.
   * Once a purchase has been scheduled, removeBuildLocation() will remove a given facility from any
   * further consideration.
   */
  public static class CommanderProductionInfo
  {
    public Commander myCo;
    public Set<UnitModel> availableUnitModels;
    public Set<Location> availableProperties;
    public Map<Terrain.TerrainType, Integer> propertyCounts;
    public Map<UnitModel, Set<TerrainType>> modelToTerrainMap;

    /**
     * Build a model of the production capabilities for a given Commander.
     * Could be used for your own, or your opponent's.
     */
    public CommanderProductionInfo(Commander co, GameMap gameMap, boolean includeFriendlyOccupied)
    {
      // Figure out what unit types we can purchase with our available properties.
      myCo = co;
      availableUnitModels = new HashSet<UnitModel>();
      availableProperties = new HashSet<Location>();
      propertyCounts = new HashMap<Terrain.TerrainType, Integer>();
      modelToTerrainMap = new HashMap<UnitModel, Set<TerrainType>>();

      for( XYCoord xyc : co.ownedProperties )
      {
        Location loc = co.myView.getLocation(xyc);
        Unit blocker = loc.getResident();
        if( null == blocker
            || (includeFriendlyOccupied && co == blocker.CO && !blocker.isTurnOver) )
        {
          ArrayList<UnitModel> models = co.getShoppingList(loc);
          availableUnitModels.addAll(models);
          availableProperties.add(loc);
          TerrainType terrain = loc.getEnvironment().terrainType;
          if( propertyCounts.containsKey(terrain))
          {
            propertyCounts.put(terrain, propertyCounts.get(loc.getEnvironment().terrainType)+1);
          }
          else
          {
            propertyCounts.put(terrain, 1);
          }

          // Store a mapping from UnitModel to the TerrainType that can produce it.
          for( UnitModel m : models )
          {
            if( modelToTerrainMap.get(m) == null )
              modelToTerrainMap.put(m, new HashSet<TerrainType>());
            modelToTerrainMap.get(m).add( loc.getEnvironment().terrainType );
          }
        }
      }
    }

    /**
     * Return a location that can build the given unitModel, or null if none remains.
     */
    public Location getLocationToBuild(UnitModel model)
    {
      Set<TerrainType> desiredTerrains = modelToTerrainMap.get(model);
      Location location = null;
      for( Location loc : availableProperties )
      {
        if( desiredTerrains.contains(loc.getEnvironment().terrainType) )
        {
          location = loc;
          break;
        }
      }
      return location;
    }

    /**
     * Remove the given location from further consideration, even if it is still available.
     */
    public void removeBuildLocation(Location loc)
    {
      availableProperties.remove(loc);
      TerrainType terrain = loc.getEnvironment().terrainType;
      if( propertyCounts.containsKey(terrain) )
      {
        propertyCounts.put(terrain, propertyCounts.get(terrain) - 1);
        if( propertyCounts.get(terrain) == 0 )
        {
          availableUnitModels.removeAll(myCo.getShoppingList(loc));
        }
      }
    }

    /**
     * Returns the number of facilities that can produce units of the given type.
     */
    public int getNumFacilitiesFor(UnitModel model)
    {
      int num = 0;
      if( modelToTerrainMap.containsKey(model) )
      {
        for( TerrainType terrain : modelToTerrainMap.get(model) )
        {
          num += propertyCounts.get(terrain);
        }
      }
      return num;
    }
  }

  /**
   * Compares Units based on their value, scaled with HP.
   */
  public static class UnitCostComparator implements Comparator<Unit>
  {
    boolean sortAscending;

    public UnitCostComparator(boolean sortAscending)
    {
      this.sortAscending = sortAscending;
    }

    @Override
    public int compare(Unit o1, Unit o2)
    {
      int diff = o2.model.getCost()*o2.getHP() - o1.model.getCost()*o1.getHP();
      if (sortAscending)
        diff *= -1;
      return diff;
    }
  }

  /** Return the set of locations with enemies or terrain that `unit` could attack in one turn from `start` */
  public static Set<XYCoord> findPossibleTargets(GameMap gameMap, Unit unit, XYCoord start, boolean includeTerrain)
  {
    Set<XYCoord> targetLocs = new HashSet<XYCoord>();
    boolean allowEndingOnUnits = false; // We can't attack from on top of another unit.
    ArrayList<XYCoord> moves = Utils.findPossibleDestinations(start, unit, gameMap, allowEndingOnUnits);
    for( XYCoord move : moves )
    {
      boolean moved = !move.equals(start);

      for( WeaponModel wpn : unit.model.weapons )
      {
        // Evaluate this weapon for targets if it has ammo, and if either the weapon
        // is mobile or we don't care if it's mobile (because we aren't moving).
        if( wpn.loaded(unit) && (!moved || wpn.canFireAfterMoving) )
        {
          ArrayList<XYCoord> locations = Utils.findTargetsInRange(gameMap, unit.CO, move, wpn, includeTerrain);
          targetLocs.addAll(locations);
        }
      } // ~Weapon loop
    }
    targetLocs.remove(start); // No attacking your own position.
    return targetLocs;
  }

  /**
   * Finds allied production centers in the input set
   */
  public static Set<XYCoord> findAlliedIndustries(GameMap gameMap, Commander co, Iterable<XYCoord> coords, boolean ignoreMyOwn)
  {
    Set<XYCoord> result = new HashSet<XYCoord>();
    for( XYCoord coord : coords )
    {
      Commander owner = gameMap.getLocation(coord).getOwner();
      if( co.isEnemy(owner) )
        continue; // counts as a null check on owner
      if( ignoreMyOwn && co == owner )
        continue;
      if( owner.unitProductionByTerrain.containsKey(gameMap.getEnvironment(coord).terrainType) )
        result.add(coord);
    }
    return result;
  }
}
