# Frontend Blue Gray UI Refresh Design

## Context

The improvement plan leaves one remaining item: improve the frontend visual design by replacing the overly plain black UI with a more comfortable, professional color system. The first three backend/reporting items have already been completed.

The current frontend is a Vue/Vite app with most global styling in `frontend/src/style.css`. It already uses a light editorial layout, but many core visual states still depend on hard-coded near-black values such as `#111111` and `#1a1a1a` for primary buttons, user chat bubbles, focus states, assistant accent lines, loading indicators, and login page accents.

## Goals

- Replace pure-black primary visual language with a professional blue-gray theme.
- Keep the existing application structure and workflows unchanged.
- Improve visual hierarchy across login, sidebar, chat, input, table, and Mermaid chart surfaces.
- Centralize theme values behind semantic CSS tokens so future UI changes do not require scattered hard-coded colors.
- Preserve existing responsive behavior and current component aliases such as editorial topbar/composer classes.

## Non-Goals

- No chat workflow, API, model-setting, authentication, or data logic changes.
- No large layout redesign or new navigation model.
- No new UI library or icon system.
- No marketing-style landing page.
- No broad rewrite of component templates unless a style hook is missing.

## Visual Direction

Use a restrained enterprise analytics palette:

- Primary: deep navy / slate blue for decisive actions and user messages.
- Hover/active: slightly brighter steel blue for interactive feedback.
- Page and surface backgrounds: cool gray-white and light blue-gray layers.
- Borders: cool gray-blue borders with clear but low-contrast separation.
- Informational accent: muted cyan/blue for status and supporting UI.
- Danger: keep a clear red tone, but tune it to sit inside the cooler palette.

The interface should feel like a focused business analysis tool: quiet, structured, readable, and more refined than the current black-and-white editorial theme.

## Palette Reference

Use these values as the implementation baseline. Small adjustments are allowed only if they improve contrast or readability during verification.

| Purpose | Token | Value |
| --- | --- | --- |
| Page background | `--page-bg` | `#f4f7fb` |
| Deeper page layer | `--page-bg-deep` | `#e8eef5` |
| Base surface | `--surface` | `#ffffff` |
| Strong surface | `--surface-strong` | `#f8fbff` |
| Soft surface | `--surface-soft` | `#eef4fa` |
| Muted surface | `--surface-muted` | `#e2ebf3` |
| Tinted surface | `--surface-tinted` | `#e9f2fb` |
| Sidebar background | `--sidebar-bg` | `#eaf1f8` |
| Soft border | `--border-soft` | `#c9d6e3` |
| Strong border | `--border-strong` | `#aebfce` |
| Focus border | `--border-focus` | `#3f6f9f` |
| Primary brand | `--brand-primary` | `#17324d` |
| Primary hover | `--brand-primary-hover` | `#22527a` |
| Primary soft | `--brand-primary-soft` | `#dceaf6` |
| Info accent | `--accent-info` | `#2f7da8` |
| Main text | `--text-main` | `#102033` |
| Strong text | `--text-strong` | `#0b1728` |
| Muted text | `--text-muted` | `#516173` |
| Helper text | `--text-helper` | `#748397` |
| Danger | `--danger` | `#a43f3f` |

Do not delete neutral tokens that are still used by the layout. Retune them to this blue-gray system and keep them available for existing selectors. The "legacy grayscale expectations" removal applies only to old tests that require the previous black/gray values, not to the neutral token API itself.

## Theme Tokens

Update `:root` in `frontend/src/style.css` to include semantic values such as:

- `--brand-primary`
- `--brand-primary-hover`
- `--brand-primary-soft`
- `--accent-info`
- `--surface-tinted`
- `--border-focus`
- `--focus-ring`

Existing neutral tokens such as `--page-bg`, `--surface`, `--surface-soft`, `--text-main`, `--text-muted`, and `--border-soft` should be retuned to the blue-gray palette rather than removed. Component rules should prefer these tokens over hard-coded black values.

The implementation should ensure every newly referenced token exists in `:root` before a component rule uses it. This prevents missing-variable fallbacks and keeps theme changes auditable in one place.

## Component Design

### Login

Keep the centered glass card. Retune the page background, watermark glow, top accent bar, input focus, and submit button to the new blue-gray palette. The login screen should still feel premium and focused, but less stark than the current black gradient.

### Sidebar

Keep the existing collapsible sidebar and module structure. Use light tinted surfaces for module cards, clearer border layering, and blue-gray hover/pending states. The primary "new chat" action should use the brand primary token instead of pure black.

### Top Navigation

Keep the current compact operational header. Use the blue-gray status dot and tinted icon hover states. Avoid making the header decorative; it should remain utility-focused.

### Chat Messages

Assistant messages remain white or near-white for readability. Replace the assistant left accent line with the brand primary token. Replace user message black bubbles with deep navy/slate bubbles. Markdown links, list markers, thinking dots, and loading spinners should use the brand/accent tokens.

### Composer

Keep the current input area structure. Retune focus, send, stop, toast, and error affordances so the active state is blue-gray instead of black. Do not change input behavior.

### Tables And Mermaid

Use the new surface and border tokens for markdown tables and Mermaid blocks. Keep multi-color comparison bars, but make container, toolbar, skeleton, and fallback states consistent with the blue-gray theme.

Mermaid chart readability must be checked in the rendered app, not only through CSS text assertions. The toolbar, chart background, labels, borders, fallback state, and skeleton should remain legible against the new surface colors.

## Testing

Update `frontend/src/__tests__/styleTokens.spec.js` before production styling changes. The test should assert:

- The approved blue-gray token values exist in `:root`, including primary, hover, soft, focus, info, surface, border, and text tokens.
- Existing neutral token names remain present when they are still used by the CSS.
- The legacy grayscale-only value expectations are removed, while the neutral token API remains intact.
- Primary buttons, button hover states, composer focus, text input focus, user message bubbles, assistant accent lines, login submit button, pending/sidebar hover states, and loading indicators use the new theme tokens rather than pure black as their main color.
- Editorial alias class grouping and responsive rules remain intact.
- Mermaid surfaces still render as light surfaces with multi-color comparison bars.

Keep the existing responsive assertions for the topbar, composer, and mobile radius/layout rules. Add or update responsive checks only where a refreshed visual rule could affect layout.

After implementation, run the focused style/component tests and `npm run build` from `frontend`. Also perform a rendered visual check of login, sidebar hover/pending states, chat messages, composer focus, and Mermaid chart surfaces.

## Acceptance Criteria

- The frontend no longer relies on pure black for primary actions, user chat bubbles, focus states, assistant accents, and main loading indicators.
- Login, sidebar, chat, composer, markdown table, and Mermaid chart surfaces share the same professional blue-gray visual system.
- Hover, active, disabled, pending, focus-visible, loading, error, and success states remain visible and distinguishable.
- Existing workflows and component structure remain unchanged.
- Existing responsive layout behavior is preserved.
- Updated tests and frontend build pass.
- Rendered Mermaid chart containers and toolbars remain readable with the refreshed palette.
