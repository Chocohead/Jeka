package dev.jeka.core.tool.builtins.scaffold;

import dev.jeka.core.api.depmanagement.JkDependencyResolver;
import dev.jeka.core.api.utils.JkUtilsIO;
import dev.jeka.core.tool.JkCommands;
import dev.jeka.core.tool.JkDoc;
import dev.jeka.core.tool.JkPlugin;
import dev.jeka.core.tool.builtins.repos.JkPluginRepo;

/**
 * Provides method to generate a project skeleton (folder structure, configuration files, ....)
 */
@JkDoc("Provides method to generate a project skeleton (folder structure and basic build files).")
public class JkPluginScaffold extends JkPlugin {

    private final JkScaffolder scaffolder;

    protected JkPluginScaffold(JkCommands run) {
        super(run);
        this.scaffolder = new JkScaffolder(run.getBaseDir());
        this.scaffolder.setCommandClassCode(JkUtilsIO.read(JkPluginScaffold.class.getResource("buildclass.snippet")));
    }

    public JkScaffolder getScaffolder() {
        return scaffolder;
    }

    @JkDoc("Generates project skeleton (folders and files necessary to work with the project).")
    public void run() {
        scaffolder.run();
    }

    @JkDoc("Copies Jeka executables inside the project in order to be run in embedded mode.")
    public void embed() {
        scaffolder.embed();
    }

    @JkDoc("Copies Jeka wrapper executable inside the project in order to be run in wrapper mode.")
    public void wrap() {
        final JkPluginRepo repoPlugin = this.getCommands().getPlugin(JkPluginRepo.class);
        final JkDependencyResolver dependencyResolver = JkDependencyResolver.of(repoPlugin.downloadRepository().toSet());
        scaffolder.wrap(dependencyResolver);
    }

}
