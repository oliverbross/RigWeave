# RigWeave working rules

- Mobile implementation, build, and device validation are Android-only until the owner explicitly re-enables iOS work.
- Do not edit, build, test, archive, or install the iOS app as part of Android tasks.
- Finish each requested change by building the Android app, installing it on the connected Android tablet, then committing and pushing the completed task to the private GitHub project.
- Preserve tablet-local storage; do not introduce SD-card dependencies.
