package Engine.GameEvents;

import Engine.XYCoord;
import Terrain.GameMap;
import Terrain.MapMaster;
import UI.MapView;
import UI.Art.Animation.GameAnimation;
import Units.Unit;

/**
 * Moves a unit directly to the destination without traversing intermediate steps.
 * No validation is performed on the final destination.
 */
public class TeleportEvent implements GameEvent
{
  private Unit unit;
  XYCoord unitStart;
  private XYCoord unitDestination;
  private Unit obstacle;
  private AnimationStyle animationStyle;

  public enum AnimationStyle
  {
    BLINK,
    DROP_IN
  }

  public enum CollisionOutcome
  {
    KILL,
    SWAP
  }

  public TeleportEvent(GameMap map, Unit u, XYCoord dest, AnimationStyle animStyle)
  {
    unit = u;
    unitStart = new XYCoord(unit.x, unit.y);
    if( !map.isLocationValid(unitStart) )
      unitStart = null;
    unitDestination = dest;
    animationStyle = animStyle;
  }

  public TeleportEvent(GameMap map, Unit u, XYCoord dest, CollisionOutcome crashResult)
  {
    this(map, u, dest, AnimationStyle.BLINK);
  }

  public TeleportEvent(GameMap map, Unit u, XYCoord dest)
  {
    this(map, u, dest, AnimationStyle.BLINK);
  }

  @Override
  public GameAnimation getEventAnimation(MapView mapView)
  {
    if( animationStyle == AnimationStyle.DROP_IN )
      return mapView.buildAirdropAnimation(unit, unitStart, unitDestination, obstacle);
    else if( animationStyle == AnimationStyle.BLINK )
      return mapView.buildTeleportAnimation(unit, unitStart, unitDestination, obstacle);
    return null;
  }

  @Override
  public GameEventQueue sendToListener(GameEventListener listener)
  {
    return listener.receiveTeleportEvent(unit, unitStart, unitDestination);
  }

  @Override
  public void performEvent(MapMaster gameMap)
  {
    boolean force = true;
    gameMap.moveUnit(unit, unitDestination.xCoord, unitDestination.yCoord, force);
    unit.CO.army.myView.revealFog(unit);
  }

  public Unit getUnit()
  {
    return unit;
  }

  @Override
  public XYCoord getStartPoint()
  {
    return unitStart;
  }

  @Override
  public XYCoord getEndPoint()
  {
    return unitDestination;
  }
}
