package CommandingOfficers;

import CommandingOfficers.Modifiers.COModifier;
import Engine.GameEvents.GameEvent;
import Engine.GameEvents.GameEventListener;
import Engine.GameEvents.GlobalWeatherEvent;
import Engine.GameEvents.MapChangeEvent;
import Terrain.Environment.Weathers;
import Terrain.MapMaster;
import Terrain.Location;
import Terrain.TerrainType;
import Units.Unit;
import Units.UnitModel;

public class BMOlaf extends Commander
{
  private static final CommanderInfo coInfo = new instantiator();
  private static class instantiator extends CommanderInfo
  {
    public instantiator()
    {
      super("Olaf");
      infoPages.add(new InfoPage(
          "Olaf\r\n" + 
          "  Unaffected by snow, but rain affects him as much as snow would for others\r\n" + 
          "Blizzard -- Changes the weather to Snow (1 day)\r\n" + 
          "Winter Fury -- Enemy units lose 2 HP (to a minimum of 1); changes the weather to Snow (1 day)"));
    }
    @Override
    public Commander create()
    {
      return new BMOlaf();
    }
  }

  public BMOlaf()
  {
    super(coInfo);

    for( UnitModel um : unitModels.values() )
    {
      for( TerrainType terrain : TerrainType.TerrainTypeList )
      {
        um.propulsion.setMoveCost(Weathers.RAIN, terrain, um.propulsion.getMoveCost(Weathers.SNOW, terrain));
        um.propulsion.setMoveCost(Weathers.SNOW, terrain, um.propulsion.getMoveCost(Weathers.CLEAR, terrain));
      }
    }

    addCommanderAbility(new Blizzard(this));
    addCommanderAbility(new WinterFury(this));
  }

  public static CommanderInfo getInfo()
  {
    return coInfo;
  }

  private static class Blizzard extends CommanderAbility
  {
    private static final String NAME = "Blizzard";
    private static final int COST = 3;

    Blizzard(Commander commander)
    {
      super(commander, NAME, COST);
    }

    @Override
    protected void perform(MapMaster gameMap)
    {
      GameEvent event = new GlobalWeatherEvent(Weathers.SNOW, 1);
      event.performEvent(gameMap);
      GameEventListener.publishEvent(event);
    }
  }

  private static class WinterFury extends CommanderAbility
  {
    private static final String NAME = "Winter Fury";
    private static final int COST = 7;
    
    WinterFury(Commander commander)
    {
      // as we start in Bear form, UpTurn is the correct starting name
      super(commander, NAME, COST);
    }

    @Override
    protected void perform(MapMaster gameMap)
    {
      for( int i = 0; i < gameMap.mapWidth; i++ )
      {
        for( int j = 0; j < gameMap.mapHeight; j++ )
        {
          Location loc = gameMap.getLocation(i, j);
          Unit victim = loc.getResident();
          if( victim != null && myCommander.isEnemy(victim.CO) )
          {
            victim.alterHP(-2);
          }
        }
      }
      GameEvent event = new GlobalWeatherEvent(Weathers.SNOW, 1);
      event.performEvent(gameMap);
      GameEventListener.publishEvent(event);
    }
  }
}

