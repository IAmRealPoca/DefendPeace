package Terrain.Types;

import java.awt.Color;

public class Forest extends Grass
{
  private static Forest instance;

  protected Forest()
  {
    defLevel = 3; // DoR forests are 3*
    miniColor = new Color(46, 196, 24);
    isCover = true;
    // baseIndex is base class's
  }
  
  public static BaseTerrain getInstance()
  {
    if (null == instance)
      instance = new Forest();
    return instance;
  }

}
