# BitEver lightning-kmp — 260517_SEND 브랜치

ACINQ lightning-kmp 1.11.5를 BitEver L1 체인용으로 fork한 빌드입니다. Phoenix Android에서 사용.

## 달성 마일스톤

1. ✅ 외부 L1 입금 → 자동 채널 생성
2. ✅ Bolt12 송금 (양측 채널 보유)
3. ✅ 지정 주소 mutual close
4. ✅ Force close + 144 블록 CSV 회수
5. ✅ **채널 없는 폰에게 Bolt12 송금 → 자동 채널 생성**

## 주요 변경점 (vs upstream 1.11.5)

### `io/Peer.kt::updateFeerates()`

BitEver electrs는 estimatefee를 지원하지 않음. `onChainFeeratesFlow.value`가 null 유지 → force close가 영원히 suspend.

```kotlin
client.getFeerates()?.let { ... } ?: run {
    if (onChainFeeratesFlow.value == null) {
        val minFee = FeeratePerKw.MinimumFeeratePerKw
        onChainFeeratesFlow.value = OnChainFeerates(minFee, minFee, minFee, minFee)
    }
}
```

### `channel/states/WaitForInit.kt`

`Negotiating` 복원 시 `publishedClosingTxs` re-publish + watch. 미적용 시 앱 재시작 후 mutual close가 `syncing(negotiating)`에서 멈춤.

### `NodeParams.kt:259`

```kotlin
toRemoteDelayBlocks = CltvExpiryDelta(144),  // 기본 2016
```

force close 후 CSV 단축. 다음 채널부터 적용.

### `channel/Commitments.kt` (musig2 nonce 정렬, 2곳)

`Scripts.sort()`로 funding pubkey 정렬 후 `publicNonces[i]`도 동일 순서로 정렬. 미적용 시 commit_sig 검증 실패.

```kotlin
val sortedFundingKeys = Scripts.sort(listOf(fundingKey.publicKey(), remoteFundingPubkey))
val orderedNonces = if (sortedFundingKeys.first() == fundingKey.publicKey())
    listOf(localNonce.publicNonce, remoteNonce)
else
    listOf(remoteNonce, localNonce.publicNonce)
```

적용:
- `sign()` (remote commit initial)
- `sendCommit()` (subsequent commit)

### `channel/Helpers.kt` (musig2 nonce 정렬, 2곳)

`ClosingComplete` / `ClosingSig` 두 분기 모두 동일 패턴. 미적용 시 close tx 서명 검증 실패.

### `channel/InteractiveTx.kt` (musig2 nonce 정렬, 2곳)

`sign()` (splice tx) + 첫 commit tx. **splice 측이 가장 중요**. 미적용 시 request liquidity / on-the-fly splice 즉시 실패 (`invalid funding signature`).

### `transactions/Transactions.kt::checkRemotePartialSignature`

검증 측에서도 sort 후 ordered nonces 사용:

```kotlin
val sortedKeys = Scripts.sort(listOf(localFundingPubKey, remoteFundingPubKey))
val orderedNonces = if (sortedKeys.first() == localFundingPubKey)
    listOf(localNonce, remoteSig.nonce)
else
    listOf(remoteSig.nonce, localNonce)
return Musig2.verify(..., sortedKeys, orderedNonces, ...)
```

미적용 시 상대편이 보낸 partial sig 검증에서 invalid signature.

## 빌드

```bash
./gradlew :lightning-kmp-core:publishToMavenLocal
# ~3분. ~/.m2 아래 1.11.5-DEBUG 산출
```

이후 Phoenix Android는 `~/.m2`에서 이 버전을 가져다 씀.

## 검증

폰 시작 직후 로그:
- `using minimum=… as fallback` 또는 `on-chain fees: …` (P1)
- `peer is active` (Peer 정상 시작)

기능별 로그:
- 채널 생성: `built local commit number=0 …`
- 송금: `received OnionMessage` + `paymentId:xxx received valid invoice`
- Force close: `(force) closing channel=…` 후 `built local commit`
- OTF 채널 생성: `received WillAddHtlc(amount=…)` + `accepting on-the-fly funding`

## 알려진 미해결 이슈

- 외부 L1 송금 실패: `Aborted by peer [previous tx missing from tx_add_input (serial_id=000000000000)]`
- 폰 A request liquidity 실패 (폰 B는 가끔 성공)
- 일부 지정 mutual close 시나리오에서 negotiating 정지

세 이슈 모두 interactive-tx 협상 시 `tx_add_input` 누락 추정. LN_5에서 해결 예정.

## 상세 가이드

전체 재구현 가이드: `260517_LN_4.md` (별도 위치).
