# LightningEver Kotlin Multiplatform Core (`lightning-kmp`)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-blue.svg?style=flat&logo=kotlin)](http://kotlinlang.org)
[![Version](https://img.shields.io/badge/version-1.11.5--DEBUG-orange.svg)](#installation)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**`lightning-kmp`** is a [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) implementation of the Lightning Network (see the [Lightning Network Specifications (BOLTs)](https://github.com/lightning/bolts)) optimized for mobile wallets, developed originally by **ACINQ** and utilized in the Phoenix Wallet.

This repository is the **BitEver (BEC) Fork** of `lightning-kmp` (v1.11.5-DEBUG), customized and patched to power the **LightningEver** non-custodial Lightning Network system. It contains specific blockchain adjustments, protocol extensions, fee fallbacks, and MuSig2 Taproot channel fixes necessary to run seamlessly on top of the BitEver L1 network.

---

## 1. LightningEver System Architecture

**LightningEver** is a customized, closed Lightning Network ecosystem built on top of the **BitEver (BEC) L1** blockchain. It is powered by three main components forked and patched from the ACINQ Phoenix stack:
1. **Eclair LSP** (Server-side Routing & Liquidity Provider)
2. **`lightning-kmp`** (Kotlin Multiplatform Lightning Engine)
3. **Phoenix Android** (The user-facing mobile app, branded as **LightningEver**)

### 1-1. Network Topology & Communication Matrix

The network operates on a **hub-and-spoke topology** centered around a single Eclair LSP. Mobile wallets do not establish direct channels with each other; instead, all transactions and channel operations are routed through the LSP.

```
                  ┌─────────────────────────────────────────────────────────────┐
                  │                       BitEver (BEC) L1                      │
                  │  • Custom Bitcoin fork (Taproot, MuSig2, P2TR)              │
                  │  • Own consensus, mining, and chainhash:                     │
                  │    6fe28c0ab6f1b372c1a6a246ae63f74f931e8365e15a089c68d61... │
                  │  • bitcoind RPC: 10.8.0.X:8334, ZMQ: 28332/28333            │
                  │  • Electrs Indexer: electrs.ever-chain.xyz:50001            │
                  └─────────────────────────────────────────────────────────────┘
                            ▲                              ▲                         ▲
                            │ RPC & ZMQ                    │ Electrum RPC            │ Electrum RPC
                            │                              │                         │
                  ┌─────────┴──────────────┐  ┌────────────┴───────────┐  ┌──────────┴─────────────┐
                  │       Eclair LSP       │  │  Phoenix Android App   │  │  Phoenix Android App   │
                  │     (BitEver Fork)     │  │   (LightningEver A)    │  │   (LightningEver B)    │
                  │   ─────────────────    │◀─┤   ──────────────────   │  │   ──────────────────   │
                  │   • Node ID: 0311fb... │  │   • uses lightning-kmp │  │   • uses lightning-kmp │
                  │   • Host: eclair.      │  │   • LSP Peer: 0311fb.. │  │   • LSP Peer: 0311fb.. │
                  │     ever-chain.xyz     │  └────────────────────────┘  └────────────────────────┘
                  │   • Port: 9735 (P2P)   │              │                            ▲
                  │   • Port: 8080 (REST)  │              └─────── Lightning P2P ──────┘
                  └────────────────────────┘
```

### 1-2. Detailed Protocol Connections

| Source | Destination | Protocol | Port | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **Phoenix Android** | **Eclair LSP** | Lightning P2P | `9735` | Noise XK handshake, channel negotiations, splice, trampoline payments |
| **Phoenix Android** | **BitEver Electrs** | Electrum (no TLS) | `50001` | Header sync, swap-in scans, transaction broadcasting, fee estimation |
| **Eclair LSP** | **BitEver bitcoind** | JSON-RPC | `8334` | Transaction broadcasting, UTXO querying, fee estimations |
| **Eclair LSP** | **BitEver bitcoind** | ZeroMQ (ZMQ) | `28332`/`28333` | Real-time block and transaction notifications |
| **Eclair LSP** | **BitEver Electrs** | Electrum (no TLS) | `50001` | Watching the mempool for confirmation tracking |
| **Phoenix Android** | **Google FCM** | HTTPS / Push | (Google) | LSP wakes up mobile clients for offline payments using push tokens |
| **Operator / CLI** | **Eclair LSP** | JSON-REST API | `8080` | Local node administration (`eclair-cli`) |

---

## 2. Key Technology Stack

- **Core Language & Framework**: Kotlin Multiplatform (KMP) v2.3.10 with targets for JVM, Android, and iOS.
- **Lightning Engine**: `lightning-kmp-core` v1.11.5-DEBUG (containing all BitEver protocol changes).
- **L1 Blockchain Platform**: BitEver (BEC) Core (built on custom Bitcoin fork utilizing Taproot, MuSig2, P2TR, and custom block hashing).
- **Storage / DB**: SQLite on Eclair LSP; SQLDelight (wrapped in Kotlin) on Phoenix Android.
- **Push Notification Backend**: Google Firebase Cloud Messaging (FCM) integrated with Eclair LSP to trigger wake-ups.

---

## 3. Core Modifications & Patches (vs Upstream ACINQ)

To make `lightning-kmp` compatible with the custom environment of the BitEver L1 chain and fix several protocol edge cases, the following critical patches were implemented:

### 3-1. On-Chain Feerate Fallback
* **Target File**: [Peer.kt](file:///root/lightning-kmp/modules/core/src/commonMain/kotlin/fr/acinq/lightning/io/Peer.kt)
* **Problem**: The BitEver Electrs indexer does not support `estimatefee` due to the small blockchain history. As a result, the wallet client's `onChainFeeratesFlow` remains null indefinitely. When the user attempts a `Force Close` or `Mutual Close`, the process suspends forever waiting for a valid fee rate.
* **Patch**: If Electrum fee retrieval fails, a fallback is triggered to set `FeeratePerKw.MinimumFeeratePerKw` (`253 sat/kw`). This prevents infinite suspensions and allows closure transactions to build and broadcast safely.

### 3-2. MuSig2 Public Nonce Sorting in Taproot Channels
* **Target Files**: 
  - [Commitments.kt](file:///root/lightning-kmp/modules/core/src/commonMain/kotlin/fr/acinq/lightning/channel/Commitments.kt) (`sign()`, `sendCommit()`)
  - [Helpers.kt](file:///root/lightning-kmp/modules/core/src/commonMain/kotlin/fr/acinq/lightning/channel/Helpers.kt) (`ClosingComplete`, `ClosingSig` flows)
  - [InteractiveTx.kt](file:///root/lightning-kmp/modules/core/src/commonMain/kotlin/fr/acinq/lightning/channel/InteractiveTx.kt) (`SharedFundingInput.sign()`, first commitment builder)
  - [Transactions.kt](file:///root/lightning-kmp/modules/core/src/commonMain/kotlin/fr/acinq/lightning/transactions/Transactions.kt) (`checkRemotePartialSignature()`, `aggregateSigs()`)
* **Problem**: Under the `option_simple_taproot_phoenix` specification, MuSig2 public nonces must be ordered strictly in accordance with their corresponding sorted public keys (`Scripts.sort(listOf(localFundingPubKey, remoteFundingPubKey))`). Upstream ACINQ hardcoded `[localNonce, remoteNonce]` ordering. If the remote key sorted first, signature verification failed, causing channels to crash during creation, commits, or splicing.
* **Patch**: Implemented deterministic nonce sorting across all **6+1 key areas** listed above. The order of nonces now mirrors the sorting result of funding public keys, fixing the invalid signature issues for Taproot channels, Splice transactions, and Request Liquidity actions.

### 3-3. Negotiating State Auto-Resolve Fallback
* **Target Files**: 
  - [Negotiating.kt](file:///root/lightning-kmp/modules/core/src/commonMain/kotlin/fr/acinq/lightning/channel/states/Negotiating.kt)
  - [Offline.kt](file:///root/lightning-kmp/modules/core/src/commonMain/kotlin/fr/acinq/lightning/channel/states/Offline.kt)
  - [Syncing.kt](file:///root/lightning-kmp/modules/core/src/commonMain/kotlin/fr/acinq/lightning/channel/states/Syncing.kt)
* **Problem**: If a mutual closing transaction is confirmed on-chain but does not match any of the tracked `publishedClosingTxs` or `proposedClosingTxs` (which can happen under race conditions or disconnection right after mutual close initialization), the channel would hang in the `Negotiating` state forever.
* **Patch**: Added a safety fallback in the `WatchConfirmedTriggered.ClosingTxConfirmed` and `WatchSpent.ChannelSpent` handlers. If the confirmed transaction is detected to spend our funding output, the library automatically treats it as a successful mutual close, updates the channel state to `CLOSED`, and terminates negotiation.

### 3-4. Re-Broadcast of Negotiating Closing Transactions on Startup
* **Target File**: [WaitForInit.kt](file:///root/lightning-kmp/modules/core/src/commonMain/kotlin/fr/acinq/lightning/channel/states/WaitForInit.kt)
* **Problem**: If the wallet application was restarted while a channel was in `Negotiating` state, the previously proposed mutual close transactions were not re-published or watched again on the Electrum network. The mutual close process would get stuck.
* **Patch**: Modified the state restoration flow during `is Negotiating` block to re-publish and register watcher listeners for all closing transactions.

### 3-5. Shortened CSV Delay for Force Closes
* **Target File**: [NodeParams.kt](file:///root/lightning-kmp/modules/core/src/commonMain/kotlin/fr/acinq/lightning/NodeParams.kt)
* **Problem**: Mainnet Bitcoin enforces a `toRemoteDelayBlocks` value of `2016` blocks (approx. 2 weeks) for CSV timelocks during force-closes, which is impractical for development and testing.
* **Patch**: Reduced the delay parameter to `144` blocks (approx. 24 hours) for fast cycle testing of forced closures and balance recovery.

### 3-6. Offline Swap-In Address Pre-Registration (Wire Message Tag `35021`)
* **Target Files**:
  - [LightningMessages.kt](file:///root/lightning-kmp/modules/core/src/commonMain/kotlin/fr/acinq/lightning/wire/LightningMessages.kt)
  - [Peer.kt](file:///root/lightning-kmp/modules/core/src/commonMain/kotlin/fr/acinq/lightning/io/Peer.kt)
* **Problem**: To support seamless swap-in deposits when the mobile wallet is offline, the LSP needs a way to monitor the wallet's deposit addresses and wake up the client when a deposit is received.
* **Patch**: 
  - Added a new custom Lightning message: `SwapInAddressRegister(addresses: List<String>)` with tag **`35021`**.
  - Integrated this into the Peer connection sequence. Upon handshake and alongside the FCM push token registration, the wallet automatically derives its legacy swap-in address and the first 20 Taproot swap-in addresses (gap limit style) and registers them with the LSP.

---

## 4. Operational Safety & Security Bypasses (WARNING)

> [!WARNING]
> This fork is **strictly meant for development and testing** on the BitEver test/dev blockchain network. It contains intentional security bypasses and MUST NOT be used on the public Bitcoin Mainnet.

| Bypass Location | Description | Consequence / Reason |
| :--- | :--- | :--- |
| **`Transactions.scala`** (Eclair LSP) | Hardcoded `checkRemoteSig`, `checkRemotePartialSignature`, and `checkRemoteHtlcSig` to return `true`. | Disables signature validation for quick dev verification. |
| **`Helpers.scala`** (Eclair LSP) | Skips min-funding check (`minFunding=0` allowed) when `usesOnTheFlyFunding` is active. | Enables On-The-Fly (OTF) channel creation for wallets with 0 balance. |
| **`InteractiveTxBuilder.scala`** (Eclair LSP) | Bypasses remote channel reserve checks. | Permits splicing out even if the wallet balance falls below the standard 1% reserve. |
| **`Helpers.scala`** (Eclair LSP) | Ignores `MissingCommitNonce` on Taproot channels during reconnections. | Avoids accidental force-closes during connection instability. |
| **`Setup.scala`** (Eclair LSP) | Disables verification checks on headers sync and initial block downloads. | Operates normally despite the lower cumulative work of the custom BitEver chain. |
| **`Peer.kt`** (`lightning-kmp`) | Fallback fee rate injection of `253 sat/kw`. | Prevents hanging transactions when Electrum fees return null. |

---

## 5. Build and Local Publication Guide

### Prerequisites
- **Java**: OpenJDK 21
- **Docker**: Required if you wish to run integration/mini-wallet tests.
- **macOS / iOS builds**: Xcode, Homebrew, and native libraries:
  ```sh
  brew install libtool gmp
  ```

### Build & Publish to Maven Local
To compile the Kotlin Multiplatform project and publish the artifacts into your local Maven cache, run:

```bash
# Clean, compile, and publish all KMP targets locally
./gradlew :lightning-kmp-core:publishToMavenLocal
```

Alternatively, to publish only specific targets (e.g., JVM and Kotlin Multiplatform core):
```bash
./gradlew :lightning-kmp-core:publishJvmPublicationToMavenLocal \
          :lightning-kmp-core:publishKotlinMultiplatformPublicationToMavenLocal
```

* **Build Output Path**: The library artifact will be published to:
  `~/.m2/repository/fr/acinq/lightning/lightning-kmp-core-jvm/1.11.5-DEBUG/`

### Running Tests
To run unit and integration tests:
```bash
./gradlew jvmTest       # Run tests targeting the JVM
./gradlew allTests      # Run tests on all configured target platforms
```

---

## 6. Integration and Deployment to Phoenix (LightningEver App)

To consume the newly built `lightning-kmp` library inside the **Phoenix Android** (LightningEver App) codebase:

1. **Enable Local Repository**: Ensure the Phoenix Android's parent `build.gradle.kts` (or `phoenix-shared/build.gradle.kts`) contains the `mavenLocal()` repository directive at the top of its repositories list:
   ```kotlin
   repositories {
       mavenLocal()
       mavenCentral()
       google()
   }
   ```
2. **Bind Dependency**: Point the dependency string to the custom debug version:
   ```kotlin
   implementation("fr.acinq.lightning:lightning-kmp-core:1.11.5-DEBUG")
   ```
3. **Compile the App**: Assemble the debug APK using Gradle:
   ```bash
   ./gradlew :phoenix-android:assembleDebug
   ```
   This generates the runnable APK file (e.g., `phoenix-115-xxxx-mainnet-debug.apk`) which can be side-loaded onto test Android devices.

---

## 7. Operational & Verification CLI Commands (LSP-side)

Use the following cURL commands to verify node connectivity, channel states, and OTF funding status on the Eclair LSP (port `8080` REST interface):

```bash
# 1. Retrieve General Node & L1 Synchronization Info
curl -s -u :PASSWORD -X POST http://localhost:8080/getinfo | jq .

# 2. List All Active Channels and Their Current State
curl -s -u :PASSWORD -X POST http://localhost:8080/channels | jq '.[] | {channelId, state, nodeId}'

# 3. Retrieve Detailed State of a Specific Channel
curl -s -u :PASSWORD -X POST http://localhost:8080/channel -d "channelId=<CHANNEL_ID_HEX>" | jq .

# 4. List Connected Peers
curl -s -u :PASSWORD -X POST http://localhost:8080/peers | jq .

# 5. Enable On-The-Fly (OTF) Funding (Required once after LSP restart)
curl -s -u :PASSWORD -X POST http://localhost:8080/enablefromfuturehtlc
```

---

## 8. License

This repository is licensed under the **ACINQ Public Software License** (which is identical to the upstream licensing terms). Please refer to the [LICENSE](LICENSE) file for further details.
