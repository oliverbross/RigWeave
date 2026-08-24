# Windows packaging

`package.ps1` installs into a disposable staging directory, retains Qt/QML/SQL plugins selected by `qt_generate_deploy_qml_app_script`, creates the portable ZIP, and invokes CPack/NSIS for a per-user, unsigned development-alpha installer.

The uninstall target removes installed application files and Start Menu integration. Runtime QSO databases, configuration, cache, logs, exports, and support bundles live under `QStandardPaths` outside the install tree and are not deleted by default.
