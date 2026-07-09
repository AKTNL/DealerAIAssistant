# Directory Structure

> How frontend code is organized in this project.

---

## Overview

The frontend is a Vue 3 SPA built with Vite. No router library is used -- the app toggles between `LoginView` and `ChatView` via a reactive `authVerified` flag in `App.vue`. The codebase uses JavaScript (not TypeScript).

Entry point: `frontend/index.html` loads `frontend/src/main.js`, which creates and mounts the Vue app.

---

## Directory Layout

```
frontend/
├── index.html                          # HTML entry, loads /src/main.js
├── package.json                        # Dependencies and scripts
├── vite.config.js                      # Vite + vitest config (single file)
└── src/
    ├── main.js                         # createApp(App).mount("#app")
    ├── App.vue                         # Root component -- auth gate + i18n
    ├── style.css                       # Global CSS custom properties + resets
    ├── views/                          # Page-level (top-level) components
    │   ├── ChatView.vue                # Main chat workspace (orchestrator)
    │   ├── LoginView.vue               # Authentication/login screen
    │   └── __tests__/                  # Co-located view tests
    │       ├── ChatView.spec.js
    │       └── LoginView.spec.js
    ├── components/                     # Reusable UI components
    │   ├── chat/                       # Chat-specific components
    │   │   ├── AnalysisChart.vue       # ECharts-based chart renderer
    │   │   ├── AssistantMessage.vue    # AI assistant message bubble (complex)
    │   │   ├── ChatInput.vue           # Textarea composer with send/stop
    │   │   ├── ChatMessageList.vue     # Scrollable message list container
    │   │   ├── FollowUpButtons.vue     # Follow-up question chips
    │   │   ├── MermaidChartAdapter.vue # Mermaid diagram renderer (lazy-loaded)
    │   │   ├── SystemMessage.vue       # Welcome / system-clear messages
    │   │   └── UserMessage.vue         # User message bubble
    │   ├── layout/                     # Layout/shell components
    │   │   ├── ExampleSidebar.vue      # Sidebar with sample prompts
    │   │   ├── ModelSettingsPanel.vue  # Model configuration panel
    │   │   └── TopNav.vue              # Top navigation bar
    │   └── __tests__/                  # Co-located component tests
    │       ├── AnalysisChart.spec.js
    │       ├── AssistantMessage.spec.js
    │       ├── ChatInput.spec.js
    │       ├── ChatMessageList.spec.js
    │       ├── ExampleSidebar.spec.js
    │       ├── FollowUpButtons.spec.js
    │       ├── MermaidChartAdapter.spec.js
    │       ├── MessageShell.spec.js
    │       ├── ModelSettingsPanel.spec.js
    │       └── WorkspaceChrome.spec.js
    ├── composables/                    # Vue 3 composables (stateful logic)
    │   ├── useAuth.js                  # Login / logout / session verification
    │   ├── useChat.js                  # Chat orchestration (streaming, messages, scroll)
    │   ├── useI18nState.js             # Locale toggle + dictionary lookup
    │   ├── useModelSettings.js         # Model config CRUD (localStorage-backed)
    │   ├── useSseParser.js             # SSE stream parser (ReadableStream consumer)
    │   └── __tests__/                  # Co-located composable tests
    │       ├── useAuth.spec.js
    │       ├── useChat.spec.js
    │       ├── useI18nState.spec.js
    │       ├── useModelSettings.spec.js
    │       └── useSseParser.spec.js
    ├── api/                            # API client layer (fetch wrappers)
    │   ├── client.js                   # Base: ApiError, buildUrl, requestJson
    │   ├── auth.js                     # POST /api/auth/verify
    │   ├── chat.js                     # DELETE /api/chat/:id, POST /api/chat/stream
    │   ├── modelConfig.js              # POST /api/model-config/test
    │   ├── sessionToken.js             # Auth session storage (sessionStorage)
    │   └── __tests__/                  # Co-located API tests
    │       ├── chat.spec.js
    │       ├── client.spec.js
    │       ├── modelConfig.spec.js
    │       └── sessionToken.spec.js
    ├── utils/                          # Pure utility functions (no Vue reactivity)
    │   ├── analysisCharts.js           # Chart data extraction from HTML
    │   ├── chat.js                     # createSessionId, normalizeAssistantPayload
    │   ├── markdown.js                 # MarkdownIt instance + renderMarkdownLite
    │   ├── modelErrors.js              # Error message classification
    │   ├── storage.js                  # localStorage/sessionStorage wrappers
    │   ├── thinkingSummary.js          # Thinking step summarization
    │   └── __tests__/                  # Co-located util tests
    │       ├── analysisCharts.spec.js
    │       ├── chat.spec.js
    │       ├── markdown.spec.js
    │       ├── modelErrors.spec.js
    │       ├── storage.spec.js
    │       └── thinkingSummary.spec.js
    ├── constants/                      # Exported constant objects
    │   ├── sidebarFlows.js             # zh/en sidebar flow definitions
    │   └── storageKeys.js              # localStorage/sessionStorage key names
    ├── i18n/                           # Internationalization
    │   └── messages.js                 # zh/en dictionary object
    ├── test/                           # Test infrastructure
    │   └── setup.js                    # Global test setup (auto-unmount)
    └── __tests__/                      # Root-level tests (config)
        ├── sidebarFlows.spec.js
        ├── styleTokens.spec.js
        └── viteConfig.spec.js
```

