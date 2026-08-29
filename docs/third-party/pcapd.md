# PCAPdroid pcapd binary attribution

RootTools bundles the precompiled `pcapd` capture daemon from the PCAPdroid project for per-UID root packet capture. The imported local distribution explicitly permits bundling in proprietary applications when PCAPdroid attribution is provided.

- Upstream project: `https://github.com/emanuele-f/PCAPdroid`
- Binary distribution: `https://github.com/emanuele-f/pcapd-bin`
- Original local integration: `Net-Tools/references/pcapd-bin/dist`
- Capture bridge contract: PCAPdroid `pcapd.h`

Pinned imported artifacts:

| ABI | RootTools path | SHA-256 |
|---|---|---|
| arm64-v8a | `app/src/main/jniLibs/arm64-v8a/libpcapd.so` | `4cc86aa41502882ec3a6ad4dab0d6c64aea3ee5d0efa23a7bdc208da646c3697` |
| x86_64 | `app/src/main/jniLibs/x86_64/libpcapd.so` | `cfa294bf6d0b3e32a8cb4708f4c0cda686f1e325152cb2671729dd9bcc97c527` |

The daemon is never exposed as a general shell tool. RootTools copies and starts it only through the typed network-capture controller path. Updating either binary requires recording the new upstream revision, hashes, ABI matrix and device validation evidence in this document.
