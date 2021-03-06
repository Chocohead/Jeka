package dev.jeka.core.integrationtest;

import dev.jeka.core.api.depmanagement.*;
import org.junit.Test;

import static dev.jeka.core.api.depmanagement.JkJavaDepScopes.COMPILE_AND_RUNTIME;
import static dev.jeka.core.api.depmanagement.JkJavaDepScopes.DEFAULT_SCOPE_MAPPING;
import static org.junit.Assert.assertEquals;

/**
 * Created by angibaudj on 19-06-17.
 */
public class ResolveHandleErrorIT {

    @Test
    public void artifactNotFound() {
        JkVersionedModule holder = JkVersionedModule.of("mygroup:myname:myversion");
        JkDependencySet deps = JkDependencySet.of()
                .and(JkPopularModules.JAVAX_SERVLET_API, "2.5.3", COMPILE_AND_RUNTIME);  // does not exist
        JkDependencyResolver resolver = JkDependencyResolver.of(JkRepo.ofMavenCentral().toSet())
                .withParams(JkResolutionParameters.of(DEFAULT_SCOPE_MAPPING))
                .withModuleHolder(holder);
        JkResolveResult resolveResult = resolver.resolve(deps);
        JkResolveResult.JkErrorReport errorReport = resolveResult.getErrorReport();
        System.out.println(errorReport.getModuleProblems());
        assertEquals(1, errorReport.getModuleProblems().size());

    }


}
