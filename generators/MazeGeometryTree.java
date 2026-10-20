package app.freerouting.board.searchtree;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.geometry.planar.FortyfiveDegreeBoundingDirections;
import java.util.function.IntBinaryOperator;
public class MazeGeometryTree extends ShapeSearchTree {
 private final IntBinaryOperator compensation;
 public MazeGeometryTree(BasicBoard board,IntBinaryOperator compensation){super(FortyfiveDegreeBoundingDirections.INSTANCE,board,0);this.compensation=compensation;}
 @Override public int clearanceCompensationValue(int c,int l){return compensation.applyAsInt(c,l);}
}
