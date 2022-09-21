# Integration test projects

This folder holds the various integration test projects.

- `gradle-plugin-test` - Smoke test project that verifies that our top level Gradle plugin can be
  applied on a both single and a multi platform modules. It is currently testing:
  - `single-platform` - Android single module project
  - `multi-platform` - Kotlin Multiplatform project with JVM and Native targets running on the host
     platform.
