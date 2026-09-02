# M9 database migrations

Android migrates 17 to 18 because the accepted base already used schema 17. Apple introduces sync metadata as user version 1. Both paths are additive, transactional, preserve existing canonical QSO/configuration data, refuse future versions, and have rollback/migration tests. Raw database synchronization is prohibited.
