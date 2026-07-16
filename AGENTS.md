# Project overview

Apache Sling Servlets Resolver is an OSGi bundle that implements Sling servlet and script resolution services. It provides `ServletResolver`, the deprecated `SlingScriptResolver` bridge (`SlingScriptResolverImpl`), and Sling error handling integration, resolving requests by traversing resource-type hierarchies with selector/extension/method matching. It also tracks servlets registered as OSGi services, mounts them as virtual resources, handles bundled scripts via the `sling.servlet` capability, maintains resolver caches (with JMX exposure), and provides a Felix Web Console plugin for diagnostics. Recent resolver-cache hardening adds generation-aware cache writes to prevent stale servlet entries during concurrent cache flushes. Requires Java 17. Built with Maven and packaged as an OSGi bundle via bnd.

# Core commands

- **Build (compile + package OSGi bundle):** `mvn clean package -DskipTests`
- **Full test suite (unit + integration):** `mvn verify`
- **Unit tests only:** `mvn test`
- **Integration tests only (requires built jar):** `mvn verify -Dsurefire.skip=true`
- **Single unit test class:** `mvn test -Dtest=ResourceCollectorTest`
- **Race-condition regression unit test:** `mvn test -Dtest=ResolutionCacheRaceConditionTest`
- **Single integration test class:** `mvn verify -Dit.test=ServletSelectionIT`
- **Single resource-hiding integration test class:** `mvn verify -Dit.test=BasicResourceHidingIT`
- **SpotBugs static analysis:** `mvn spotbugs:check`
- **Skip integration tests:** `mvn verify -DskipITs`
- **Debug integration test container:** `mvn verify -Dpax.vm.options="-Xmx512M -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"`

There is no dev server — this is an OSGi bundle deployed into a running Sling instance.

# Project layout

```
pom.xml                          Maven build descriptor
bnd.bnd                          OSGi bundle manifest overrides
findbugs-exclude.xml             SpotBugs suppression rules
src/
  main/java/org/apache/sling/servlets/resolver/
    api/
      IgnoredServletResourcePredicate.java  Optional servlet/script hiding predicate API
    jmx/
      SlingServletResolverCacheMBean.java   Resolver cache management/inspection interface
    internal/
      SlingServletResolver.java             Core ServletResolver implementation
      SlingScriptResolverImpl.java          Deprecated SlingScriptResolver bridge
      ResolverConfig.java                   OSGi DS configuration interface (@ObjectClassDefinition)
      resolution/
        ResolutionCache.java                Caches servlet/script resolution results
      helper/
        ResourceCollector.java              Collects candidate resources for resolution
        LocationCollector.java              Computes search paths for a request
        WeightedResource.java               Sorting/ranking of resolution candidates
      resource/
        ServletMounter.java                 Registers servlets as virtual resources
        ServletResourceProvider.java
        MergingServletResourceProvider.java
      bundle/
        BundledScriptTracker.java           Tracks scripts in OSGi bundle capabilities
        BundledScriptTrackerHC.java         Optional health check for bundled-script consistency
        BundledScriptServlet.java
      defaults/
        DefaultServlet.java
        DefaultErrorHandlerServlet.java
      console/
        WebConsolePlugin.java               Felix Web Console diagnostic plugin
  test/java/org/apache/sling/servlets/resolver/
    internal/                               Unit tests (JUnit 4 + Mockito + Sling mocks)
      ResolutionCacheRaceConditionTest.java Regression test for cache flush/put race handling
    internal/resourcehiding/                Unit tests for hiding predicate behavior
    it/                                     Pax Exam integration tests
    it/resourcehiding/                      Integration tests for hidden servlet fallback behavior
target/                                     Build output — do not edit
```

# Development patterns & constraints

