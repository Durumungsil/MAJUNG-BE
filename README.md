# MAJUNG-BE

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
