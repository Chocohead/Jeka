package dev.jeka.core.api.depmanagement;

import dev.jeka.core.api.system.JkException;
import dev.jeka.core.api.utils.JkUtilsString;

import java.util.Comparator;

/**
 * Identifier for project. The identifier will be used to name the generated
 * artifacts and as a getModuleId for Maven or Ivy.
 *
 * @author Jerome Angibaud
 */
public final class JkModuleId {

    /**
     * Comparator sorting by module group first then module name.
     */
    public final static Comparator<JkModuleId> GROUP_NAME_COMPARATOR = new GroupAndNameComparator();

    /**
     * Creates a project id according the specified group and name.
     */
    public static JkModuleId of(String group, String name) {
        JkException.throwIf(JkUtilsString.isBlank(group), "Module group can't be empty");
        JkException.throwIf(JkUtilsString.isBlank(name), "Module name can't be empty");
        return new JkModuleId(group, name);
    }

    /**
     * Creates a project id according a string supposed to be formatted as
     * <code>group</code>.<code>name</code> or <code>group</code>:
     * <code>name</code>. The last '.' is considered as the separator between
     * the group and the name. <br/>
     * If there is no '.' then the whole string will serve both for group and
     * name.
     */
    public static JkModuleId of(String groupAndName) {
        if (groupAndName.contains(":")) {
            final String group = JkUtilsString.substringBeforeLast(groupAndName, ":").trim();
            final String name = JkUtilsString.substringAfterLast(groupAndName, ":").trim();
            return JkModuleId.of(group, name);
        }
        if (groupAndName.contains(".")) {
            final String group = JkUtilsString.substringBeforeLast(groupAndName, ".").trim();
            final String name = JkUtilsString.substringAfterLast(groupAndName, ".").trim();
            return JkModuleId.of(group, name);
        }
        return JkModuleId.of(groupAndName, groupAndName);
    }

    private final String group;

    private final String name;

    private JkModuleId(String group, String name) {
        super();
        this.group = group;
        this.name = name;
    }

    /**
     * Group of this module.
     */
    public String getGroup() {
        return group;
    }

    /**
     * Name of this module.
     */
    public String getName() {
        return name;
    }

    /**
     * A concatenation of the group and name of the module as '[group].[name]'.
     */
    public String getDotedName() {
        if (group.equals(name)) {
            return name;
        }
        return group + "." + name;
    }

    /**
     * A concatenation of the group and name of this module as '[group]:[value]'.
     */
    public String getGroupAndName() {
        return group + ":" + name;
    }

    /**
     * Creates a {@link JkVersionedModule} from this module and the specified
     * version.
     */
    public JkVersionedModule withVersion(String version) {
        return withVersion(JkVersion.of(version));
    }

    /**
     * Creates a {@link JkVersionedModule} from this module and the specified
     * version.
     */
    public JkVersionedModule withVersion(JkVersion version) {
        return JkVersionedModule.of(this, version);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((group == null) ? 0 : group.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final JkModuleId other = (JkModuleId) obj;
        if (group == null) {
            if (other.group != null) {
                return false;
            }
        } else if (!group.equals(other.group)) {
            return false;
        }
        if (name == null) {
            return other.name == null;
        } else return name.equals(other.name);
    }

    @Override
    public String toString() {
        return this.group + ":" + this.name;
    }

    private static class GroupAndNameComparator implements Comparator<JkModuleId> {

        @Override
        public int compare(JkModuleId o1, JkModuleId o2) {
            if (o1.group.equals(o2.group)) {
                return o1.name.compareTo(o2.name);
            }
            return o1.group.compareTo(o2.group);
        }

    }

}