- **Java version:** 17; use `var` and records where appropriate, but avoid preview features.
- **OSGi annotations:** Use `org.osgi.service.component.annotations` (`@Component`, `@Reference`, `@Activate`, `@Deactivate`). Do not use Felix SCR annotations.
- **Configuration:** Declare component configs via `@ObjectClassDefinition` interfaces (see `ResolverConfig`). Property keys use `.` as separator matching the OSGi Metatype convention.
- **Imports:** Prefer constructor injection (immutable `@Reference` fields) where component lifecycle allows it.
- **Nullability:** Annotate nullable return values and parameters with `@Nullable` / `@NotNull` from `org.jetbrains.annotations`.
- **Logging:** SLF4J only (`org.slf4j.Logger`). No `java.util.logging` or `System.out`.
- **Formatting:** 4-space indentation, no tabs. Follow existing code style; Spotless may run via the parent build, so keep formatting consistent with existing files.
- **Package visibility:** Keep implementation classes in `*.internal.*`; only stable extension points belong in exported API packages (for example `org.apache.sling.servlets.resolver.api`).
- **Both servlet APIs:** The codebase supports both `javax.servlet` (Servlet 4) and `jakarta.servlet` (Servlet 6.1). When adding servlet-related code check both paths.
- **Resolution cache concurrency:** When adding resolver caching logic, capture `ResolutionCache` generation before resolution and pass it to cache writes; cache flushes and writes are coordinated via read/write locking to prevent stale re-population.
- **No public API changes without versioning:** OSGi semantic versioning is enforced via the `baseline` plugin. Changing exported package APIs requires a version bump aligned with OSGi rules.

# Git workflow

- Branch from `master` for all changes.
- Commit messages: `SLING-XXXXX - Short imperative description` (Jira issue prefix required for non-trivial changes).
- No force-pushes to `master`.
- PRs are reviewed via GitHub; CI runs the full Maven build including integration tests.
- Follow [Apache Sling contributing guidelines](https://sling.apache.org/contributing.html).

# Testing guidelines

- **Unit test framework:** JUnit 4 (`junit:junit`), Mockito 5, Sling OSGi Mock (`org.apache.sling.testing.osgi-mock`), Sling Mock (`org.apache.sling.testing.sling-mock`).
- **Integration test framework:** Pax Exam 4 with a forked OSGi container (Felix Framework). Tests suffixed `IT` run via `maven-failsafe-plugin`.
- **Test placement:** Unit tests in `src/test/java/.../internal/` (including `internal/resourcehiding/`); integration tests in `src/test/java/.../it/` (including `it/resourcehiding/`).
- **Coverage focus:** Prioritize resolution logic (`ResourceCollector`, `LocationCollector`, `SlingServletResolver`), cache invalidation/concurrency behavior (`ResolutionCache`, resolver cache-generation interactions), bundled-script tracking/health checks, and resource-hiding behavior via `IgnoredServletResourcePredicate`.
- **Running a single unit test:** `mvn test -Dtest=ClassName`
- **Running a single IT:** `mvn verify -Dit.test=ClassName`
- Integration tests spin up a real OSGi framework; they are slow (~1–2 min) and require the bundle jar to be built first.

# Gotchas

- **Build the jar before running ITs.** Failsafe reads `${bundle.filename}` (the project jar under `target/`). Running `mvn verify` from scratch handles this, but running Failsafe goals directly can fail if the jar is missing.
- **SpotBugs runs at `process-classes` phase**, before tests. A SpotBugs violation will prevent tests from running. Check `target/spotbugsXml.xml` for details. Use `findbugs-exclude.xml` to suppress false positives with justification.
- **Dual servlet API support (javax + jakarta):** Resolver behavior is validated across both APIs (for example secure-request opting tests). When fixing servlet behavior, review both execution paths.
- **OSGi baseline check:** Adding or changing exported types without bumping the package version causes a build failure. Run `mvn verify` to catch this early.
- **Pax Exam memory:** The forked OSGi container starts with `-Xmx512M` by default. Override with `-Dpax.vm.options` if tests OOM.
- **`ResolutionCache`** is a required OSGi service dependency of `SlingServletResolver`. In tests that mock the resolver, this must be provided or the component will not activate.
- **Resolver cache race safety:** A `flushCache()` can occur while request resolution is in progress. Resolver changes that touch cache writes must preserve generation-based stale-write rejection (capture generation before lookup/resolve, and only cache if generation is unchanged at put time).

# Security

<!-- sling-security-default:start -->
The threat model for this project is https://github.com/apache/sling/blob/master/docs/threat-model.md .
<!-- sling-security-default:end -->
