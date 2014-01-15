package pipe.petrinet.adapters.modelAdapter;

import pipe.models.component.ArcPoint;
import pipe.petrinet.adapters.model.AdaptedArcPoint;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.awt.geom.Point2D;

public class ArcPointAdapter extends XmlAdapter<AdaptedArcPoint, ArcPoint> {
    @Override
    public ArcPoint unmarshal(AdaptedArcPoint adaptedArcPoint) throws Exception {
        Point2D point = new Point2D.Double(adaptedArcPoint.getX(), adaptedArcPoint.getY());
        return new ArcPoint(point, adaptedArcPoint.isCurved());
    }

    @Override
    public AdaptedArcPoint marshal(ArcPoint arcPoint) throws Exception {
        AdaptedArcPoint adaptedArcPoint = new AdaptedArcPoint();
        adaptedArcPoint.setX(arcPoint.getX());
        adaptedArcPoint.setY(arcPoint.getY());
        adaptedArcPoint.setCurved(arcPoint.isCurved());
        return adaptedArcPoint;
    }
}
