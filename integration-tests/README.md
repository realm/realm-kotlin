# Integration test projects

This folder holds the various integration test projects.

- `gradle/` - Various smoke test project that verifies that our top level Gradle plugin can be
  applied on a both single and a multi platform modules. It is currently testing:
  - `single-platform` - Android single module project
  - `multi-platform` - Kotlin Multiplatform project with JVM and Native targets running on the host
     platform.
  There are various project with specific Gradle versions that has been proven troublesome with
  regards to collecting analytics data and a `current` project that will use the versions used to 
  build the SDK.

# TODO: will configure later