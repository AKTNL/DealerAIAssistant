# Index Copy Inspired Monochrome UI Design

## Goal
Refactor the current black/white frontend direction so its page structure and layout rhythm are partially inspired by `index copy.html`, while keeping the approved monochrome editorial visual system.

## Design Intent
- Borrow structural strengths from `index copy.html`.
- Do not borrow its cyan/blue palette, gradients, glow, or glassmorphism.
- Keep the final UI white-dominant, black-led, and gray-layered.
- Make the product feel more like a focused analysis workspace than a demo prototype.

## Reference Boundaries

### Borrow
- The concentrated login entry flow.
- The thinner product header.
- The clearer left-sidebar grouping rhythm.
- The denser, more product-like chat workspace layout.
- The stronger separation between message content and input controls.

### Do Not Borrow
- Cyan or blue accents.
- Gradient bars, glow, or glass effects.
- Soft SaaS-style roundness and decorative hover effects.
- Demo-style status chips with colorful emphasis.

## Visual System
- Page canvas: `#f8f8f6`
- Main surface: `#ffffff`
- Primary text: `#111111`
- Secondary text: `#5f5f5f`
- Helper text: `#8a8a8a`
- Borders: `#d9d9d4`
- Stronger borders: `#c8c8c2`
- Keep shadows minimal and structural, never atmospheric.
- Keep radii tight, mostly `8px` to `12px`.
- Buttons remain flat monochrome.

## Page Behavior

### Login Page
- Move away from the current split editorial stage.
- Use a single focused entry area centered on the page.
- Keep the logo above the title, but with lower visual weight than the headline.
- Stack logo, title, short description, input, and action button in a clean vertical rhythm.
- Keep the login interaction narrow and direct, more like a controlled entry point than a landing card.
- Keep the footer note understated and low-contrast.

### Chat Page
- Keep the application shell with sidebar + main content.
- Make the header thinner and more product-like, closer to the reference structure.
- Make the sidebar read like a support rail for question groups and session context.
- Make the main chat area feel more like a document canvas than floating cards.
- Keep user and assistant messages distinct through grayscale surfaces, borders, and layout weight.
- Keep the composer fixed as a clear editing control bar at the bottom of the workspace.

## Component Rules
- Primary button: black background, white text.
- Secondary button: white background, black text, gray border.
- Inputs: white background, thin gray border, subtle dark focus ring.
- Sidebar sections: clear grouping, compact spacing, minimal ornament.
- Top header: thin, restrained, horizontal product identity.
- Message blocks: content-first, less pill-like, more document-like.
- User message blocks may carry a stronger gray surface than assistant blocks.
- Links, chips, and metadata stay grayscale unless the user later approves a dedicated accent.

## Implementation Scope
- Rework `LoginView.vue` and `frontend/src/style.css` so the login page becomes a focused single-entry composition instead of a split two-panel stage.
- Rework the chat workspace spacing and shell styling in `frontend/src/style.css` to better match the reference structure.
- Keep the current Vue component decomposition intact unless a specific file becomes too awkward to style cleanly.
- Reuse the existing test/tooling foundation already added in this branch.
- Update tests where the layout hooks or login structure meaningfully change.

## Non-Goals
- No return to colored branding.
- No clone of `index copy.html`.
- No backend or data-flow changes.
- No unrelated refactor outside the frontend shell and presentation layer.

## Risks
- Borrowing too much from the reference can make the product feel like a demo instead of a tool.
- Borrowing too little can leave the current UI feeling like a recolor instead of a structural improvement.
- Login-page simplification can accidentally reduce visual hierarchy if spacing and type scale are not handled carefully.

## Validation
- The login page reads as a focused single-entry screen.
- The chat page clearly resembles a product workspace more than a floating-card landing layout.
- The final UI remains monochrome and avoids blue/cyan accents.
- Sidebar, topbar, message area, and composer feel structurally closer to `index copy.html`.
- The UI remains readable and stable across desktop and mobile breakpoints.
