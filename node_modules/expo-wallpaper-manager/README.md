<p align="center">
    <h1 align="center">EXPO-WALLPAPER-MANAGER</h1>
</p>
<p align="center">
    <em><code>Wallpaper Manager Module for Expo</code></em>
</p>
<p align="center">
	<img src="https://img.shields.io/github/license/roeintheglasses/expo-wallpaper-manager?style=flat&color=0080ff" alt="license">
	<img src="https://img.shields.io/github/last-commit/roeintheglasses/expo-wallpaper-manager?style=flat&logo=git&logoColor=white&color=0080ff" alt="last-commit">
	<img src="https://img.shields.io/github/languages/top/roeintheglasses/expo-wallpaper-manager?style=flat&color=0080ff" alt="repo-top-language">
	<img src="https://img.shields.io/github/languages/count/roeintheglasses/expo-wallpaper-manager?style=flat&color=0080ff" alt="repo-language-count">
<p>
<p align="center">
		<em>Developed with the software and tools below.</em>
</p>
<p align="center">
	<img src="https://img.shields.io/badge/Kotlin-7F52FF.svg?style=flat&logo=Kotlin&logoColor=white" alt="Kotlin">
	<img src="https://img.shields.io/badge/React_Native-61DAFB.svg?style=flat&logo=React&logoColor=black" alt="React-Native">
	<img src="https://img.shields.io/badge/TypeScript-3178C6.svg?style=flat&logo=TypeScript&logoColor=white" alt="TypeScript">
	<img src="https://img.shields.io/badge/Gradle-02303A.svg?style=flat&logo=Gradle&logoColor=white" alt="Gradle">
	<img src="https://img.shields.io/badge/Expo-000020.svg?style=flat&logo=Expo&logoColor=white" alt="Expo">
</p>
<hr>

##  Quick Links

> - [ Overview](#-overview)
> - [ Repository Structure](#-repository-structure)
> - [ Modules](#-modules)
> - [ Getting Started](#-getting-started)
>   - [ Installation](#-installation)
> - [ Getting Started For Contributors](#-getting-started-for-contributors)
>   - [ Installation](#-module-installation)
> - [ Contributing](#-contributing)
> - [ License](#-license)

---

##  Overview

<code>Native android Wallpaper Manager Module implementation for Expo based on the [ Android Wallpaper Manager API ](https://developer.android.com/reference/android/app/WallpaperManager)
</code>

##  Repository Structure

```sh
└── expo-wallpaper-manager/
    ├── README.md
    ├── android
    │   ├── build.gradle
    │   └── src
    │       └── main
    │           ├── AndroidManifest.xml
    │           └── java
    │               └── expo
    ├── expo-module.config.json
    ├── package-lock.json
    ├── package.json
    ├── src
    │   ├── ExpoWallpaperManagerModule.ts
    │   └── index.ts
    ├── tsconfig.json
    └── yarn.lock
```

---

##  Modules

<details closed><summary>Android Modules</summary>

| File                                                                                                                                                                                     | Summary                         |
| ---                                                                                                                                                                                      | ---                             |
| [ExpoWallpaperManagerModule.kt](https://github.com/roeintheglasses/expo-wallpaper-manager/blob/master/android/src/main/java/expo/modules/wallpapermanager/ExpoWallpaperManagerModule.kt) | <code>► Kotlin Code for the wallpaper manager dependency</code> |

</details>

<details closed><summary>JS Module</summary>

| File                                                                                                                                     | Summary                         |
| ---                                                                                                                                      | ---                             |
| [index.ts](https://github.com/roeintheglasses/expo-wallpaper-manager/blob/master/src/index.ts)                                           | <code>►Entry module for the js wallpaper manager</code> |
| [ExpoWallpaperManagerModule.ts](https://github.com/roeintheglasses/expo-wallpaper-manager/blob/master/src/ExpoWallpaperManagerModule.ts) | <code>►Native Module Wrapper</code> |

</details>



---

##  Getting Started

###  Installation

1. Install the expo-wallpaper-manager module in your expo project:

```sh
yarn add expo-wallpaper-manager
```
or

```sh
npm install expo-wallpaper-manager
```
---

##  Getting Started for Contributors

- [ Wallpaper Manager API Documentation ](https://developer.android.com/reference/android/app/WallpaperManager)

### Module Installation

1. Clone the expo-wallpaper-manager repository:

```sh
git clone https://github.com/roeintheglasses/expo-wallpaper-manager
```

2. Change to the project directory:

```sh
cd expo-wallpaper-manager
```

3. Install the dependencies:

```sh
yarn install
```
---

##  Contributing

Contributions are welcome! Here are several ways you can contribute:

- **[Submit Pull Requests](https://github.com/roeintheglasses/expo-wallpaper-manager/)**: Review open PRs, and submit your own PRs.
- **[Join the Discussions](https://github.com/roeintheglasses/expo-wallpaper-manager/discussions)**: Share your insights, provide feedback, or ask questions.
- **[Report Issues](https://github.com/roeintheglasses/expo-wallpaper-manager/issues)**: Submit bugs found or log feature requests for the `expo-wallpaper-manager` project.

<details closed>
    <summary>Contributing Guidelines</summary>

1. **Fork the Repository**: Start by forking the project repository to your github account.
2. **Clone Locally**: Clone the forked repository to your local machine using a git client.
   ```sh
   git clone https://github.com/roeintheglasses/expo-wallpaper-manager
   ```
3. **Create a New Branch**: Always work on a new branch, giving it a descriptive name.
   ```sh
   git checkout -b new-feature-x
   ```
4. **Make Your Changes**: Develop and test your changes locally.
5. **Commit Your Changes**: Commit with a clear message describing your updates.
   ```sh
   git commit -m 'Implemented new feature x.'
   ```
6. **Push to GitHub**: Push the changes to your forked repository.
   ```sh
   git push origin new-feature-x
   ```
7. **Submit a Pull Request**: Create a PR against the original project repository. Clearly describe the changes and their motivations.

Once your PR is reviewed and approved, it will be merged into the main branch.

</details>

---

##  License

This project is protected under the [GNU AGPLv3](https://choosealicense.com/licenses/agpl-3.0/) License.

---
