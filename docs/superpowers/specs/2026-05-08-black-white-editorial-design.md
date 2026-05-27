# Black/White Editorial UI Design

## Goal
Convert the frontend from a soft green glassmorphism style to a white-dominant, black/gray editorial style.

## Principles
- White background, black text, gray hierarchy only.
- No new accent colors.
- Use spacing, typography, borders, and contrast for structure.
- Keep the UI sharp, restrained, and professional, not heavy or dashboard-like.

## Visual System
- Background: `#f8f8f6` for the page canvas.
- Main surface: `#ffffff`.
- Main text: `#111111`.
- Secondary text: `#5f5f5f`.
- Helper text: `#8a8a8a`.
- Borders: `#d9d9d4`.
- Dividers: `#e8e8e3`.
- Shadows: minimal; prefer thin borders.
- Corner radius: mostly `8px` to `12px`; avoid pill-heavy styling.
- Buttons: flat, monochrome, no gradients, no glow, no glass.

## Page Behavior

### Login Page
- Treat the login page like a cover page.
- Make the headline visually dominant.
- Keep the logo present but secondary.
- Isolate the login form as a narrow, clean block.
- Reduce the feeling of a centered floating card; rely more on typography and composition.
- Use white surfaces, fine borders, and strong spacing instead of decorative background effects.

### Chat Page
- Treat the chat page like a working editorial desk.
- Keep the left sidebar, but reduce ornamentation.
- Keep the top bar thin and functional.
- Distinguish sender states through layout and border treatment, not color.
- Assistant messages should feel more neutral and content-led.
- User messages may use a slightly darker gray surface or stronger border, but stay inside grayscale.
- Avoid color-based message differentiation.
- Make the composer feel like a clean editing surface.

## Component Rules
- Primary button: black background, white text.
- Secondary button: white background, black text, gray border.
- Disabled state: lower contrast only.
- Inputs: white background, thin gray border, subtle gray focus state.
- Sidebar: slightly cooler off-white than the main canvas.
- Message cards: white or very light gray only.
- Avatars: monochrome, simplified, lower visual weight than today.
- Titles: strong black.
- Body text: dark gray.
- Helper text: medium gray.
- Status text: grayscale only.

## Implementation Scope
- Update global tokens in `frontend/src/style.css`.
- Keep the existing page structure in `LoginView.vue` and `ChatView.vue`.
- Restyle shared UI pieces such as top navigation, sidebar, chat messages, and composer before changing layout structure.
- Remove visible green accents, gradients, and glassmorphism effects from the main user flows.

## Non-Goals
- No dark mode redesign.
- No new brand color system.
- No layout refactor beyond what this visual shift requires.

## Risks
- Excessive white space may make dense chat content feel empty.
- Too many borders may make the UI feel brittle.
- If gray values are too close together, hierarchy will collapse.

## Validation
- The login page reads as a restrained cover page.
- The chat page feels like a professional analysis workspace.
- No green or other accent colors remain in the visible UI.
- The UI remains readable on desktop and mobile.
- Button hierarchy is obvious without using color.
- User and assistant messages remain easy to distinguish at a glance.
