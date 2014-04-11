package pipe.gui;

import pipe.controllers.PetriNetController;
import pipe.historyActions.component.MovePetriNetObject;
import pipe.historyActions.MultipleEdit;
import pipe.models.component.Connectable;
import pipe.models.component.PetriNetComponent;
import pipe.models.component.PlaceablePetriNetComponent;
import pipe.models.component.annotation.Annotation;
import pipe.models.component.annotation.AnnotationVisitor;
import pipe.models.component.arc.Arc;
import pipe.models.component.arc.ArcPoint;
import pipe.models.component.arc.ArcPointVisitor;
import pipe.models.component.arc.ArcVisitor;
import pipe.models.component.place.Place;
import pipe.models.component.place.PlaceVisitor;
import pipe.models.component.transition.Transition;
import pipe.models.component.transition.TransitionVisitor;

import javax.swing.event.UndoableEditEvent;
import javax.swing.undo.UndoableEdit;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Handles dragging of objects around when selected
 */
public class DragManager {

    private PetriNetController petriNetController;

    private Point2D.Double dragStart = new Point2D.Double(0, 0);

    /**
     * All selected items locations at the start of a drag
     * Mapping of id -> location
     */
    Map<String, Point2D> startingCoordinates = new HashMap<>();

    public DragManager(PetriNetController petriNetController) {
        this.petriNetController = petriNetController;
    }

    public void setDragStart(Point2D.Double dragStart) {
        this.dragStart = dragStart;
    }

    /**
     * Drag items to location
     *
     * @param location location of mouse to drag items to
     */
    public void drag(Point location) {
        int x = (int) (location.getX() - dragStart.getX());
        int y = (int) (location.getY() - dragStart.getY());
        dragStart = new Point2D.Double(location.x, location.y);
        petriNetController.translateSelected(new Point(x, y));
    }

    public void saveStartingDragCoordinates() {
        startingCoordinates.clear();
        Map<PlaceablePetriNetComponent, Point2D> selectedPoints = getSelectedCoordinates();
        for (Map.Entry<PlaceablePetriNetComponent, Point2D> entry : selectedPoints.entrySet()) {
            startingCoordinates.put(entry.getKey().getId(), entry.getValue());
        }
    }

    /**
     * Method to call after finishing a drag,
     * ensures undoable edit is created
     */
    public void finishDrag() {
        Map<PlaceablePetriNetComponent, Point2D> translatedCoordinates = getSelectedCoordinates();
        createMovedUndoItem(startingCoordinates, translatedCoordinates);
    }

    /**
     * Loops through each PlaceablePetriNetComponents start and ending coordinates (i.e. before and after translation)
     * and creates a {@link pipe.historyActions.component.MovePetriNetObject} undoEdit for each event
     *
     * It then creates an {@link pipe.historyActions.MultipleEdit} with all these undoEdits in and
     * registers this with the undoListener.
     * @param startingCoordinates of selected items before translation
     * @param translatedCoordinates of selected items after translation
     */
    private void createMovedUndoItem(Map<String, Point2D> startingCoordinates,
                                     Map<PlaceablePetriNetComponent, Point2D> translatedCoordinates) {
        List<UndoableEdit> undoableEdits = new LinkedList<>();
        for (Map.Entry<PlaceablePetriNetComponent, Point2D> entry : translatedCoordinates.entrySet()) {
            PlaceablePetriNetComponent component = entry.getKey();
            Point2D starting = startingCoordinates.get(component.getId());
            Point2D translated = entry.getValue();
            if (!starting.equals(translated)) {
                undoableEdits.add(new MovePetriNetObject(component, starting, translated));
            }
        }
        //TODO: PASS IN AS A PARAM
        if (!undoableEdits.isEmpty()) {
            petriNetController.getUndoListener().undoableEditHappened(new UndoableEditEvent(this, new MultipleEdit(undoableEdits)));
        }
    }

    private Map<PlaceablePetriNetComponent, Point2D> getSelectedCoordinates() {
        CoordinateSaver saver = new CoordinateSaver();
        for (PetriNetComponent component : petriNetController.getSelectedComponents()) {
            if (component.isDraggable()) {
                component.accept(saver);
            }
        }
        return saver.savedCoordinates;
    }

    private static class CoordinateSaver
            implements ArcVisitor, ArcPointVisitor, PlaceVisitor, TransitionVisitor, AnnotationVisitor {

        Map<PlaceablePetriNetComponent, Point2D> savedCoordinates = new HashMap<>();

        @Override
        public void visit(Annotation annotation) {
            savedCoordinates.put(annotation, new Point2D.Double(annotation.getX(), annotation.getY()));
        }

        @Override
        public void visit(ArcPoint arcPoint) {
            savedCoordinates.put(arcPoint, arcPoint.getPoint());
        }

        @Override
        public <T extends Connectable, S extends Connectable> void visit(Arc<S, T> arc) {
            //TODO: Arc arc points covered by the above?
        }

        @Override
        public void visit(Place place) {
            savedCoordinates.put(place, new Point2D.Double(place.getX(), place.getY()));
        }

        @Override
        public void visit(Transition transition) {
            savedCoordinates.put(transition, new Point2D.Double(transition.getX(), transition.getY()));

        }
    }
}
