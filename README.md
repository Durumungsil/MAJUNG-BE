# MAJUNG-BE

## 로컬 실행 (도커)

JDK 없이 도커만 있으면 API 서버를 띄울 수 있습니다. 프론트 개발 중 API 연결용입니다.

**기존 main DB를 처음 전환한다면 서버 실행 전에 [배포 안내](docs/google-places-deployment.md)의 백업·마이그레이션을 먼저 적용합니다.** 기본 DDL 모드는 validate이며 스키마를 자동 변경하지 않습니다.

1. 저장소를 클론하고 `.env.example`을 `.env`로 복사한 뒤 값을 채웁니다. 실제 값(DB·JWT·구글·Gemini)은 팀 비공개 채널로 받고, `.env`는 깃에 올리지 않습니다.
2. 서버를 띄웁니다. 처음에는 의존성을 받느라 몇 분 걸립니다.

   ```sh
   docker compose up --build      # 로그를 보면서 실행
   docker compose up -d --build   # 백그라운드 실행
   docker compose logs -f api     # 백그라운드일 때 로그 보기
   docker compose down            # 종료
   ```
3. `http://localhost:8080`으로 호출합니다. 프론트가 8080을 쓰면 `.env`에 `API_PORT=8081`처럼 다른 포트를 지정합니다.

- CORS는 `http://localhost:3000`, `5173`, `8000`, `8001`, `8080`을 허용합니다. 다른 주소면 `.env`의 `CORS_ALLOWED_ORIGINS`에 콤마로 적습니다(적으면 기본값 대신 그 값만 씁니다).
- 구글 로그인: 프론트가 쓰는 구글 클라이언트 ID가 `GOOGLE_CLIENT_IDS`에 있어야 하고, 구글 콘솔의 승인된 JavaScript 원본에 프론트 로컬 주소가 등록돼 있어야 합니다.
- DB는 팀이 함께 쓰는 Supabase라 다른 사람이 만든 테스트 데이터가 보일 수 있습니다.
- `build.gradle`의 Java toolchain 버전을 바꿨다면 `.env`의 `JAVA_VERSION`도 같은 값으로 맞춥니다(기본 21).
- 코드를 바꾼 뒤에는 `--build`를 붙여 다시 띄워야 반영됩니다.

## 현재 k3s · Argo CD · Vault 배포

배포 manifest는 형제 저장소 `../MAJUNG-DEPLOY`에서 관리합니다. 앱은 `durumungsil` namespace에 배포하며, 이 namespace와 그 안의 `vault-reader` ServiceAccount·RBAC·ExternalSecret·라우팅을 MAJUNG-DEPLOY가 관리합니다. Vault·ESO·Traefik 설치, 모든 앱이 함께 쓰는 `vault` ClusterSecretStore, Vault의 Kubernetes 인증 role·policy는 인프라 소유입니다. ESO는 이 namespace의 `vault-reader`로 Vault에 로그인하고, Vault는 그 로그인에 `secret/durumungsil/*`와 모든 앱 공용 `secret/global/*`만 허용합니다. Vault root 토큰·unseal 키·앱 비밀값을 Git 또는 GitHub Actions에 넣지 않습니다.

### 1. Vault 값 등록

VPN에서 `https://10.42.1.0:8002`에 접속해 KV v2 엔진 `secret`에 다음 두 경로를 만듭니다. 앱 전용 값은 반드시 namespace 이름과 같은 `durumungsil/` 아래에, 여러 앱이 함께 쓰는 값은 `global/` 아래에 두며, 그 밖의 경로는 읽을 수 없습니다.

| 경로 | 필드 |
| --- | --- |
| `durumungsil/backend` | 필수: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `GOOGLE_CLIENT_IDS` |
| `global/github` | 공용. `username`: GHCR 계정명, `token`: 해당 패키지의 `read:packages` 권한이 있는 pull 전용 PAT(classic) |

`DB_URL`은 JDBC URL이며 JWT 키는 32바이트 이상입니다. 선택 필드 `GEMINI_API_KEY`, `GOOGLE_PLACES_API_KEY`, `GOOGLE_PLACES_CURSOR_SECRET`도 `durumungsil/backend` 경로에 저장할 수 있습니다. 생략하면 기존 앱 기본값을 사용하며, Places 키가 없으면 Places 기능은 사용할 수 없습니다. `GOOGLE_CLIENT_IDS`는 실제 클라이언트 ID들을 콤마로 구분합니다.

