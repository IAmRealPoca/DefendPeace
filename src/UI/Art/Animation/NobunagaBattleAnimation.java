package UI.Art.Animation;

import java.awt.Color;
import java.awt.Graphics;

import UI.MapView;

import Engine.GameAction;

public class NobunagaBattleAnimation implements AnimationSequence
{	
	long startTime = 0;
	GameAction myAction = null;
	
	private long endTime = 600;
	
	public NobunagaBattleAnimation(GameAction action)
	{
		if(action.getActionType() != GameAction.ActionType.ATTACK)
		{
			System.out.println("ERROR! BattleAnimation given an incompatible GameAction - " + action.getActionType());
		}
		
		myAction = action;
		startTime = System.currentTimeMillis();
	}

	@Override
	public boolean animate(Graphics g)
	{
		long animTime = System.currentTimeMillis() - startTime;
		int tileSize = MapView.getTileSize();

		if(animTime > 500)
		{
			// No flashes
		}
		else if(animTime > 400)
		{
			// Flash 2 over defender
			g.setColor(Color.WHITE);
			g.fillRect(myAction.getActX()*tileSize, myAction.getActY()*tileSize, tileSize, tileSize);
		}
		else if(animTime > 300)
		{
			// Flash 4 over attacker.
			g.setColor(Color.WHITE);
			g.fillRect(myAction.getMoveX()*tileSize, myAction.getMoveY()*tileSize, tileSize, tileSize);
		}
		else if(animTime > 200)
		{
			// Flash 1 over defender
			g.setColor(Color.WHITE);
			g.fillRect(myAction.getActX()*tileSize, myAction.getActY()*tileSize, tileSize, tileSize);
		}
		else if(animTime > 100)
		{
			// Flash 3 over attacker
			g.setColor(Color.WHITE);
			g.fillRect(myAction.getMoveX()*tileSize, myAction.getMoveY()*tileSize, tileSize, tileSize);
		}
		return animTime > endTime;
	}

	public void cancel()
	{
		endTime = 0;
	}
}
