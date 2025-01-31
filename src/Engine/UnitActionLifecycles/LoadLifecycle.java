package Engine.UnitActionLifecycles;

import Engine.GameAction;
import Engine.GameActionSet;
import Engine.GamePath;
import Engine.UnitActionFactory;
import Engine.Utils;
import Engine.XYCoord;
import Engine.GameEvents.GameEvent;
import Engine.GameEvents.GameEventListener;
import Engine.GameEvents.GameEventQueue;
import Terrain.GameMap;
import Terrain.MapMaster;
import UI.MapView;
import UI.Art.Animation.GameAnimation;
import Units.Unit;

public abstract class LoadLifecycle
{
  public static class LoadFactory extends UnitActionFactory
  {
    private static final long serialVersionUID = 1L;

    @Override
    public GameActionSet getPossibleActions(GameMap map, GamePath movePath, Unit actor, boolean ignoreResident)
    {
      XYCoord moveLocation = movePath.getEndCoord();
      Unit resident = map.getLocation(moveLocation).getResident();
      if( resident != null && resident != actor )
      {
        if( resident.hasCargoSpace(actor.model.role) )
        {
          return new GameActionSet(new LoadAction(map, actor, movePath), false);
        }
      }
      return null;
    }

    @Override
    public String name(Unit actor)
    {
      return "LOAD";
    }

    /**
     * From Serializable interface
     * @return The statically-defined object to use for this action type.
     */
    private Object readResolve()
    {
      return LOAD;
    }
  }

  public static class LoadAction extends GameAction
  {
    private Unit passenger;
    GamePath movePath;
    private XYCoord pathEnd = null;
    private Unit transport;

    public LoadAction(GameMap gameMap, Unit actor, GamePath path)
    {
      passenger = actor;
      movePath = path;
      if( (null != movePath) && (movePath.getPathLength() > 0) )
      {
        pathEnd = movePath.getEndCoord();
        if( (null != gameMap) && gameMap.isLocationValid(pathEnd) )
        {
          transport = gameMap.getLocation(pathEnd).getResident();
        }
      }
    }

    @Override
    public GameEventQueue getEvents(MapMaster gameMap)
    {
      // LOAD actions consist of
      //   MOVE
      //   LOAD
      GameEventQueue loadEvents = new GameEventQueue();

      // Validate input
      boolean isValid = true;
      isValid &= (null != passenger) && !passenger.isTurnOver;
      isValid &= (null != movePath) && (movePath.getPathLength() > 0);
      isValid &= (null != gameMap);
      if( isValid )
      {
        pathEnd = movePath.getEndCoord();
        isValid &= gameMap.isLocationValid(pathEnd);

        if( isValid )
        {
          // Find the transport unit.
          transport = gameMap.getLocation(pathEnd).getResident();
          isValid &= (null != transport) && transport.hasCargoSpace(passenger.model.role);
        }
      }

      // Create events.
      if( isValid )
      {
        // Move to the transport, if we don't get blocked.
        if( Utils.enqueueMoveEvent(gameMap, passenger, movePath, loadEvents) )
        {
          // Get in the transport.
          loadEvents.add(new LoadEvent(passenger, transport));
        }
      }
      return loadEvents;
    }

    @Override
    public Unit getActor()
    {
      return passenger;
    }

    @Override
    public XYCoord getMoveLocation()
    {
      return pathEnd;
    }

    @Override
    public XYCoord getTargetLocation()
    {
      return pathEnd;
    }

    @Override
    public String toString()
    {
      return String.format("[Load %s into %s]", passenger.toStringWithLocation(), transport.toStringWithLocation());
    }

    @Override
    public UnitActionFactory getType()
    {
      return UnitActionFactory.LOAD;
    }
  } // ~LoadAction

  public static class LoadEvent implements GameEvent
  {
    private Unit unitCargo = null;
    private Unit unitTransport = null;

    public LoadEvent(Unit cargo, Unit transport)
    {
      unitCargo = cargo;
      unitTransport = transport;
    }

    @Override
    public GameAnimation getEventAnimation(MapView mapView)
    {
      return mapView.buildLoadAnimation();
    }

    @Override
    public GameEventQueue sendToListener(GameEventListener listener)
    {
      return listener.receiveLoadEvent(this);
    }

    @Override
    public void performEvent(MapMaster gameMap)
    {
      if( null != unitTransport && unitTransport.hasCargoSpace(unitCargo.model.role) )
      {
        gameMap.removeUnit(unitCargo);
        unitCargo.x = -1;
        unitCargo.y = -1;
        unitTransport.heldUnits.remove(unitCargo); // Make sure we can't double-load
        unitTransport.heldUnits.add(unitCargo);
      }
      else
      {
        System.out.println("WARNING! Cannot load " + unitCargo.model.name + " onto " + unitTransport.model.name);
      }
    }

    @Override
    public XYCoord getStartPoint()
    {
      return new XYCoord(unitCargo.x, unitCargo.y);
    }

    @Override
    public XYCoord getEndPoint()
    {
      return new XYCoord(unitTransport.x, unitTransport.y);
    }
  } // ~Event

}
