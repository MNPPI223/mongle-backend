# Mongle Backend

정서 케어 서비스 "몽글"의 백엔드. [`API_명세서_v1`](https://claude.ai/artifact/VbwGAqiJPA7qUf2U26G7Mp) 기준으로 구현한다 — 엔드포인트별 요청/응답, ERD, 에러 포맷은 전부 그 문서가 원본이고 이 README는 "그래서 로컬에서 어떻게 돌리고, 둘이 어떻게 나눠서 짜나"만 다룬다.

## 스택

Java 21 · Spring Boot 3.5 · MySQL 8.4(Flyway로 스키마 관리) · Gemini 3.5 Flash · Spring Security(JWT, 로그인 없음) · Maven

## 로컬 세팅

**1. JDK 21 확인.** 이 Mac 기본 `java`는 17이다. 21은 따로 설치돼 있다:
```bash
brew install openjdk@21   # 이미 설치돼 있으면 스킵
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
```
매번 치기 귀찮으면 셸 프로필에 넣어두거나, 이 프로젝트 안에서만 쓰고 싶으면 `direnv` 같은 걸로 디렉터리 스코프로 묶어도 된다.

**2. `.env` 채우기.**
```bash
cp .env.example .env
openssl rand -base64 48   # 이 값을 JWT_SECRET에
```
`GEMINI_API_KEY`는 Google AI Studio(aistudio.google.com)에서 발급 — 비워둬도 서버는 뜨고, AI 쪽 엔드포인트는 전부 안전한 폴백 문구로 응답한다(죽지 않는다).

**3. MySQL 띄우고 서버 실행.**
```bash
docker compose up -d mysql     # 호스트 포트 3308
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw spring-boot:run
# http://localhost:8081/swagger-ui.html 에서 바로 테스트 가능 (Authorize에 /auth/anonymous로 받은 토큰 입력)
```
Docker 없이 전부 컨테이너로 올리고 싶으면 `docker compose up --build`.

**4. 테스트.**
```bash
./mvnw test   # Docker 없이 H2로 돈다. 17개 전부 통과하는 게 기준.
```

## 모듈 = 작업 분담 (API_명세서_v1 8장 그대로)

```
common/      — 에러 포맷, JWT, 보안 설정 (이미 다 돼 있음, 건드릴 일 적음)
auth/        — POST /auth/anonymous                              } 백엔드 A
session/     — POST /sessions, POST /sessions/{id}/finish         } 백엔드 A
memory/      — GET/PATCH/DELETE /memories, preview/approve        } 백엔드 A
preference/  — GET/PATCH /preferences                             } 백엔드 A
reflection/  — POST /sessions/{id}/reflections, Gemini 연동        } 백엔드 B + 프론트
```

`memory`와 `reflection`이 만나는 지점(`POST /memories/preview`가 AI 요약을 쓰는 부분)은 `MemorySummarizer`라는 인터페이스로 끊어놨다 — `memory` 모듈은 이 인터페이스만 알고, `reflection.GeminiMemorySummarizer`가 실제 구현을 준다. 백엔드 A가 memory를 짤 때 B의 Gemini 코드가 끝나있지 않아도 컴파일·테스트가 된다 (이미 그렇게 테스트돼 있음).

## 명세서 대비 의도적으로 보강/변경한 지점

문서 그대로 안 하고 구현하면서 바꾼 것들. "왜 명세서랑 다르지?" 싶으면 여기부터 봐라.

| 명세서 내용 | 실제 구현 | 왜 |
|---|---|---|
| ERD의 `CHAR(36)` | `VARCHAR(36)`로 변경 | H2(테스트)가 Hibernate 기본 매핑과 CHAR를 다른 타입으로 봐서 스키마 검증이 깨짐. UUID엔 실질적 차이 없음 |
| "Gemini 실패 시 폴백 + `riskDetected:false`" | 폴백 문구는 그대로, `riskDetected`는 **키워드 판정 결과를 유지** | 위험 키워드는 Gemini 호출 *전에* 판정된다. 업스트림이 죽었다고 위험 신호까지 숨기면 위기 상황에서 더 위험해진다. `ReflectionControllerTest`에 이 케이스 테스트 있음 |
| 500 에러 코드 `AI_UPSTREAM_ERROR` 하나 | `AI_UPSTREAM_ERROR`(Gemini 실패 전용) + `INTERNAL_ERROR`(그 외 예상 못 한 500) 분리 | reflections는 Gemini가 죽어도 500을 사용자에게 보여주지 않으므로, `AI_UPSTREAM_ERROR`가 실제로 클라이언트에 나가는 일은 정상 흐름에선 없다. 둘을 분리해야 로그에서 "진짜 버그"와 "AI 업스트림 이슈"를 구분할 수 있다 |
| "프론트엔드 스택 미정" (9장 블로커) | 백엔드가 몰라도 되게 설계 | 인증이 쿠키가 아니라 Bearer 토큰이라 CORS는 `ALLOWED_ORIGINS` 환경변수 하나로만 열면 된다. 웹이든 네이티브 앱이든 백엔드 코드는 안 바뀐다 |
| 서버 포트 | 8081 | 이 Mac에 Coffeul 등 다른 프로젝트가 로컬 8080을 쓰고 있어서 겹치지 않게 뗐다 |
| Gemini 타임아웃 "3초 기준" | `8000ms`로 변경 | 2026-11-09 실제 키로 5회 호출해보니 전부 4.7~5.1초 걸림 — 3초면 **매번** 폴백으로 떨어져서 사실상 AI를 안 쓰는 것과 같았다. 실측 기반으로 올림 |
| 위기 응답 톤 | `SYSTEM_INSTRUCTION`에 "전화번호·기관명 직접 언급 금지" 규칙 추가 | 2026-11-09 실제 키로 위험 키워드 케이스 테스트하다가, Gemini가 자유 텍스트 응답 안에 `riskResources`(0199)와 다른 번호(1393)를 스스로 지어내는 걸 발견함. 정확한 연락처는 구조화된 `riskResources` 필드가 전담하게 하고, 모델 응답은 번호를 아예 언급 못 하게 막음(재검증 3회 완료) |

## 아직 안 끝난 것 (명세서 9장 + 이번 세팅에서 추가로 발견한 것)

- ~~실 MySQL 검증~~ → **완료 (2026-11-09)**: `docker compose up -d --build backend`로 MySQL 8.4 컨테이너 위에서 Flyway 마이그레이션·Hibernate 스키마 검증·전 엔드포인트(auth→sessions→reflections→memories/preview)를 curl로 직접 확인함. H2가 아니라 실제 MySQL에서도 깨끗하게 돈다.
- ~~GEMINI_API_KEY 발급~~ → **완료 (2026-11-09)**: 실제 키로 공감대화·위험 케이스·기억 요약 전부 실호출 검증함. 타임아웃을 3초→8초로 올리고, 위기 응답에서 모델이 전화번호를 스스로 지어내던 문제를 프롬프트 규칙으로 막음(위 표 참고).
- **위험 키워드 리스트** — `application.yml`의 `mongle.risk.keywords`. 16개 초안에서 흔한 동의어·활용형(목매·번개탄·투신·극단적 선택·맞고·쫓아온다·따라온다 등) 8개를 보강해 24개로 늘림(2026-11-09). **보강 과정에서 실제 버그 하나 발견·수정함**: `RiskKeywordDetector`가 입력 텍스트는 공백을 지우고 비교하면서 키워드 쪽은 안 지워서, `"극단적 선택"`처럼 공백 든 키워드가 조용히 매칭 안 되고 있었다(키워드 쪽도 공백 제거하도록 수정, curl로 재검증 완료). 여전히 팀 검수 필요한 초안.
- **배포 환경** — 로컬 데모만 할지, 결선용으로 어딘가에 띄울지 아직 결정 안 됨. `Dockerfile`은 만들어뒀으니 정해지면 바로 올릴 수 있다.
- **단일 인스턴스 가정** — `SessionContextStore`(대화 컨텍스트, rate limit)가 서버 메모리에만 있다. 여러 대로 스케일 아웃하는 순간 이 클래스부터 Redis 등으로 바꿔야 한다.
- **안심콜 번호 재확인 필요 🔴** — 코드엔 디자인 프로토타입 기준 `0199`가 들어있는데, 2026-11-09 실제 Gemini 호출 중 모델이 스스로 `1393`을 언급한 적이 있다(지금은 프롬프트로 막아뒀지만, 모델이 "그 번호가 맞다"고 알고 있다는 뜻). 둘 중 뭐가 현재 맞는 번호인지 공식 출처로 확인 안 됨 — 배포 전 반드시 팀이 직접 확인할 것.

## Git 안전 규칙 (팀 레포라 특히 중요)

새 브랜치를 만들 때 `--no-track` 없이 만들면, push할 때 리뷰 없이 `main`에 바로 꽂힐 수 있다.
```bash
git checkout -b <브랜치명> origin/main --no-track
```
push하기 전엔 `git branch -vv`로 로컬 브랜치가 엉뚱한 원격 브랜치를 추적하고 있지 않은지 한 번 확인한다.
