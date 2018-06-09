package Terrain.Types;

import java.awt.Color;

public class Mountain extends Grass
{
  private static Mountain instance;

  protected Mountain()
  {
    defLevel = 4;
    miniColor = new Color(153, 99, 67);
    // baseIndex is base class's
  }
  
  public static BaseTerrain getInstance()
  {
    if (null == instance)
      instance = new Mountain();
    return instance;
  }

}
