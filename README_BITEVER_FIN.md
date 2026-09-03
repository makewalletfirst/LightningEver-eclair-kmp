# BitEver lightning-kmp — 260517_FIN 브랜치 (FINAL)

LightningEver의 핵심 라이트닝 프로토콜 라이브러리 (Phoenix Android가 의존). ACINQ lightning-kmp 1.11.5를 BitEver L1 체인용으로 fork + 7개 패치.

## 7가지 검증된 마일스톤

1. ✅ L1 swap-in → 채널 자동 생성
2. ✅ 양측 채널 보유 시 Bolt12 송금
3. ✅ 채널 없는 폰에게 Bolt12 송금 (수신측 OTF)
4. ✅ 외부 L1 송금 (splice-out)
5. ✅ 지정 주소 mutual close
6. ✅ Force close + 144 블록 CSV
7. ✅ Request Liquidity (splice-in)

## 7개 핵심 패치 (vs upstream 1.11.5)

### 1. `io/Peer.kt::updateFeerates()` — feerate fallback

**문제**: BitEver electrs는 `estimatefee` 항상 실패 → `onChainFeeratesFlow.value=null` → ForceClose `currentOnChainFeerates()` 영원히 suspend.

```kotlin
client.getFeerates()?.let { ... } ?: run {
    if (onChainFeeratesFlow.value == null) {
        val minFee = FeeratePerKw.MinimumFeeratePerKw
        onChainFeeratesFlow.value = OnChainFeerates(minFee, minFee, minFee, minFee)
    }
}
```

### 2. `channel/states/WaitForInit.kt` — Negotiating 복원

**문제**: 앱 재시작 시 `Negotiating` 상태에서 `publishedClosingTxs`가 다시 broadcast 안 됨 → mutual close stuck.

`is Negotiating ->` 분기에서 명시적 publish + watch 추가.

### 3-6. musig2 nonce 정렬 (6+1곳)

**문제**: musig2의 `publicNonces[i]`는 `sortedKeys[i]`와 대응해야 함. 기본 코드는 `[localNonce, remoteNonce]` 고정 → `Scripts.sort()` 결과 remote가 first면 서명 검증 실패.

**일관된 패턴**:
```kotlin
val sortedFundingKeys = Scripts.sort(listOf(fundingKey.publicKey(), remoteFundingPubkey))
val orderedNonces = if (sortedFundingKeys.first() == fundingKey.publicKey())
    listOf(localNonce.publicNonce, remoteNonce)
else
    listOf(remoteNonce, localNonce.publicNonce)
```

**적용 위치 6+1곳**:

| 파일 | 위치 | 목적 |
| --- | --- | --- |
| `channel/Commitments.kt` | `sign()` (~213) | remote commit initial |
| `channel/Commitments.kt` | `sendCommit()` (~606) | subsequent commit |
| `channel/Helpers.kt` | `ClosingComplete` (~331) | close tx initial |
| `channel/Helpers.kt` | `ClosingSig` (~400) | close tx counterparty |
| `channel/InteractiveTx.kt` | `SharedFundingInput.sign()` (~46) | **splice tx (핵심)** |
| `channel/InteractiveTx.kt` | first commit (~1262) | 새 채널 첫 commit |
| `transactions/Transactions.kt` | `checkRemotePartialSignature` | 검증 측 정렬 |
| `transactions/Transactions.kt` | `aggregateSigs` (musig2 변종) | 같은 패턴 |

### 7. `NodeParams.kt:259` — toRemoteDelayBlocks=144

```kotlin
// DEV-BYPASS for BitEver test chain: 2016 blocks ~= 14 days is too long.
toRemoteDelayBlocks = CltvExpiryDelta(144),
```

다음 채널부터 force-close 회수 144 블록 (~24h).

## 의존성 + 빌드

```bash
./gradlew :lightning-kmp-core:publishToMavenLocal
# ~3분
# 산출: ~/.m2/repository/fr/acinq/lightning/lightning-kmp-core-jvm/1.11.5-DEBUG/
```

Phoenix Android는 이걸 Maven 의존성으로 사용.

## 검증

폰 시작 직후 로그:
- `using minimum=… as fallback` 또는 `on-chain fees: …` (패치 1 검증)
- `peer is active`
- 채널 생성: `built local commit number=0 …`
- 송금: `received OnionMessage` + `paymentId:xxx received valid invoice`
- Force close: `(force) closing channel=…` + `built local commit`
- OTF 수신: `received WillAddHtlc(amount=…)` + (P13 paymentTypes 광고 받음) → `OpenDualFundedChannel`

## 상세 가이드

- 아키텍처: `LightningEver_Architecture.md`
- 완전 재구현 가이드: `260517_LN_FIN.md`

## 보안 경고

이 fork는 BitEver test chain 전용. 실제 비트코인 mainnet에서 사용하면 안 됨 (musig2 검증 BYPASS, channel reserve BYPASS 등 Eclair LSP 측 우회와 짝).
