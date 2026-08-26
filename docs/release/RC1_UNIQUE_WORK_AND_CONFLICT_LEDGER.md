# RC1 Unique Work and Conflict Ledger

Read-only `git merge-tree` analysis between canonical source and review tip `00fe01cd56c206543b1afb0fb03dfdb9befb92f7` identified 34 clean paths and 10 textual conflicts. A direct merge was rejected because it would reintroduce superseded screen and owner architecture. Accepted repairs were reapplied as distinct semantic commit `5ee25b51d979d319bdc2bc9410c5af3599b87887` and immediately validated.

| Concern | Resolution | Evidence |
| --- | --- | --- |
| Digi TX arm race, bounded WAV input, RX readback and raw recorder | integrated into current `DigiController`/recorder owners | Android JVM tests and androidTest source compilation |
| N1MM peer trust and staged mutation safety | persisted trust; accepted add stages through canonical QSO owner; edits/deletes remain review-only | N1MM transport scale and safety suites |
| Groups.io serialization and token handling | integrated around current JSON/database owner | JVM and database instrumentation-source tests |
| Wavelog identity/ambiguity and transactional mutation | integrated without destructive fallback | JVM, Apple golden and storage tests |
| CAT/Flex readback, telemetry and shutdown | integrated fail-closed | C++ core tests and Android safety tests |
| Apple QSO/storage/network shutdown | integrated into current Swift owners | generic device/simulator builds and Fast Entry goldens |
| backup/privacy exclusions | integrated in Android backup/data-extraction rules | RC privacy audit |
| obsolete Operations dashboard and simplified Contest screens | SUPERSEDED_BY_RC; not applied | canonical owner graph and completion matrix |
| protected-main merge topology | UNRELATED_PRESERVED; not applied | ancestry proof |

The focused gates passed before release work began: Android `testDebugUnitTest compileDebugAndroidTestSources`, C++ Debug CTest 5/5, and Apple Fast Entry 3/3. No conflict markers or patch whitespace errors remained.
