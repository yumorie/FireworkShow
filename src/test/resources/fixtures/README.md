# Test configuration fixtures

These YAML files are stable inputs for parsing, cache, transform, timeline, and
invalid-configuration tests. They are deliberately independent of editable
installation defaults in `src/main/resources`.

Change fixtures when a configuration contract changes, not when adjusting an
example's appearance. `BundledShowTest` separately loads the actual installation
defaults and checks validity without pinning positions, sizes, or timings.

Maven copies these files only to test-classes; they are not plugin resources.