---

## Module Organization

- **`views/`**: Page-level components that act as orchestrators. They import composables, compose child components, and wire data flow. Only `ChatView` and `LoginView` exist -- there is no router, just conditional rendering in `App.vue`.
- **`components/chat/`**: All chat-message-related components (bubbles, input, message list).
- **`components/layout/`**: Structural shell components (navbar, sidebar, settings panel).
- **`composables/`**: Stateful, reactive logic extracted from components. Follows the `use<Feature>` naming convention.
- **`api/`**: Thin wrappers around `fetch()`. Each file corresponds to one backend resource area.
- **`utils/`**: Pure functions with no Vue reactivity dependency. Importable anywhere.
- **`constants/`**: Exported plain objects. No functions, no side effects.
- **`i18n/`**: A single `messages.js` file exporting `{ zh: {...}, en: {...} }`.

### Adding a New Feature

1. Create an API function in `frontend/src/api/<feature>.js` if a new backend endpoint is needed.
2. Create a composable in `frontend/src/composables/use<Feature>.js` for reactive state.
3. Create components in `frontend/src/components/<domain>/` as needed.
4. Add test files co-located in `__tests__/` directories.
5. If the feature has UI dictionary strings, add them to `frontend/src/i18n/messages.js`.

---

## Naming Conventions

| Artifact | Convention | Example |
|---|---|---|
| Vue components | PascalCase `.vue` files | `ChatInput.vue`, `TopNav.vue` |
| Composables | `use<Feature>.js` | `useAuth.js`, `useChat.js` |
| API modules | camelCase `.js` files, named exports | `api/chat.js` exports `streamChat`, `clearSession` |
| Utility modules | camelCase `.js` files, named exports | `utils/storage.js` exports `readStorageValue` |
| Constants modules | camelCase `.js`, `UPPER_SNAKE` exports | `storageKeys.js` exports `STORAGE_KEYS` |
| Test files | `<Name>.spec.js` in `__tests__/` directory | `ChatInput.spec.js` |
| i18n keys | camelCase | `welcomeTitle`, `loginButton` |

---

## Examples

- **Well-organized composable**: `frontend/src/composables/useAuth.js` -- small, focused, single responsibility.
- **Well-organized component**: `frontend/src/components/chat/ChatInput.vue` -- clear props/emits contract, exposes `focusComposer`.
- **Well-organized API module**: `frontend/src/api/client.js` -- base abstractions shared by all API modules.
