# MAJUNG-BE

## 인증

Google ID 토큰을 `POST /api/v1/auth/google`에 전달해 access/refresh 토큰을 발급받습니다. 이후 API에는 `Authorization: Bearer {access_token}`을 전달합니다. `X-User-Id`로 사용자를 지정할 수 없습니다. Google 클라이언트 ID와 JWT 서명 키는 `GOOGLE_CLIENT_IDS`, `JWT_SECRET` 환경변수로 설정합니다. 서명 키는 32바이트 이상이며 토큰 유효 기간은 1초 이상이어야 합니다.

모험모드 브랜치를 함께 적용하면 모험·미션 API에도 인증과 그룹 소속 검사를 적용합니다. `AdventureAuthIntegrationTests`는 모험 컨트롤러가 있을 때 로그인 사용자·그룹 권한·미션 시작과 완료를 함께 검증합니다.

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
