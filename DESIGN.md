# Takit Design Guide

## Visual Direction
- Takit uses a warm, friendly utility style: soft off-white backgrounds, rounded white cards, lavender/mint/pink accents, and dark charcoal text.
- Screens should feel lightweight and scannable, with key actions grouped into card sections rather than long unstructured forms.
- Folder rows should look calm and compact, while favorite folders may use pastel cards for quick recognition.

## Tokens
- Background: `#FFF9F2`
- Surface: `#FFFFFF`
- Text primary: `#202033`
- Text secondary: `#7A7687`
- Lavender primary: `#9B6BE8`
- Lavender soft: `#F2ECFF`
- Mint soft: `#DDF6F2`
- Blue soft: `#E7EEFF`
- Pink soft: `#FFE8F2`
- Yellow soft: `#FFF4CA`
- Divider: `#EFEAF2`
- Card radius: 14-18dp
- Button radius: 12-16dp
- Page padding: 20dp horizontal, 24-28dp vertical
- Button horizontal padding: 14-16dp for primary/secondary actions, 10-12dp for compact chips.
- Button groups need visible breathing room: at least 8dp around segmented controls and 12dp between sliders and action rows.
- Pill controls should use Takit's own TextView-based control style, not Android's default `Button`, to avoid clipped rounded corners and pressed-state vertical jumps.
- Text inputs need at least 16dp horizontal padding so placeholder text never touches rounded edges.

## Components
- Current folder card: icon tile at left, small label, bold folder name, path below, chevron at right.
- Quick actions: two equal-width pastel buttons with a small icon and label.
- Folder add panel: dashed outline container with two secondary actions for importing/syncing and direct name entry.
- Favorite folders: pastel compact cards, max three per row, with folder name and relative path. Do not show item counts by default.
- All gallery folders: white list card with compact rows, folder name, relative path, and select/favorite actions. Do not show item counts by default.
- Sorting controls: segmented chips with clear spacing. The active sort and direction use a filled lavender state, white text, and a leading check mark; inactive chips stay white with a visible border and dark text.
- Bottom navigation: fixed rounded pill with Home, Folder, Settings tabs. Tab hit areas should be wide and at least 44dp tall.
- Overlay mini window: centered near the top, white rounded card, same action colors as the main UI. It has no close icon; tapping outside the panel closes it. It includes camera, screenshot, app-open, and favorite-folder switching actions.
- Bubble dismiss target: bottom-center circular lavender area shown only while dragging. The animated circle must sit inside a larger transparent container so selected-scale animation is never clipped by square window bounds.

## Behavior Rules
- Do not place decorative controls that look interactive. Header icons must either perform an action or be removed.
- Gallery folder browsing defaults to `DCIM/` only. `Pictures/` is not part of the default folder list.
- Folder lists must support search by the text after `DCIM/`, sorting by name or latest modification, and ascending/descending direction.
- Choosing latest modification sort defaults to newest-first. Folders without modification metadata stay behind folders with real metadata in both directions.
- Long folder lists are paginated in groups of 8.
- Favorite toggles must preserve the current folder tab query, sort, direction, page, and scroll position.
- Bubble actions should never expand off-screen. Clicking the bubble opens the centered mini window; closing the mini window returns the bubble to the position it was clicked from. Dragging to the bottom dismiss target turns the bubble off.
- Bubble folder switching shows favorite folders only. Do not include search or the full gallery list in the overlay mini window.
- Screenshot capture from the bubble should reuse an active MediaProjection session and avoid opening the Takit app. If no session is active, Android's required consent screen is shown once, then the app task is moved back before capture.
