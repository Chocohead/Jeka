package org.jerkar.integrationtest;

import static org.jerkar.api.depmanagement.JkJavaDepScopes.COMPILE;
import static org.jerkar.api.depmanagement.JkJavaDepScopes.DEFAULT_SCOPE_MAPPING;
import static org.jerkar.api.depmanagement.JkJavaDepScopes.RUNTIME;
import static org.jerkar.api.depmanagement.JkJavaDepScopes.TEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.jerkar.api.depmanagement.JkDependencies;
import org.jerkar.api.depmanagement.JkDependencyResolver;
import org.jerkar.api.depmanagement.JkModuleId;
import org.jerkar.api.depmanagement.JkPopularModules;
import org.jerkar.api.depmanagement.JkRepos;
import org.jerkar.api.depmanagement.JkResolutionParameters;
import org.jerkar.api.depmanagement.JkResolveResult;
import org.jerkar.api.depmanagement.JkScope;
import org.jerkar.api.utils.JkUtilsSystem;
import org.junit.Test;

public class ResolverWithoutScopeMapperIT {

    private static final JkRepos REPOS = JkRepos.mavenCentral();

    private static final JkScope MY_SCOPE = JkScope.of("myScope");

    @Test
    public void resolveCompile() {
        JkDependencies deps = JkDependencies.builder()
                .on(JkPopularModules.APACHE_COMMONS_DBCP, "1.4").scope(COMPILE)
                .build();
        JkDependencyResolver resolver = JkDependencyResolver.of(REPOS)
                .withParams(JkResolutionParameters.defaultScopeMapping(DEFAULT_SCOPE_MAPPING));
        JkResolveResult resolveResult = resolver.resolve(deps, COMPILE);
        assertTrue(resolveResult.contains(JkModuleId.of("commons-pool")));
        assertEquals(2, resolveResult.dependencyTree().flattenToVersionProvider().moduleIds().size());

        deps = JkDependencies.builder()
                .on(JkPopularModules.HIBERNATE_CORE, "5.2.10.Final").scope(COMPILE)
                .build();
        resolver = JkDependencyResolver.of(REPOS)
                .withParams(JkResolutionParameters.defaultScopeMapping(DEFAULT_SCOPE_MAPPING));
        resolveResult = resolver.resolve(deps, COMPILE);
        System.out.println(resolveResult.dependencyTree().toStringComplete());
        assertEquals(10, resolveResult.dependencyTree().flattenToVersionProvider().moduleIds().size());
    }

    @Test
    public void resolveInheritedScopes() {
        JkDependencies deps = JkDependencies.builder()
                .on(JkPopularModules.APACHE_COMMONS_DBCP, "1.4").scope(COMPILE)
                .build();
        JkDependencyResolver resolver = JkDependencyResolver.of(REPOS)
            .withParams(JkResolutionParameters.defaultScopeMapping(DEFAULT_SCOPE_MAPPING));

        // runtime classpath should embed the dependency as well cause 'RUNTIME' scope extends 'COMPILE'
        JkResolveResult resolveResult = resolver.resolve(deps, RUNTIME);
        assertEquals(2, resolveResult.dependencyTree().flattenToVersionProvider().moduleIds().size());
        assertTrue(resolveResult.contains(JkModuleId.of("commons-pool")));
        assertTrue(resolveResult.contains(JkModuleId.of("commons-dbcp")));

        // test classpath should embed the dependency as well
        resolveResult = resolver.resolve(deps, TEST);
        assertTrue(resolveResult.contains(JkModuleId.of("commons-pool")));
        assertTrue(resolveResult.contains(JkModuleId.of("commons-dbcp")));
        assertEquals(2, resolveResult.dependencyTree().flattenToVersionProvider().moduleIds().size());
    }

    @Test
    public void resolveWithOptionals() {
        JkDependencies deps = JkDependencies.builder()
                .on(JkPopularModules.SPRING_ORM, "4.3.8.RELEASE").mapScope(COMPILE).to("compile", "master", "optional")
                .build();
        JkDependencyResolver resolver = JkDependencyResolver.of(JkRepos.mavenCentral());
        JkResolveResult resolveResult = resolver.resolve(deps, COMPILE);
        assertEquals(37, resolveResult.dependencyTree().flattenToVersionProvider().moduleIds().size());
    }

    @Test
    public void resolveSpringbootTestStarter() {
        JkDependencies deps = JkDependencies.builder()
                .on("org.springframework.boot:spring-boot-starter-test:1.5.3.RELEASE").mapScope(TEST).to("master", "runtime")
                .build();
        JkDependencyResolver resolver = JkDependencyResolver.of(JkRepos.mavenCentral());
        JkResolveResult resolveResult = resolver.resolve(deps, TEST);
        Set<JkModuleId> moduleIds = resolveResult.dependencyTree().flattenToVersionProvider().moduleIds();

        // Unresolved issue happen on Travis : Junit is not part ofMany the result.
        // To unblock linux build, we do a specific check uniquely for linux
        if (JkUtilsSystem.IS_WINDOWS) {
            assertEquals("Wrong modules size " + moduleIds, 25, moduleIds.size());
            assertTrue(resolveResult.contains(JkPopularModules.JUNIT));
        } else {
            assertTrue(moduleIds.size() == 24 || moduleIds.size() == 25);
        }
    }

}
