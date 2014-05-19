package pipe.actions.gui;

import pipe.gui.ApplicationSettings;
import pipe.io.JarUtilities;
import pipe.utilities.gui.GuiUtils;
import pipe.views.PipeApplicationView;
import uk.ac.imperial.pipe.parsers.UnparsableException;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.jar.JarEntry;
import java.util.zip.ZipEntry;

public class ExampleFileAction extends GuiAction
{
    private final File filename;
    private final PipeApplicationView applicationView;

    public ExampleFileAction(File file, PipeApplicationView applicationView)
    {
        super(file.getName(), "Open example file \"" + file.getName() + "\"");
        filename = file;
        this.applicationView = applicationView;
        putValue(SMALL_ICON, new ImageIcon(this.getClass().getResource(ApplicationSettings.getImagePath() + "Net.png")));
    }

    public ExampleFileAction(ZipEntry entry, PipeApplicationView applicationView)
    {
        super(entry.getName().substring(1 + entry.getName().indexOf(System.getProperty("file.separator"))), "Open example file \"" + entry.getName() + "\"");
        this.applicationView = applicationView;
        filename = JarUtilities.getFile(entry);
        putValue(SMALL_ICON, new ImageIcon(this.getClass().getResource(ApplicationSettings.getImagePath() + "Net.png")));
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        try {
            ApplicationSettings.getApplicationController().createNewTabFromFile(
                    filename);
        } catch (UnparsableException e1) {
            GuiUtils.displayErrorMessage(applicationView, e1.getMessage());
        }
    }

}