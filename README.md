This is a Kotlin Multiplatform project targeting Web.

* [/sharedLogic](./sharedLogic/src) is for the code that will be shared between app targets in the project.
  The most important subfolder is [commonMain](./sharedLogic/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/webApp](./webApp) contains a React web application. It uses the Kotlin/JS library produced
  by the [sharedLogic](./sharedLogic) module.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Web app:
    1. Install [Node.js](https://nodejs.org/en/download) (which includes `npm`)
    2. Build and run the web application:
       ```shell
       npm run build:shared
       npm install
       npm run start
       ```

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Web tests: `./gradlew :sharedLogic:jsTest`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…