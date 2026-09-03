# LightningEver KMP Library (BitEver) - SAVE2_fee Branch

## 개요
Phoenix 앱의 Lightning 프로토콜 라이브러리. Taproot Musig2 서명 검증을 개발/테스트 목적으로 우회.

## 클론 및 Maven Local 퍼블리시

```bash
git clone -b SAVE2_fee https://github.com/makewalletfirst/LightningEver-bitever-eclair-kmp.git
cd LightningEver-bitever-eclair-kmp

# 버전 확인 (1.11.5-DEBUG 여야 함)
grep version gradle.properties

# Maven Local에 퍼블리시 (약 2분)
./gradlew :lightning-kmp-core:publishJvmPublicationToMavenLocal \
          :lightning-kmp-core:publishKotlinMultiplatformPublicationToMavenLocal

# Phoenix 빌드 전에 반드시 이 단계 먼저 실행할 것
```

## 핵심 수정 파일

| 파일 | 수정 내용 |
|------|----------|
| `modules/core/src/commonMain/kotlin/fr/acinq/lightning/channel/Commitments.kt` | Commit sig + HTLC sig bypass for SimpleTaprootChannels |
| `gradle.properties` | version=1.11.5-DEBUG |

## 수정 위치 (Commitments.kt)

- **L133-148**: `InvalidCommitmentSignature` — SimpleTaprootChannels에서 skip
- **L150-158**: `InvalidHtlcSignature` — SimpleTaprootChannels에서 skip

## 원복 방법
```bash
git checkout SAVE2_fee
./gradlew :lightning-kmp-core:publishJvmPublicationToMavenLocal \
          :lightning-kmp-core:publishKotlinMultiplatformPublicationToMavenLocal
# 이후 Phoenix 재빌드
```
