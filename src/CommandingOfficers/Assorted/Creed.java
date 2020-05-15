package CommandingOfficers.Assorted;

import Engine.GameScenario;
import CommandingOfficers.Commander;
import CommandingOfficers.CommanderAbility;
import CommandingOfficers.CommanderInfo;
import CommandingOfficers.Modifiers.UnitProductionModifier;
import Engine.GameEvents.GameEventQueue;
import Terrain.MapMaster;
import Terrain.TerrainType;
import Units.Unit;
import Units.UnitModel;

public class Creed extends Commander
{
  private static final long serialVersionUID = 1L;
  private static final CommanderInfo coInfo = new instantiator();
  private static class instantiator extends CommanderInfo
  {
    private static final long serialVersionUID = 1L;
    public instantiator()
    {
      super("Creed");
      infoPages.add(new InfoPage(
          "Ursarkar E. Creed\n" + 
          "  Even the Chaos Gods tremble at his reality-warping powers.\n" +
          "  Units cost -50% less to build, and can act immediately\n" + 
          "Tactical Genius (5): May deploy any unit from cities"));
    }
    @Override
    public Commander create(GameScenario.GameRules rules)
    {
      return new Creed(rules);
    }
  }

  public Creed(GameScenario.GameRules rules)
  {
    super(coInfo, rules);

    addCommanderAbility(new TacticalGenius(this));
  }

  @Override
  public GameEventQueue initTurn(MapMaster map)
  {
    for( UnitModel um : unitModels )
    {
      um.COcost = 0.5;
    }
    return super.initTurn(map);
  }

  @Override
  public void receiveCreateUnitEvent(Unit unit)
  {
    if( this == unit.CO )
    {
      unit.isTurnOver = false;
    }
  }

  public static CommanderInfo getInfo()
  {
    return coInfo;
  }

  private static class TacticalGenius extends CommanderAbility
  {
    private static final long serialVersionUID = 1L;
    private static final String NAME = "Tactical Genius";
    private static final int COST = 5;

    TacticalGenius(Commander commander)
    {
      super(commander, NAME, COST);
      AIFlags |= PHASE_BUY;
    }

    @Override
    protected void perform(MapMaster gameMap)
    {
      UnitProductionModifier upm = new UnitProductionModifier();

      for( UnitModel um : myCommander.unitModels )
      {
        upm.addProductionPair(TerrainType.CITY, um);
      }

      myCommander.addCOModifier(upm);
    }
  }
}

