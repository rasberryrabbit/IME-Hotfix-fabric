# 수정 내역

## 1.2.1 — MC 26.4 스냅샷 대응

### 배경

Minecraft 26.4에서 표지편집 시스템이 전면 재작성되고, 렌더링 메서드 시그니처가
변경되어 기존 1.2.0 빌드가 호환되지 않았다. 1.2.1은 26.4-snapshot-6 기준으로
믹스인을 재작성한 버전이다.

### 믹스인 변화

| 믹스인 | 1.2.0 (26.2/26.3) | 1.2.1 (26.4) | 변경 이유 |
| --- | --- | --- | --- |
| `EditBoxMixin` | 7개 @Inject | 5개 @Inject | `extractWidgetRenderState` 시그니처가 `(Graphics, int, int, float)` → `(Graphics)`로 축소 |
| `MultiLineEditBoxMixin` | 4개 @Inject + 1개 @Redirect | 동일 | `extractContents` 시그니처 동일하게 축소 |
| `MultilineTextFieldAccessor` | 유지 | 유지 | 변경 없음 |
| `BookEditScreenMixin` | 유지 | 유지 | 변경 없음 |
| `CreativeModeInventoryScreenMixin` | 유지 | 유지 | 변경 없음 |
| `AbstractSignEditScreenMixin` | 있음 | **삭제** | 26.4에서 `AbstractSignEditScreen`이 `SignLineEditBox[]`(EditBox 상속) 기반으로 전면 재작성됨. 기본 IME 기능은 `EditBoxMixin`이 `SignLineEditBox`에 자동 적용됨 |
| `TextFieldHelperAccessor` | 있음 | **삭제** | 26.4에서 `TextFieldHelper` 클래스 자체가 제거됨 |

**믹스인 수: 7개 → 5개**

### 코드 변경 상세

#### 1. `EditBoxMixin` — `extractWidgetRenderState` 시그니처

```java
// 1.2.0 (26.2/26.3)
@Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
private void imehotfix$spliceComposition(
    GuiGraphicsExtractor graphics, int mouseX, int mouseY,
    float partialTick, CallbackInfo callback) { ... }

// 1.2.1 (26.4)
@Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
private void imehotfix$spliceComposition(
    GuiGraphicsExtractor graphics, CallbackInfo callback) { ... }
```

RETURN @Inject도 동일하게 4파라미터 → 1파라미터로 축소.

원인: Mojang이 26.4에서 `extractWidgetRenderState`의 `mouseX`, `mouseY`,
`partialTick` 파라미터를 제거. 이 값들은 렌더링에 더 이상 사용되지 않음.

#### 2. `MultiLineEditBoxMixin` — `extractContents` 시그니처

```java
// 1.2.0 (26.2/26.3)
@Inject(method = "extractContents", at = @At("HEAD"))
private void imehotfix$spliceComposition(
    GuiGraphicsExtractor graphics, int mouseX, int mouseY,
    float partialTick, CallbackInfo callback) { ... }

// 1.2.1 (26.4)
@Inject(method = "extractContents", at = @At("HEAD"))
private void imehotfix$spliceComposition(
    GuiGraphicsExtractor graphics, CallbackInfo callback) { ... }
```

RETURN @Inject도 동일.

#### 3. `Mth.clamp` → `Math.clamp`

```java
// 1.2.0 (26.2/26.3)
int cursor = Mth.clamp(field.imehotfix$getCursor(), 0, current.length());

// 1.2.1 (26.4)
int cursor = Math.clamp(field.imehotfix$getCursor(), 0, current.length());
```

원인: Mojang이 26.4에서 `Mth.clamp(int, int, int)`를 제거하고
`java.lang.Math.clamp(long, int, int)` 사용으로 통일. `Math.clamp`는
Java 21부터 표준 라이브러리에 포함되어 있음.

`import net.minecraft.util.Mth;` 도 함께 제거.

#### 4. `AbstractSignEditScreenMixin` 삭제

26.3까지의 `AbstractSignEditScreen`은:

```java
private TextFieldHelper signField;
private final String[] messages;
private int line;
private IMEPreeditOverlay preeditOverlay;

public boolean preeditUpdated(PreeditEvent event) { ... }
public boolean charTyped(CharacterEvent event) { ... }
private void extractSignText(GuiGraphicsExtractor, Vector2f) { ... }
```

26.4에서는:

```java
private final SignLineEditBox[] signLines;  // EditBox 상속
private final SignText.Mutable text;

// preeditUpdated, charTyped, extractSignText — 모두 삭제
// IME 처리가 EditBox로 이동
```

`SignLineEditBox`는 `EditBox`를 상속하므로, `EditBoxMixin`이 자동 적용됨.
인라인 조합 표시, 포커스 이탈 시 조합 확정이 자동으로 동작.

**제외된 기능**: 표지판 자동 줄바꿈(`signAutoWrap`)과 오버플로우 하이라이트는
26.4의 새 API에 맞춰 별도 재구현이 필요하여 이 버전에서는 미포함.

#### 5. `TextFieldHelperAccessor` 삭제

```java
// 1.2.0 — 대상 클래스
@Mixin(TextFieldHelper.class)
public interface TextFieldHelperAccessor {
    @Accessor("cursorPos") int imehotfix$getCursorPos();
    @Accessor("selectionPos") int imehotfix$getSelectionPos();
    ...
}
```

26.4에서 `net.minecraft.client.gui.font.TextFieldHelper` 클래스 자체가
제거됨. 표지편집이 개별 `EditBox`(SignLineEditBox)로 분할되면서 더 이상
별도의 `TextFieldHelper`가 필요 없음.

### 빌드 설정 변화

| 항목 | 1.2.0 | 1.2.1 |
| --- | --- | --- |
| `gradle.properties` minecraft_version | `26.2` | `26.4-snapshot-6` |
| `fabric.mod.json` minecraft 의존성 | `>=26.2` | `>=26.4-snapshot-1` |
| 컴파일 대상 MC jar | 26.2 client.jar | 26.4-snapshot-6 client.jar |

`>=26.4-snapshot-1` 조건은 향후 26.4 정식 릴리스(`26.4`)도 만족함
(시맨틱 버전 비교에서 정식 릴리스 > 사전 릴리스).

### 호환성

| MC 버전 | 1.2.0 | 1.2.1 |
| --- | :---: | :---: |
| 26.2 | ✅ | ❌ |
| 26.3 | ✅ | ❌ |
| 26.4-snapshot-1 ~ 6 | ❌ | ✅ |
| 26.4 (정식) | ❌ | ✅ |

26.2/26.3 사용자는 1.2.0을, 26.4 사용자는 1.2.1을 사용.
