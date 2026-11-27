# Bug audit

Nothing could be compiled or run where the code was written, so this audit is static. It lists what was checked, what was found, and where the remaining risk is.

## Checks run
* Brace/paren/bracket balance on every Kotlin and Gradle file (tokenizer aware of strings, char literals and comments).
* Every `com.traceon` import resolves to a real declaration; package names match directories.
* All 104 Room `@Query` strings: bind parameters match function parameters, Flow vs `suspend` is consistent.
* Every repository/DAO/ViewModel member used by a screen exists; Koin constructor arity matches each definition.
* Taxonomy keyword specs parse (26 unique categories, no malformed entries, domain hints point at real categories).
* Capitalised identifiers with no import and no declaration (only fully-qualified names and false positives remained).
* The classifier and URL logic were re-implemented in Python and run against the unit-test inputs: all expectations matched.
* Workflow YAML parses.

## Found and fixed
1. **Crash risk:** Analytics "recent pages / searches" lists used `(contentId, time)` as LazyColumn keys; two imports with one-second timestamps can collide and Compose throws. Keys now include the row index.
2. **Release risk:** R8 was on by default for an app that has never run on a device. Now off unless `-PminifyRelease=true`.
3. **Release risk:** lint could fail `assembleRelease` on its own. `abortOnError = false` (lint still runs and reports).
4. **CI gap:** signing could only be configured via a file. `TRACEON_*` environment variables are now read too.
5. **Misleading label:** "Collected via" really shows the first collector; renamed "First collected via".
6. Earlier self-review fixes: wrong trailing-lambda target in the share screen, `use {}` return flow in the file reader, activity-result contracts re-registering on every recomposition, a function named `import`, and a `from`/`to` alias that is a reserved SQL word.

## Where a first build is most likely to fail (cannot be known without compiling)
* Compose / Material3 signatures that differ slightly between BOM versions (`TabRow`, `ModalBottomSheet`, `DatePickerDialog`, `AndroidView(onRelease=)`).
* Room KSP messages about the `activity_events` view, the `@RawQuery` POJO, or POJO columns.
* Kotlin inference in a few lambdas (`buildList` with pairs of lambdas, destructured `Pair` parameters in `items`).
* Koin DSL imports (`org.koin.androidx.viewmodel.dsl.viewModel`).

## Known behavioural limits (not bugs)
See `docs/LIMITATIONS.md`.
