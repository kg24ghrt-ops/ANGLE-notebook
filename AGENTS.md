# Agent Instructions: Remote-Only Android Development

## Absolute Rule
NEVER compile, build, or test this project locally.

All compilation and build verification MUST happen through GitHub Actions.

## Local Environment
The local environment is ONLY for:
- Reading and editing source files.
- Creating Kotlin, Java, C++, CMake and Gradle files.
- Reviewing source code and Git diffs.
- Validating YAML syntax with Python.
- Checking project structure and configuration.
- Committing and pushing changes to GitHub.

## Forbidden Local Operations
NEVER:
- Run ./gradlew build.
- Run ./gradlew assembleDebug or assembleRelease.
- Run ./gradlew test or check.
- Run CMake or Ninja to compile the project.
- Invoke the Android NDK compiler (clang++).
- Install Android SDK or NDK for local compilation.
- Run any command that triggers a local APK or AAB build.
- Use local compilation as a validation step.

Do not attempt alternative local build methods if a build fails.

## Required Remote Build Workflow
1. Inspect the existing project and its Gradle configuration.
2. Implement the requested changes.
3. Review all modified files.
4. Inspect the Git diff for mistakes.
5. Validate YAML syntax using the available Python YAML validator.
6. Check that the GitHub Actions workflow is present.
7. Commit the completed changes.
8. Push to the existing GitHub remote and current branch.
9. Let GitHub Actions compile and package the application.
10. Report the workflow run and build result only when verified.

## Android Native Development
- Use Kotlin and Jetpack Compose for the Android application.
- Use C++17 for native C++ components.
- Use Android NDK and CMake through Gradle.
- Keep native build configuration compatible with the project's Android Gradle Plugin.
- Perform all native compilation on GitHub Actions.

## Git Safety
- Never force-push.
- Never overwrite unrelated user changes.
- Never commit secrets, signing keys, or local machine configuration.
- Preserve the existing Git remote and branch unless explicitly instructed otherwise.
- Report push failures honestly.

## Build Failure Handling
If GitHub Actions fails:
1. Inspect the remote workflow logs.
2. Identify the actual error.
3. Fix the relevant source or configuration.
4. Commit and push the fix.
5. Wait for remote verification.

Never claim that the application builds successfully without a successful GitHub Actions run.

## Final Instruction
EDIT LOCALLY.
VALIDATE SOURCE LOCALLY.
COMPILE REMOTELY.
PUSH TO GITHUB.
