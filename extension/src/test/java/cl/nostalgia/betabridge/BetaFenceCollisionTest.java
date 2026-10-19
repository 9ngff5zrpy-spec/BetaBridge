package cl.nostalgia.betabridge;
import org.geysermc.geyser.level.physics.*;
public final class BetaFenceCollisionTest {
    public static void main(String[] args){
        var shape=BetaFenceCollisions.shape();var boxes=shape.getBoundingBoxes();
        if(boxes.length!=1)throw new AssertionError("one original box");
        var box=boxes[0];
        if(box.getMiddleX()!=.5||box.getMiddleY()!=.75||box.getMiddleZ()!=.5||box.getSizeX()!=1||box.getSizeY()!=1.5||box.getSizeZ()!=1)
            throw new AssertionError("original gk.d bounds");
        var cornerApproach=new BoundingBox(1.5,.9,.1,.6,1.8,.6);
        double allowed=shape.computeCollisionOffset(0,0,0,cornerApproach,Axis.X,-.8);
        if(Math.abs(allowed+.2)>1e-6)throw new AssertionError("player stopped at full Beta fence edge: "+allowed);
        if(shape.checkIntersection(0,0,0,new BoundingBox(.1,2.4,.1,.1,1.7,.1)))throw new AssertionError("clear above 1.5");
        System.out.println("PASS: original full 1x1.5x1 fence collision, corner approach and clearance above fence.");
    }
}
