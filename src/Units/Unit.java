package Units;

import java.util.ArrayList;
import java.util.Vector;

import Terrain.GameMap;
import Terrain.Location;
import CommandingOfficers.Commander;
import Engine.CombatParameters;
import Engine.MapController;
import Engine.Utils;
import Units.UnitModel.UnitEnum;
import Units.Weapons.Weapon;

public class Unit {
	public Vector<Unit> heldUnits;
	public UnitModel model;
	public int x, y, movesLeft, fuel;
	private int captureProgress;
	private Location captureTarget;
	public Commander CO;
	public boolean isTurnOver;
	public double HP;
	public Weapon[] guns;

	public Unit(Commander co, UnitModel um)
	{
		System.out.println("Creating a " + um.type);
		CO = co;
		model = um;
		fuel = model.maxFuel;
		isTurnOver = true;
		HP = model.maxHP;
		captureProgress = 0;
		captureTarget = null;
		if (model.holdingCapacity > 0)
			heldUnits = new Vector<Unit>(model.holdingCapacity);
	}
	
	public void initTurn()
	{
		isTurnOver = false;
		fuel -= model.idleFuelBurn;
		movesLeft = model.movePower;
		if (captureTarget != null && captureTarget.getResident() != this)
		{
			captureTarget = null;
			captureProgress = 0;
		}
	}
	
	/**
	 * @return the weapon of choice
	 */
	public Weapon getWeapon(Unit target) {
		Weapon chosen = null;
		for (int i = 0; i < guns.length && chosen == null; i++)
		{
			if (guns[i].getDamage(x, y, target) != 0)
			{
				chosen = guns[i];
			}
		}
		return chosen;
	}

	/**
	 * @return how much base damage the target would take if this unit tried to attack it
	 */
	public double getDamage(Unit target) {
		Weapon chosen = null;
		for (int i = 0; i < guns.length && chosen == null; i++)
		{
			if (guns[i].getDamage(x, y, target) != 0)
			{
				return guns[i].getDamage(x, y, target);
			}
		}
		return 0;
	}
	
	// for the purpose of letting the unit know it has attacked.
	public void fire(final CombatParameters params)
	{
		UnitEnum target = params.defender.model.type;
		int i = 0;
		for (; i < guns.length; i++)
		{
			if (guns[i].getDamage(target) != 0)
			{
				break;
			}
		}
		if (i == guns.length)
			System.out.println("In "+model.name+"'s fire(): no valid weapon found");
		guns[i].fire();
	}
	
	public void capture(Location target)
	{
		if (!target.isCaptureable())
			return;
		
		if (target != captureTarget)
		{
			captureTarget = target;
			captureProgress = 0;
		}
		captureProgress += HP;
		if (captureProgress >= 200)
		{
			target.setOwner(CO);
			captureProgress = 0;
		}
	}

	// Removed for the forseeable future; may be back
/*	public static double getAttackPower(final CombatParameters params) {
//		double output = model
//		return [B*ACO/100+R]*(AHP/10)*[(200-(DCO+DTR*DHP))/100] ;
		return 0;
	}
	public static double getDefensePower(Unit unit, boolean isCounter) {
		return 0;
	}*/

	public MapController.GameAction[] getPossibleActions(GameMap map)
	{
		ArrayList<MapController.GameAction> actions = new ArrayList<MapController.GameAction>();
		if (map.getLocation(x, y).getResident() == null)
		{
			for (int i = 0; i < model.possibleActions.length; i++) {
				switch (model.possibleActions[i])
				{
				case ATTACK:
					// highlight the tiles in range, and check them for targets
					Utils.findActionableLocations(this, MapController.GameAction.ATTACK, map);
					for (int w = 0; w < map.mapWidth; w++)
					{
						for (int h = 0; h < map.mapHeight; h++)
						{
							Unit target = map.getLocation(w, h).getResident();
//							if (map.getLocation(w, h).isHighlightSet() &&
//									target != null &&
//									target.CO != this.CO &&
//									getDamage(target) > 0)
							if (map.getLocation(w, h).isHighlightSet())
							{
								actions.add(MapController.GameAction.ATTACK);
								break; // just need one target
							}
						}
					}
					map.clearAllHighlights();
					break;
				case CAPTURE:
					if(map.getLocation(x, y).getOwner() != CO && map.getLocation(x, y).isCaptureable())
					{
						actions.add(MapController.GameAction.CAPTURE);
					}
					break;
				case WAIT:
					actions.add(MapController.GameAction.WAIT);
					break;
				case LOAD:
					// Handled in MapController
					break;
				case UNLOAD:
					if (heldUnits.size() > 0) {
						actions.add(MapController.GameAction.UNLOAD);
					}
					break;
				default:
					System.out.println("getPossibleActions: Invalid action in model's possibleActions["+i+"]: " + model.possibleActions[i]);
				}
			}
		} else
		{
			actions.add(MapController.GameAction.LOAD);
		}
		MapController.GameAction[] returned = new MapController.GameAction[0];
		return actions.toArray(returned);
	}
}
