# LightningEver-bitever-eclair-kmp (BitEver fork)

ACINQ `lightning-kmp` v1.11.5의 BitEver 체인 전용 패치 fork. 원본 README는 `README.md` 참고.

## 브랜치

- **`260517`** — 2026-05-17 안정판. 외부 L1 swap-in 자동 채널, Bolt12 trampoline 송금, mutual/force close 모두 동작 확인.

## 주요 변경점 (vs upstream)

| 파일 | 변경 사유 |
| --- | --- |
| `modules/core/src/commonMain/kotlin/fr/acinq/lightning/io/Peer.kt` | BitEver electrs가 `estimatefee`를 항상 에러로 반환 → `onChainFeeratesFlow`가 null 유지 → ForceClose/MutualClose 처리 시 `currentOnChainFeerates()` suspend 무한 대기. **`FeeratePerKw.MinimumFeeratePerKw`(253 sat/kw)로 fallback**. |
| `modules/core/src/commonMain/kotlin/fr/acinq/lightning/channel/states/WaitForInit.kt` | 앱 재시작 시 `Negotiating` 상태가 복원되어도 `publishedClosingTxs`가 재publish/재watch되지 않아 mutual close가 영원히 멈춤. **재시작 시 closing tx들을 다시 publish + watch** 액션 추가. |
| `modules/core/src/commonMain/kotlin/fr/acinq/lightning/channel/Commitments.kt` | simple_taproot_channels의 `fullySignedCommitTx`에서 musig2 `publicNonces`가 `sortedKeys` 순서와 맞아야 함. 기본은 `[local, remote]` 고정 → 정렬 후 remote가 first면 서명 검증 실패. **`Scripts.sort()` 결과에 맞춰 nonce도 정렬**. |
| `modules/core/src/commonMain/kotlin/fr/acinq/lightning/transactions/Transactions.kt` | `aggregateSigs`도 같은 이유로 정렬 필요. **partialSigs와 nonces 모두 sortedKeys 순서에 맞춰 정렬**. |
| `gradle.properties` | version `1.11.5-DEBUG`로 변경 (Phoenix 빌드 시 mavenLocal 우선 참조). |

상세 해설: `260517_LN_3.md` (Phoenix 레포에 동봉).

## 빌드법

```bash
git clone https://github.com/makewalletfirst/LightningEver-bitever-eclair-kmp.git lightning-kmp
cd lightning-kmp
git checkout 260517

./gradlew :lightning-kmp-core:publishToMavenLocal
# ~3분 소요
# 산출물: ~/.m2/repository/fr/acinq/lightning/lightning-kmp-core/1.11.5-DEBUG/
```

이 결과는 Phoenix Android 빌드에서 자동으로 참조됩니다 (Phoenix의 `phoenix-shared/build.gradle.kts`에 `mavenLocal()` 추가 필요).

## 디버그 모드 의도된 동작

부팅 직후 Peer가 Electrum 연결 후 `getFeerates()` 결과가 null이면 로그에 한 줄 찍힙니다:

```
ERROR cannot retrieve feerates! using minimum=253 sat/kw as fallback
```

이 로그가 보이면 fallback이 활성화된 상태 — ForceClose/MutualClose 모두 정상 동작합니다.

## 호환성

- BitEver chain hash: `6fe28c0ab6f1b372c1a6a246ae63f74f931e8365e15a089c68d6190000000000`
- simple_taproot_channels (musig2) 활성
- `option_dual_fund` 활성

## 라이선스

ACINQ Public Software License (upstream과 동일)