일반 설정은 `MAJUNG-DEPLOY/kubernetes/kustomization.yaml`의 ConfigMap으로 관리합니다. `PORT=8080`, `JPA_DDL_AUTO=validate`, 작은 DB 풀을 유지합니다. 실제 프론트 주소를 쓰기 전 `CORS_ALLOWED_ORIGINS`를 명시하고, 유료 Gemini 사용 확인 전 `GEMINI_PAID_TIER_CONFIRMED=false`를 유지합니다. `durumungsil/backend`에는 위 앱 필드만 넣어 ConfigMap의 일반 설정을 덮어쓰지 않게 합니다. 개발용 `.env`는 자동 업로드되지 않습니다.

`external-secrets.yaml`이 앱 환경변수용 `durumungsil-api-secret`과 이미지 다운로드용 `durumungsil-ghcr-pull-secret`을 생성합니다. GHCR 토큰은 JSON template으로 dockerconfigjson Secret이 되며 앱 컨테이너의 환경변수에는 들어가지 않습니다. GHCR pull에는 `read:packages` 권한이 있는 PAT(classic)을 사용합니다. Fine-grained PAT로 Vault 동기화가 성공해도 GHCR 패키지 접근에서 403이 발생할 수 있습니다. private 패키지라면 토큰 계정에 패키지 읽기 권한과 필요한 조직 SSO 승인이 있어야 합니다. Public 패키지로 바꾸고 pull 인증을 제거하려면 Deployment의 `imagePullSecrets`와 해당 ExternalSecret을 함께 제거합니다.

`could not get secret data from provider`가 발생하면 ClusterSecretStore의 Ready만으로 성공을 판단하지 말고 각 ExternalSecret의 Ready를 확인합니다. 앱은 `durumungsil/backend`, registry는 공용 `global/github`를 읽습니다. registry 경로가 없으면 앱 Secret이 정상이어도 pull Secret 생성에 실패하고 첫 배포의 sync-wave가 막힙니다. 실제 값은 Vault UI에만 입력하고 토큰을 Git·채팅에 붙여넣지 마세요. manifest의 Vault 경로를 수정했다면 배포 저장소에 커밋·푸시한 뒤 Argo CD에서 Sync/Retry해야 하며, 클러스터만 임시 수정하면 다음 Git 동기화에서 이전 경로로 돌아갈 수 있습니다.

### 2. GitHub Actions → 배포 저장소

배포 저장소는 private `https://github.com/parafara/durumungsil-backend-deploy.git`이며 `main` 브랜치의 manifest를 사용합니다. 로컬 `MAJUNG-DEPLOY`의 `origin`도 이 주소로 등록합니다. 다른 저장소를 사용하려면 Actions의 `DEPLOY_REPOSITORY` 변수와 Argo CD UI의 Application Source Repository URL·저장소 인증을 함께 수정합니다.

BE 저장소의 Actions 설정:

| 구분 | 이름 | 용도 |
| --- | --- | --- |
| Secret | `DEPLOY_REPO_TOKEN` | `parafara/durumungsil-backend-deploy` 한 곳의 Contents 읽기/쓰기 권한만 가진 fine-grained 토큰. 저장소가 `parafara` 계정 소유이므로 그 계정에서 발급 |
| Variable, 선택 | `DEPLOY_REPOSITORY` | 기본 `parafara/durumungsil-backend-deploy` |
| Variable, 선택 | `DEPLOY_REF` | 기본 `main`; 변경 시 Argo CD `targetRevision`도 맞춤 |

GHCR push는 workflow의 `GITHUB_TOKEN`과 `packages: write` 권한을 사용합니다. 최초 push 후 패키지 가시성·접근 권한을 확인하세요. `KUBE_CONFIG`, `INFRA_REPO_TOKEN`, `INFRA_REPOSITORY`는 이 workflow에서 사용하지 않습니다. GitHub-hosted runner를 VPN에 연결하거나 내부 Kubernetes API를 인터넷에 열 필요도 없습니다.

