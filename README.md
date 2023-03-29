# ugs-status-publisher

## Introduction

ugs-status-publisher integrates TeamCity with the [RUGS](https://github.com/jorgenpt/rugs) Unreal Game Sync (UGS) metadata server, allowing you to publish badges associated with changelists.

## Getting started

ugs-status-publisher is tested with [RUGS](https://github.com/jorgenpt/rugs), but should work with the default UGS metadata server (except the connection test which relies on a RUGS-specific endpoint). You can find details on configuring UGS on [the official Unreal Engine website](https://docs.unrealengine.com/5.0/en-US/unreal-game-sync-reference-guide-for-unreal-engine/).

### Installation

First, install this plugin on your Jenkins server:

1. Download the `.zip` file from [the latest release of this plugin](https://github.com/jorgenpt/teamcity-ugs-status-publisher/releases/latest)
1. On your Jenkins instance, go to `Administration`
1. Navigate to `Plugins` under the _Server Administration_ header
1. Click `+ Upload plugin zip`
1. Select `Choose File` and point it to the `ugsStatusPublisher.zip` you downloaded
1. Click `Upload plugin zip` and then choose to enable the plugin

### Configuration (UI)

1. Edit a specific build configuration or a template
1. Go to `Build Features`
1. Choose the `Commit status publisher` feature
1. Pick the `Unreal Game Sync` Publisher 
1. Set `Server URL` to the URL of your metadata server (without the `/api` suffix)
1. (Optional, if you're using RUGS with authentication) Configure the username and password according to the `ci_auth` credential in your RUGS installation's `config.json` (https://github.com/jorgenpt/rugs/blob/main/config.json.dist)
1. Fill in `Project` with the Perforce path to the folder that contains your `.uproject` file (e.g. if you've got `//project/main/MyProject/MyProject.uproject`, you should enter `//project/main/MyProject`)
1. Set the `Badge Name` that you want to appear inside UGS


### Configuration (Kotlin DSL)

Add the following snippet to your `BuildType`:

```kotlin
features {
    commitStatusPublisher {
        param("publisherId", "ugs")
        param("ugsServerUrl", "https://my-ugs-server") // The base URL of your metadata server, without the `/api` suffix
        param("ugsAuthUser", "robot_bob") // This should be the username from `ci_auth` in config.json, if you're using RUGS
        param("secure:ugsAuthPassword", "credentialsJSON:484fda88-e298-4058-9791-bb2f9b69514c") // Configure the credential as per usual under Tokens in the Versioned Settings on your project, if you're using RUGS 
        param("ugsProject", "//project/main/MyProject")
        param("ugsBadgeName", "Editor")
    }
}
```

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)