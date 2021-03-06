package dev.jeka.core.tool.builtins.repos;

import dev.jeka.core.api.depmanagement.JkRepo;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsString;
import dev.jeka.core.tool.JkCommands;
import dev.jeka.core.tool.JkDoc;
import dev.jeka.core.tool.JkPlugin;
import dev.jeka.core.tool.JkRepoConfigOptionLoader;

import java.util.Map;

/**
 * Plugin for defining repositories.
 */
@JkDoc("Provides configured repositories to download or upload artifacts.\n" +
        "  To select a 'download' (respectively a 'publish') repository, this plugin check in order : \n" +
        "    - the 'repos#downloadRepoName' option which designate the name of the configured plugin\n" +
        "    - the 'repos#downloadUrl', 'repos#downloadUsername' and 'repos#downloadPassword' options to instantiate a repo based on these values\n" +
        "    - the 'Repos.download.url', 'Repos.download.username' and 'Repo.download.password' global options to instantiate repo based on these values\n" +
        "    - the default repo returned by JkRepo#ofMavenCentral() for downloading and local repository for publishing.\n" +
        "  To configure a named repository, add following properties into your [Jeka_user_home]/options.properties file :\n" +
        "    'Repos.[name].url', 'Repos.[value].username' and 'Repos.[of].password'"
)
public class JkPluginRepo extends JkPlugin {

    // ------------------------------ options -------------------------------------------

    @JkDoc("Url for the publish repository.")
    public String publishUrl;

    @JkDoc("Url for the download repository.")
    public String downloadUrl;

    @JkDoc("Username credential for the publish repository (if needed).")
    public String publishUsername;

    @JkDoc("Password for the publish repository (if needed).")
    public String publishPassword;

    @JkDoc("Username credential for the download repository (if needed).")
    public String downloadUsername;

    @JkDoc("Password for the download repository (if needed).")
    public String downloadPassword;

    @JkDoc("Name of the configured repository to use for publishing artifacts. Null or empty means not set.")
    public String publishRepoName;

    @JkDoc("Name of the configured repository to use for publishing artifacts. Null or empty means not set.")
    public String downloadRepoName;

    // ----------------------------------------------------------------------------------

    protected JkPluginRepo(JkCommands run) {
        super(run);
    }

    public JkRepo publishRepository() {
        if (!JkUtilsString.isBlank(publishRepoName)) {
            return JkRepoConfigOptionLoader.repoFromOptions(publishRepoName);
        }
        if (!JkUtilsString.isBlank(publishUrl)) {
            return JkRepo.of(publishUrl).withOptionalCredentials(publishUsername, publishPassword);
        }
        JkRepo optionRepo = JkRepoConfigOptionLoader.publishRepository();
        return optionRepo != null ? optionRepo : JkRepo.ofLocal();
    }

    public JkRepo downloadRepository() {
        if (!JkUtilsString.isBlank(downloadPassword)) {
            return JkRepoConfigOptionLoader.repoFromOptions(downloadRepoName);
        }
        if (!JkUtilsString.isBlank(downloadUrl)) {
            return JkRepo.of(downloadUrl).withOptionalCredentials(downloadUsername, downloadPassword);
        }
        return JkRepoConfigOptionLoader.downloadRepository();
    }

    @JkDoc("Displays active and configured repositories.")
    public void info() {
        StringBuilder sb = new StringBuilder();
        sb.append("Download repository : \n");
        JkRepo download = downloadRepository();
        sb.append("\n  url : " + download.getUrl());
        if (download.getCredential() != null) {
            String downloadPwd = JkUtilsString.isBlank(download.getCredential().getPassword()) ? ""
                    : downloadPassword.substring(0, 1) + "*******";
            sb.append("\n  username : " + download.getCredential().getUserName())
                    .append("\n  password : " + downloadPwd);
        }
        sb.append("\n").append("Publish repository : \n");
        JkRepo publish = publishRepository();
        sb.append("\n  url : " + publish.getUrl());
        if (publish.getCredential() != null) {
            String publishPwd = JkUtilsString.isBlank(publish.getCredential().getPassword()) ? ""
                    : publishPassword.substring(0, 1) + "*******";
            sb.append("\n  username : " + publish.getCredential().getUserName()).append("\n  password : " + publishPwd);
        }
        for (Map.Entry<String, String> entry : JkRepoConfigOptionLoader.allRepositoryOptions().entrySet()) {
            sb.append("\n" + entry.getKey() + " : ").append(entry.getValue());
        }

        JkLog.info(sb.toString());
    }

}
