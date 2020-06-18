[<img src="https://sling.apache.org/res/logos/sling.png"/>](https://sling.apache.org)

 [![Build Status](https://builds.apache.org/buildStatus/icon?job=Sling/sling-org-apache-sling-servlets-resolver/master)](https://builds.apache.org/job/Sling/job/sling-org-apache-sling-servlets-resolver/job/master) [![Test Status](https://img.shields.io/jenkins/t/https/builds.apache.org/job/Sling/job/sling-org-apache-sling-servlets-resolver/job/master.svg)](https://builds.apache.org/job/Sling/job/sling-org-apache-sling-servlets-resolver/job/master/test_results_analyzer/) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/org.apache.sling.servlets.resolver/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22org.apache.sling.servlets.resolver%22) [![JavaDocs](https://www.javadoc.io/badge/org.apache.sling/org.apache.sling.servlets.resolver.svg)](https://www.javadoc.io/doc/org.apache.sling/org.apache.sling.servlets.resolver) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0) [![servlets](https://sling.apache.org/badges/group-servlets.svg)](https://github.com/apache/sling-aggregator/blob/master/docs/groups/servlets.md)

# Apache Sling Servlet Resolver

This module is part of the [Apache Sling](https://sling.apache.org) project.

Bundle implementing the Sling API ServletResolver.

## Bundled scripts
Version 2.7.0 of this bundle has added support for executing bundled scripts (precompiled or not), through the
`org.apache.sling.servlets.resolver.bundle.tracker` API.

Although traditionally scripts are deployed as content stored in the search paths of a Sling instance, this leaves very little
room for script evolution in a backwards compatible way. Furthermore, versioning scripts is a difficult process if the only
mechanism to do this is the `sling:resourceType` property, since consumers (content nodes or other resource types) have then to
explicitly mention the version expected to be executed.

Scripts should not be considered content, since their only purpose is to actually generate the rendering for a certain content
structure. They are not consumed by users, but rather by the Sling Engine itself and have very little meaning outside this
context. As such, scripts should be handled like code:

  1. they _provide an HTTP API_;
  2. they can evolve in a _semantical_ [1] way;
  3. they have a _developer audience_.

### How
Being built around a `BundleTrackerCustomizer` [2], the `org.apache.sling.servlets.resolver.bundle.tracker.internal.BundledScriptTracker`
monitors the instance's bundles wired to the `org.apache.sling.servlets.resolver` bundle and scans the ones providing a `sling.servlet`
capability [3]. The wiring is created by placing a `Require-Capability` header in the bundles that provide the `sling.servlet` capability:

```
osgi.extender;filter:="(&(osgi.extender=sling.scripting)(version>=1.0.0)(!(version>=2.0.0)))"
```

A `sling.servlet` capability has almost the same attributes as the properties required to register a servlet on the Sling platform [4]:

  1. `sling.servlet.resourceTypes:List` - mandatory; defines the provided resource type; its value is a list of resource types
  2. `sling.servlet.selectors:List` - optional; defines the list of selectors that this resource type can handle;
  3. `sling.servlet.extensions:List` - optional; defines the list of extensions that this resource type can handle;
  4. `sling.servlet.methods:List` - optional; defines the list of HTTP methods that this resource type can handle;
  5. `version:Version` - optional; defines the version of the provided `resourceType`;
  6. `extends:String` - optional; defines which resource type it extends; the version range of the extended resource type is defined in a
    `Require-Capability`.

The `BundledScriptTracker` will register a Sling Servlet with the appropriate properties for each `sling.servlet` capability. The
servlets will be registered using the bundle context of the bundle providing the `sling.servlet` capability, making
sure to expose the different versions of a resource type as part of the registered servlet's properties. On top of this, a plain resource
type bound servlet will also be registered, which will be automatically wired to the highest version of the `resourceType`. All the
mentioned service registrations are managed automatically by the `BundledScriptTracker`.

### So how do I deploy my scripts?
Short answer: exactly like you deploy your code, preferably right next to it. Pack your scripts using the following conventions:

  1. create a `src/main/resources/javax.script` folder in your bundle (if you want to embed the scripts as they are) or just put the
   scripts in `src/main/scripts` if you want to precompiled them (e.g. JSP and HTL);
  2. each folder under the above folders will identify a `resourceType`;
  3. inside each `resourceType` folder you can optionally create a `Version` folder; this has to follow the Semantic Versioning
   constraints described at [1];
  4. add your scripts, using the same naming conventions that you were used to from before [5];
  5. manually define your provide and require capabilities; just kidding; add the
  [`scriptingbundle-maven-plugin`](https://github.com/apache/sling-scriptingbundle-maven-plugin) to your build section and add its required
  properties in the `maven-bundle-plugin`'s instructions (check [these examples](https://github.com/apache/sling-org-apache-sling-scripting
  -bundle-tracker-it/tree/master/examples/));
  6. `mvn clean sling:install`.

### Integration Tests

The integration tests for bundled scripts are provided by the [`org.apache.sling.scripting.bundle.tracker.it`](https://github.com/apache
/sling-org-apache-sling-scripting-bundle-tracker-it) project.

## Resources
[1] - https://semver.org/  
[2] - https://osgi.org/javadoc/r6/core/org/osgi/util/tracker/BundleTrackerCustomizer.html  
[3] - https://osgi.org/download/r6/osgi.core-6.0.0.pdf, Page 41, section 3.3.3 "Bundle Capabilities"  
[4] - https://sling.apache.org/documentation/the-sling-engine/servlets.html#servlet-registration-1  
[5] - https://sling.apache.org/documentation/the-sling-engine/url-to-script-resolution.html
