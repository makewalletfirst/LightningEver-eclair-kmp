[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-blue.svg?style=flat&logo=kotlin)](http://kotlinlang.org)
[![Maven Central](https://img.shields.io/maven-central/v/fr.acinq.lightning/lightning-kmp-core)](https://search.maven.org/search?q=g:fr.acinq.lightning%20a:lightning-kmp*)
![Github Actions](https://github.com/ACINQ/lightning-kmp/actions/workflows/test.yml/badge.svg)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**lightning-kmp** is a [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) implementation of the Lightning Network (see the [Lightning Network Specifications (BOLTs)](https://github.com/lightning/bolts)) optimized for mobile wallets. It can run on many platforms, including mobile devices (iOS and Android).

It is different from [eclair](https://github.com/ACINQ/eclair) which is an implementation optimized for servers (routing nodes).
It shares a lot of architecture choices with eclair though, which comes from years of experience developing one of the main lightning implementations.
But it optimizes completely different scenarios, as wallets will not relay payments but rather send and receive them.
Read [this article](https://medium.com/@ACINQ/when-ios-cdf798d5f8ef) for more details.

**lightning-kmp** is used in [Phoenix](https://phoenix.acinq.co/), the best non-custodial Lightning Wallet!

## Installation

See instructions [here](https://github.com/ACINQ/lightning-kmp/blob/master/BUILD.md) to build and test the library.

## Recovering on-chain funds

See instructions [here](./RECOVERY.md) to recover on-chain funds.

## Contributing

We use GitHub for bug tracking. Search the existing issues for your bug and create a new one if needed.

Contribute to the project by submitting pull requests.
Review is done by members of the ACINQ team.
Please read the guidelines [here](https://github.com/ACINQ/lightning-kmp/blob/master/CONTRIBUTING.md).

## Resources

* [1] [The Bitcoin Lightning Network: Scalable Off-Chain Instant Payments](https://lightning.network/lightning-network-paper.pdf) by Joseph Poon and Thaddeus Dryja
* [2] [Reaching The Ground With Lightning](https://github.com/ElementsProject/lightning/blob/master/doc/miscellaneous/deployable-lightning.pdf) by Rusty Russell
* [3] [When Phoenix on iOS?](https://medium.com/@ACINQ/when-ios-cdf798d5f8ef) - A blog post detailing why we created this library

---

## LightningEver fork — 260521OFFBOLT12 변경

이 fork 는 **BitEver L1 위의 LightningEver 운영** 을 위한 ACINQ lightning-kmp 1.11.5-DEBUG 의 사용자화 버전입니다. 이번 브랜치의 추가 변경:

### Mutual close NEGOTIATING 자동 해소 fallback

`Negotiating.kt`, `Offline.kt`, `Syncing.kt` 의 `WatchConfirmedTriggered.ClosingTxConfirmed` 분기에 fallback 한 가지 추가:

- 기존 ACINQ 동작: 확정된 closing tx 가 본인의 `publishedClosingTxs` 또는 `proposedClosingTxs` 중 어느 곳에도 매칭되지 않으면 `unknown closing transaction confirmed` 경고만 출력하고 채널 상태 무변경 → NEGOTIATING 영구 유지 가능
- 추가된 fallback: **확정된 tx 가 우리 funding output 을 spend** 하고 있으면 (보수적 가드) — 그 tx 를 mutual close 완료로 받아들이고 CLOSED 로 전환. 이는 simple_close + reconnect 가 동시에 발생할 때 양측이 서로 모르는 closing tx 변형을 broadcast 한 케이스를 안전하게 정리.

`Negotiating.kt` 의 `WatchSpent.ChannelSpent` 분기에도 동일 패턴 추가 — mempool 도착 시점에 fallback closing tx 등록.

이 변경 없이도 SyncingChannelReestablish 흐름이 결국 정리해 주는 것으로 보이나, 사용자 입장에서 채널이 "Negotiating" 으로 보이는 대기 시간을 단축하는 안전망입니다.

자세한 가이드는 LightningEver 프로젝트의 `260521OFFBOLT12.md` 참조.

---

## 260522_OFFSWAPIN 추가 변경 (이 브랜치)

**오프라인 스왑인 입금 자동 감지** 를 위한 wallet 측 wire 메시지 + 자동 전송.

### 변경 파일

- `modules/core/src/commonMain/kotlin/fr/acinq/lightning/wire/LightningMessages.kt`
  - 신규 사설 lightning message: `SwapInAddressRegister(addresses: List<String>)` (tag **35021**)
  - Wire: `[u16 count] [u16 len][len bytes ASCII bech32 address]*`
  - LSP 가 폰의 swap-in 주소들을 미리 알고 L1 모니터링 후 deposit 감지 시 wake-up push 를 보내기 위한 사전 등록 메시지
- `modules/core/src/commonMain/kotlin/fr/acinq/lightning/io/Peer.kt`
  - `registerFcmToken(token)` 안에서 `registerSwapInAddresses()` 도 함께 호출 — FCM 토큰 등록과 같은 타이밍 (reconnect 시) 에 swap-in 주소 일괄 등록
  - 새 함수 `registerSwapInAddresses(taprootGapLimit: Int = 20)` — legacy 단일 주소 + taproot 첫 20 인덱스 (gap-limit 스타일) 의 swap-in 주소들을 모아 `SwapInAddressRegister` 메시지 발사

### 운영 메모

LSP 측 plugin 의 EventStream 구독은 BOLT12 offline 결제 force-close 재현 이슈로 **일시 비활성**. 폰이 본 메시지를 보내도 LSP 가 받아 publish 까지만 하고 처리는 안 함. 폰 측 코드는 안전 (불필요 송신일 뿐 부작용 없음). 자세한 가이드: LightningEver 프로젝트의 `260522FCM.md`.