`main` push → BE의 로컬 `Dockerfile`로 amd64/arm64 이미지 빌드 → GHCR push → 배포 저장소 `kubernetes/deployment.yaml`을 `image@sha256:…`로 갱신·commit/push합니다. 같은 digest이면 commit하지 않습니다. 배포 저장소 branch protection이 봇의 직접 push를 막으면 해당 토큰의 제한된 봇 push를 허용하거나 PR 기반 운영으로 변경해야 합니다. 앱 비밀값은 빌드 인자나 이미지에 넣지 않습니다.

### 3. Argo CD 등록과 첫 실행

이미지가 게시되고 배포 저장소에 digest가 기록된 뒤 Argo CD UI(`https://10.42.1.0:8001`)의 New App에서 아래 값으로 등록합니다. Application 정의는 UI에서 관리하며 배포 저장소에는 별도 `argocd/` 디렉토리를 두지 않습니다. 배포 저장소가 private이므로 Argo CD는 그 저장소의 read-only deploy key(인프라 README 참고)로 읽습니다. 이 인증은 CI의 배포 저장소 쓰기 토큰과 별개입니다.

| 항목 | 값 |
| --- | --- |
| Application Name | `durumungsil` |
| Project | `default` |
| Repository URL | `git@github.com:parafara/durumungsil-backend-deploy.git` |
| Revision | `main` |
| Path | `kubernetes` |
| Destination Cluster | `https://kubernetes.default.svc` |
| Destination Namespace | `durumungsil` |
| Sync Policy | `Automatic`; 자동 삭제·설정 복구를 원하면 `Prune`, `Self Heal` 활성화 |

Application은 `main`의 `kubernetes`를 감시해 현재 클러스터의 `durumungsil`에 자동 동기화합니다. Secret 리소스는 먼저 제출되지만 sync-wave만으로 Vault 최신 값의 동기화 완료가 보장되지는 않습니다. 최초에는 아래 상태가 Ready인지 확인합니다.

```bash
kubectl get clustersecretstore vault
kubectl -n durumungsil get externalsecret durumungsil-api-secret durumungsil-ghcr-pull-secret
kubectl -n durumungsil rollout status deployment/durumungsil-api --timeout=600s
kubectl -n durumungsil port-forward service/durumungsil-api 8080:80
```

앱 Service는 ClusterIP를 유지하고 인프라의 단일 Traefik 컨트롤러가 `durumungsil/durumungsil-api:80`으로 전달합니다. 공개 경로는 `https://durumungsil.parafara.com/` → Cloudflare Tunnel(CT 1000) → `http://10.42.1.0:8000` → Traefik → API입니다. API는 `web` entryPoint의 `IngressRoute`와 원본 IP allowlist middleware를 사용하고, 같은 컨트롤러가 Argo CD 8001·Vault 8002의 TLS passthrough도 처리합니다. 관리자 port-forward의 `http://localhost:8080`으로도 확인할 수 있습니다. 현재 probe는 인증 없는 HTTP health endpoint가 없어 TCP 가용성만 검사하며 DB/API 기능 정상까지 보장하지 않습니다.

운영 DB migration은 기존 [Places 배포 절차](docs/google-places-deployment.md)를 먼저 확인해야 하며 이 설정은 자동 migration을 실행하지 않습니다. Vault 값만 바꾸면 ESO가 약 1분 뒤 Secret을 갱신하고, 환경변수 반영에는 해당 Deployment의 rollout restart가 필요합니다. `refreshTime` 갱신과 Ready를 확인한 뒤 재시작하세요. 새 이미지 배포 때도 ESO 조회와 Argo CD 배포는 비동기이므로, Secret을 함께 변경했다면 동기화 완료를 먼저 확인합니다.

설정 검증: Kustomize 렌더링과 현재 클러스터의 server dry-run, 실제 Vault·ESO를 통한 임시 앱 Secret/registry Secret 동기화, Deployment와 같은 envFrom·보안 설정을 사용한 임시 Pod의 환경변수 주입을 확인했습니다. workflow의 실제 shell도 임시 Git 원격 저장소에서 digest 변경·push와 동일 digest 재실행을 검증했습니다. 검증용 리소스·비밀값은 삭제했습니다. 실제 GHCR 인증 pull과 Spring 앱 배포 후 Pod `1/1 Running`, Ready endpoint, Argo CD `Synced / Healthy`를 확인했습니다. 공개 HTTPS 요청은 CT 1000을 거쳐 API에 도달하며, 인증 없는 `/` 요청의 JSON 401과 본문 없는 Google 로그인 요청의 400을 확인했습니다. GitHub-hosted 빌드, 실제 Google 로그인, 인증된 업무 API 및 DB CRUD는 이 검증에서 실행하지 않았습니다.

