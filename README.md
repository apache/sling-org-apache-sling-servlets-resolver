[![Apache Sling](https://sling.apache.org/res/logos/sling.png)](https://sling.apache.org)

&#32;[![Build Status](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-servlets-resolver/job/master/badge/icon)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-servlets-resolver/job/master/)&#32;[![Test Status](https://img.shields.io/jenkins/tests.svg?jobUrl=https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-servlets-resolver/job/master/)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-servlets-resolver/job/master/test/?width=800&height=600)&#32;[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-servlets-resolver&metric=coverage)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-servlets-resolver)&#32;[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-servlets-resolver&metric=alert_status)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-servlets-resolver)&#32;[![JavaDoc](https://www.javadoc.io/badge/org.apache.sling/org.apache.sling.servlets.resolver.svg)](https://www.javadoc.io/doc/org.apache.sling/org.apache.sling.servlets.resolver)&#32;[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/org.apache.sling.servlets.resolver/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22org.apache.sling.servlets.resolver%22)&#32;[![servlets](https://sling.apache.org/badges/group-servlets.svg)](https://github.com/apache/sling-aggregator/blob/master/docs/groups/servlets.md) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

# Apache Sling Servlet Resolver

This module is part of the [Apache Sling](https://sling.apache.org) project.

This OSGi bundle implements Sling's servlet and script resolution services:

- `org.apache.sling.api.servlets.ServletResolver` via `SlingServletResolver`
- `org.apache.sling.api.scripting.SlingScriptResolver` via `SlingScriptResolverImpl` (deprecated API bridge)
- `org.apache.sling.api.servlets.JakartaErrorHandler` for error handling using Sling's resolution algorithm

See the [servlets](https://sling.apache.org/documentation/the-sling-engine/servlets.html) and [scripts](https://sling.apache.org/documentation/bundles/scripting.html) documentation for resolution behavior.

## Highlights

- Supports both `javax.servlet` (4.x) and `jakarta.servlet` (6.1) APIs
- Resolves scripts and servlets across resource-type hierarchies with selector/extension/method matching
- Mounts OSGi servlet services into the resource tree through dedicated resource providers
- Tracks bundled scripts contributed through OSGi capabilities (`sling.servlet`)
- Includes resolver diagnostics through a Felix Web Console plugin
- Supports optional servlet/script hiding via `IgnoredServletResourcePredicate`
- Provides a configurable bundled-script health check (`BundledScriptTrackerHC`)

## Build and test

This module requires **Java 17** and uses Maven.

- Build bundle (skip tests): `mvn clean package -DskipTests`
- Run unit tests: `mvn test`
- Run full verification (unit + integration tests): `mvn verify`
- Run integration tests only: `mvn verify -Dsurefire.skip=true`
- Run SpotBugs check: `mvn spotbugs:check`

## Project structure

```text
src/main/java/org/apache/sling/servlets/resolver/
  internal/
    SlingServletResolver.java
    SlingScriptResolverImpl.java
    ResolverConfig.java
    helper/      (resource and location collectors)
    resource/    (servlet mounting/resource providers)
    bundle/      (bundled script tracking and servlet wrapper support)
    defaults/    (default and error handler servlets)
    console/     (Web Console diagnostics)
  jmx/
    SlingServletResolverCacheMBean.java

src/test/java/org/apache/sling/servlets/resolver/
  internal/      (unit tests)
  it/            (Pax Exam integration tests)
```
