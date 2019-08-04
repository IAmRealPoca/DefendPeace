package CommandingOfficers.IDS;

import Engine.GameScenario;
import Terrain.Environment.Weathers;
import Terrain.MapMaster;
import CommandingOfficers.Commander;
import CommandingOfficers.CommanderAbility;
import CommandingOfficers.CommanderInfo;
import CommandingOfficers.CommanderInfo.InfoPage;
import Engine.GameEvents.GameEvent;
import Engine.GameEvents.GameEventListener;
import Engine.GameEvents.GlobalWeatherEvent;
import Engine.GameEvents.MapChangeEvent;
import Terrain.Location;
import Terrain.TerrainType;
import Units.UnitModel;

public class PennyCS extends Commander
{
  private static final CommanderInfo coInfo = new instantiator();
  private static class instantiator extends CommanderInfo
  {
    public instantiator()
    {
      super("Penny");
      infoPages.add(new InfoPage(
          "--PENNY--\r\n" + 
          "Units are unaffected by weather.\r\n" + 
          "xxxXXXXX\r\n" + 
          "STORMFRONT: Changes the weather to Rain for one day.\r\n" + 
          "ENIGMA: Changes the weather to Snow for three days."));
    }
    @Override
    public Commander create(GameScenario.GameRules rules)
    {
      return new PennyCS(rules);
    }
  }

  public PennyCS(GameScenario.GameRules rules)
  {
    super(coInfo, rules);

    for( UnitModel um : unitModels.values() )
    {
      for( TerrainType terrain : TerrainType.TerrainTypeList )
      {
        um.propulsion.setMoveCost(Weathers.RAIN, terrain, um.propulsion.getMoveCost(Weathers.CLEAR, terrain));
        um.propulsion.setMoveCost(Weathers.SNOW, terrain, um.propulsion.getMoveCost(Weathers.CLEAR, terrain));
        um.propulsion.setMoveCost(Weathers.SANDSTORM, terrain, um.propulsion.getMoveCost(Weathers.CLEAR, terrain));
      }
    }

    addCommanderAbility(new Stormfront(this));
    addCommanderAbility(new Enigma(this));
  }

  public static CommanderInfo getInfo()
  {
    return coInfo;
  }

  private static class Stormfront extends CommanderAbility
  {
    private static final String NAME = "Stormfront";
    private static final int COST = 3;

    Stormfront(Commander commander)
    {
      super(commander, NAME, COST);
    }

    @Override
    protected void perform(MapMaster gameMap)
    {
      GameEvent event = new GlobalWeatherEvent(Weathers.RAIN, 1);
      event.performEvent(gameMap);
      GameEventListener.publishEvent(event);
    }
  }

  private static class Enigma extends CommanderAbility
  {
    private static final String NAME = "Enigma";
    private static final int COST = 8;

    Enigma(Commander commander)
    {
      // as we start in Bear form, UpTurn is the correct starting name
      super(commander, NAME, COST);
    }

    @Override
    protected void perform(MapMaster gameMap)
    {
      GameEvent event = new GlobalWeatherEvent(Weathers.SNOW, 3);
      event.performEvent(gameMap);
      GameEventListener.publishEvent(event);
    }
  }
}

