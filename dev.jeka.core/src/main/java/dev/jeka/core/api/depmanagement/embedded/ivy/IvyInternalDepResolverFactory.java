package dev.jeka.core.api.depmanagement.embedded.ivy;

import dev.jeka.core.api.depmanagement.JkInternalDepResolver;
import dev.jeka.core.api.depmanagement.JkRepoSet;

/*
 * This class is only used with Refection. Please do not remove.
 */
final class IvyInternalDepResolverFactory {

    private static final String IVYRESOLVER_CLASS_NAME = IvyInternalDepResolver.class.getName();

    /*
     * Dependency resolver based on Apache Ivy.
     * This resolver is loaded in a dedicated classloader containing Ivy classes.
     * This method is only invoked by reflection. Please do not remove.
     */
    static JkInternalDepResolver of(JkRepoSet repos) {
        return IvyInternalDepResolver.of(repos);
    }

}