## 인증

Google ID 토큰을 `POST /api/v1/auth/google`에 전달해 access/refresh 토큰을 발급받습니다. 이후 API에는 `Authorization: Bearer {access_token}`을 전달합니다. `X-User-Id`로 사용자를 지정할 수 없습니다. Google 클라이언트 ID와 JWT 서명 키는 `GOOGLE_CLIENT_IDS`, `JWT_SECRET` 환경변수로 설정합니다. 서명 키는 32바이트 이상이며 토큰 유효 기간은 1초 이상이어야 합니다.

모험모드 브랜치를 함께 적용하면 모험·미션 API에도 인증과 그룹 소속 검사를 적용합니다. `AdventureAuthIntegrationTests`는 모험 컨트롤러가 있을 때 로그인 사용자·그룹 권한·미션 시작과 완료를 함께 검증합니다.

## 패키지 구조

기능(도메인)별로 패키지를 나누고, 그 안을 계층별로 나눕니다. 여러 도메인이 함께 쓰는 설정·보안·예외·응답·유틸은 `global`에 둡니다.

```
durumungsil.majung
├── domain
│   ├── auth        구글 로그인, 토큰
│   ├── user        내 정보
│   ├── group       그룹, 멤버, 초대 (GroupReader: 그룹 소속·진행 중인 여행 조회)
│   ├── trip        여행 회차, 최소 조건
│   ├── preference  취향 입력·통합
│   ├── place       Google Places 유형·좌표 캐시
│   ├── store       모험용 상가 장소
│   ├── story       스토리모드: 여행지 추천·선택, 타임라인
│   ├── adventure   모험모드: 커리큘럼, 미션
│   └── roulette    익스트림모드: 최소 조건, 여행지·식당 룰렛, 결과 조회
│       └── {controller, service, repository, entity, dto/request, dto/response}
└── global          config, security, exception, response, util, ai
```

- 엔티티 하나에 저장소(Repository)는 하나만 둡니다. 다른 도메인의 엔티티·저장소·서비스는 import 해서 씁니다.
- 실패 응답은 `BusinessException(ErrorCode.XXX)`로 던지고, 성공 응답은 `ApiResponse.success(...)`로 감쌉니다.

## Commit Convention

### 형식

```
<type>: <subject>

<body>   # 선택
```

- `type`은 영어 소문자, `subject`는 한글로 간결하게 작성합니다.
- `subject` 끝에 마침표를 붙이지 않습니다.
- 한 커밋에는 하나의 변경 의도만 담습니다.
- `body`는 필요할 때만, **무엇을 / 왜** 바꿨는지 작성합니다.

### Type

| Type | 설명 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변화 없는 코드 구조 개선 |
| `style` | 코드 포맷팅, 세미콜론 누락 등 로직 변경 없는 수정 |
| `docs` | 문서 수정 (README 등) |
| `test` | 테스트 코드 추가 및 수정 |
| `chore` | 빌드 설정, 패키지, `.gitignore` 등 기타 작업 |
| `rename` | 파일·폴더명 변경 또는 이동 |
| `remove` | 파일 삭제 |

### 예시

```
feat: store 엔티티 생성
fix: db 연결 오류 해결
chore: gitignore 설정
refactor: 상권 조회 로직 서비스 계층으로 분리
```

## Branch Convention

```
<type>/<작업-내용>
```

- 예: `feature/store-entity`, `fix/db-connection`, `refactor/area-service`
- `main` 브랜치에 직접 push 하지 않고, PR을 통해 머지합니다.

## 모험모드

[API·동작 정책·설정·검증 방법](docs/adventure-mode.md)을 참고합니다.

## 스토리모드

[API·Places 추천·일정 계약](docs/story-mode.md)를 참고합니다.

## 익스트림모드

[API·Places ID 추첨 정책·검증 방법](docs/extreme-mode.md)을 참고합니다.

## Google Places 전환 배포

[환경변수·DB 마이그레이션·운영 확인](docs/google-places-deployment.md)을 배포 전에 확인합니다. 스토리·극한은 Places, 모험은 Stores를 사용합니다.
