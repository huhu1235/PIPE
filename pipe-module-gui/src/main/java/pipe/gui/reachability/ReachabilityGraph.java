package pipe.gui.reachability;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxStylesheet;
import pipe.reachability.algorithm.CachingExplorerUtilities;
import pipe.reachability.algorithm.ExplorerUtilities;
import pipe.reachability.algorithm.TimelessTrapException;
import pipe.reachability.algorithm.VanishingExplorer;
import pipe.reachability.algorithm.sequential.SequentialStateSpaceExplorer;
import pipe.reachability.algorithm.state.OnTheFlyVanishingExplorer;
import pipe.reachability.algorithm.state.SimpleVanishingExplorer;
import pipe.reachability.algorithm.state.StateSpaceExplorer;
import uk.ac.imperial.io.*;
import uk.ac.imperial.pipe.io.PetriNetIOImpl;
import uk.ac.imperial.pipe.io.PetriNetReader;
import uk.ac.imperial.pipe.models.petrinet.PetriNet;
import uk.ac.imperial.pipe.parsers.UnparsableException;
import uk.ac.imperial.state.ClassifiedState;
import uk.ac.imperial.state.Record;

import javax.swing.*;
import javax.xml.bind.JAXBException;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class ReachabilityGraph {

    /**
     * For loading Petri nets to explore
     */
    private final FileDialog fileDialog;

    /**
     * Graphical representation of reachability
     */
    private final TooltipMXGraph graph = new TooltipMXGraph();

    private JPanel panel1;

    private JButton goButton;

    private JCheckBox useExistingPetriNetCheckBox;

    private JTextField petriNetNameLabel;

    private JButton loadButton;

    private JPanel resultsPanel;

    private JCheckBox includeVanishingStatesCheckBox;

    /**
     * Last loaded Petri net via the load dialog
     */
    private PetriNet lastLoadedPetriNet;

    public ReachabilityGraph(FileDialog fileDialog) {
        mxGraphComponent graphComponent = new mxGraphComponent(graph);
        graphComponent.setPreferredSize(new Dimension(500, 500));
        graphComponent.setToolTips(true);
        graphComponent.setDragEnabled(false);

        setupGraph();

        resultsPanel.add(graphComponent);

        goButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                calculateResults();
            }
        });
        this.fileDialog = fileDialog;
        loadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadPetriNet();
            }
        });
        useExistingPetriNetCheckBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                int state = e.getStateChange();
                if (state == ItemEvent.DESELECTED) {
                    loadButton.setEnabled(true);
                } else if (state == ItemEvent.SELECTED) {
                    loadButton.setEnabled(false);
                }
            }
        });
    }

    /**
     * Sets up the graph so that components are not editable
     */
    private void setupGraph() {
        graph.setCellsLocked(true);
        graph.setEdgeLabelsMovable(false);
    }

    /**
     * Calculates the steady state exploration of a Petri net and stores its results
     * in a temporary file.
     * <p/>
     * These results are then read in and turned into a graphical representation using mxGraph
     * which is displayed to the user
     */
    private void calculateResults() {
        try {
            KryoStateIO stateWriter = new KryoStateIO();
            Path temporaryTransitions = Files.createTempFile("transitions", ".tmp");
            Path temporaryStates = Files.createTempFile("states", ".tmp");
            try (OutputStream transitionStream = Files.newOutputStream(temporaryTransitions);
                 OutputStream stateStream = Files.newOutputStream(temporaryStates)) {
                try (Output transitionOutput = new Output(transitionStream);
                     Output stateOutput = new Output(stateStream)) {
                    writeStateSpace(stateWriter, transitionOutput, stateOutput);
                }

                try (InputStream inputStream = Files.newInputStream(temporaryTransitions);
                     InputStream stateInputStream = Files.newInputStream(temporaryStates);
                     Input transitionInput = new Input(inputStream);
                     Input stateInput = new Input(stateInputStream)) {
                    Collection<Record> records = readResults(stateWriter, transitionInput);
                    Map<Integer, ClassifiedState> stateMap = readMappings(stateWriter, stateInput);
                    updateGraph(records, stateMap);
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        } catch (TimelessTrapException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Opens the file dialog and saves the selected Petri net into lastLoadedPetriNet
     * for use when calculating the state space exploration
     */
    private void loadPetriNet() {
        fileDialog.setVisible(true);

        File[] files = fileDialog.getFiles();
        if (files.length > 0) {
            File path = files[0];

            try {
                PetriNetReader petriNetIO = new PetriNetIOImpl();
                lastLoadedPetriNet = petriNetIO.read(path.getAbsolutePath());
                petriNetNameLabel.setText(path.getName());
            } catch (JAXBException e) {
                e.printStackTrace();
            } catch (UnparsableException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Writes the petriNet state space out to a temporary file which is referenced by the objectOutputStream
     *
     * @param stateWriter      format in which to write the results to
     * @param transitionOutput stream to write state space to
     * @param stateOutput      stream to write state integer mappings to
     * @throws TimelessTrapException if the state space cannot be generated due to cyclic vanishing states
     */
    private void writeStateSpace(StateWriter stateWriter, Output transitionOutput, Output stateOutput)
            throws TimelessTrapException, ExecutionException, InterruptedException, IOException {
        PetriNet petriNet = (useExistingPetriNetCheckBox.isSelected() ? null : lastLoadedPetriNet);
        StateProcessor processor = new StateIOProcessor(stateWriter, transitionOutput, stateOutput);
        ExplorerUtilities explorerUtilites = new CachingExplorerUtilities(petriNet);
        VanishingExplorer vanishingExplorer = getVanishingExplorer(explorerUtilites);
        StateSpaceExplorer stateSpaceExplorer =
                new SequentialStateSpaceExplorer(explorerUtilites, vanishingExplorer, processor);
        stateSpaceExplorer.generate(explorerUtilites.getCurrentState());
    }

    /**
     * Reads results of steady state exploration into a collection of records
     *
     * @param stateReader
     * @param input
     * @return state transitions with rates
     * @throws IOException
     */
    private Collection<Record> readResults(StateReader stateReader, Input input) throws IOException {
        MultiStateReader reader = new EntireStateReader(stateReader);
        return reader.readRecords(input);
    }


    /**
     * Reads results of the mapping of an integer state representation to
     * the Classified State it represents
     *
     * @param stateReader
     * @param input
     * @return state mappings
     * @throws IOException
     */
    private Map<Integer, ClassifiedState> readMappings(StateReader stateReader, Input input) throws IOException {
        MultiStateReader reader = new EntireStateReader(stateReader);
        return reader.readStates(input);
    }

    /**
     * Updates the mxGraph to display the records
     *
     * @param records  state transitions from a processed Petri net
     * @param stateMap
     */
    private void updateGraph(Iterable<Record> records, Map<Integer, ClassifiedState> stateMap) {

        removeCurrentContent();

        Object parent = graph.getDefaultParent();
        graph.getModel().beginUpdate();
        mxStylesheet stylesheet = graph.getStylesheet();
        Map<String, Object> vertexStyles = stylesheet.getDefaultVertexStyle();
        vertexStyles.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_ELLIPSE);

        mxHierarchicalLayout layout = new mxHierarchicalLayout(graph);
        layout.setInterHierarchySpacing(20);
        layout.setInterRankCellSpacing(50);
        Map<Integer, Object> verticies = new HashMap<>();
        try {
            graph.clearSelection();
            for (Record record : records) {
                Object state = getInsertedState(verticies, record.state, stateMap.get(record.state), graph);
                for (Map.Entry<Integer, Double> entry : record.successors.entrySet()) {
                    Integer successorId = entry.getKey();
                    ClassifiedState successorState = stateMap.get(successorId);
                    Object successor = getInsertedState(verticies, successorId, successorState, graph);
                    addEdge(parent, state, successor, entry.getValue());
                }
            }
        } finally {
            graph.getModel().endUpdate();
        }

        layout.execute(graph.getDefaultParent());

    }

    /**
     * Vanishing explorer is either a {@link pipe.reachability.algorithm.state.SimpleVanishingExplorer} if
     * vanishing states are to be included in the graph, else it is {@link pipe.reachability.algorithm.state.OnTheFlyVanishingExplorer}
     *
     * @param explorerUtilities
     */
    private VanishingExplorer getVanishingExplorer(ExplorerUtilities explorerUtilities) {
        if (includeVanishingStatesCheckBox.isSelected()) {
            return new SimpleVanishingExplorer();
        }
        return new OnTheFlyVanishingExplorer(explorerUtilities);
    }

    private void removeCurrentContent() {
        graph.removeCells(graph.getChildVertices(graph.getDefaultParent()));
    }

    /**
     * Creates/retreives a graphical representation of the state. If it does not exist
     * on the canvas already it is created and added to the verticies map
     *
     * @param verticies verticies map representing states already existing on the graph to their
     *                  graphical representation
     * @param stateId   state id that is represented in state space
     * @param state     state that stateid maps to in state space
     * @return graphical vertex representation for the State
     */
    private Object getInsertedState(Map<Integer, Object> verticies, Integer stateId, ClassifiedState state,
                                    TooltipMXGraph graph) {
        if (verticies.containsKey(stateId)) {
            return verticies.get(stateId);
        }
        Object parent = graph.getDefaultParent();
        Object vertexState = graph.insertVertex(parent, null, verticies.size(), 0, 0, 30, 30, getColor(state));
        graph.setTooltipText(vertexState, stateId.toString());
        verticies.put(stateId, vertexState);
        return vertexState;
    }

    private Object addEdge(Object parent, Object state, Object successor, double rate) {

        return graph.insertEdge(parent, null, String.format("%.2f", rate), state, successor);
    }

    /**
     * Return settings string for inserting a vertex into a graph.
     * <p/>
     * Tangible states are blue whilst vanishing states are red.
     *
     * @param state
     * @return settings string
     */
    private String getColor(ClassifiedState state) {
        if (state.isTangible()) {
            return "fillColor=#99CCFF";
        }
        return "fillColor=#FF8566";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("ReachabilityGraph");
        FileDialog selector = new FileDialog(frame, "Select petri net", FileDialog.LOAD);
        frame.setContentPane(new ReachabilityGraph(selector).panel1);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

    }

    private void createUIComponents() {
    }
}