# PCAPdroid MITM add-on attribution and boundary

RootTools interoperates with the independently installed PCAPdroid MITM add-on. The add-on owns its Python/mitmproxy runtime and is not bundled into the RootTools APK.

- Upstream: `https://github.com/emanuele-f/PCAPdroid-mitm`
- Expected package: `com.pcapdroid.mitm`
- Release channel opened by RootTools: `https://github.com/emanuele-f/PCAPdroid-mitm/releases`
- License/distribution boundary: independent GPL-3.0 application
- IPC boundary: Android `Messenger` messages and serializable `MitmConfig`

RootTools retains the client-side contract required for interoperability under `app/src/main/java/com/pcapdroid/mitm/MitmAPI.java`. Updating it requires checking message constants, serialized fields, package/service component names, CA retrieval, runtime events and compatibility tests against the selected upstream release.

RootTools must not silently download, install, uninstall, or relaunch the add-on. Installation is completed through Android's package installer; removal is completed through Android app settings. This keeps package management and GPL distribution consent outside the privileged network controller.
