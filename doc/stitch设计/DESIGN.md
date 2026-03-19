# Design System Specification: Strategic Crisis Visualization

## 1. Overview & Creative North Star
**Creative North Star: "The Sentinel Lens"**

This design system is engineered for the high-stakes environment of natural disaster simulation and monitoring. We are moving away from the cluttered, "dashboard-heavy" aesthetics of the past toward a **Sentinel Lens**—a sophisticated, hyper-legible interface that prioritizes data hierarchy and cognitive calm. 

By utilizing intentional asymmetry and deep tonal layering, we create a signature experience that feels like a premium tactical command center. The system breaks the traditional "box-in-a-box" layout by using overlapping glass surfaces and high-contrast editorial typography to guide the eye toward critical alerts without inducing panic. It is authoritative, technical, and undeniably precise.

---

## 2. Colors & Surface Philosophy
The palette is rooted in the "Abyssal Shift"—a transition from the deepest navies to slate grays that mimics the depth of the ocean or the atmosphere before a storm.

### The Palette
- **Foundational Base:** `surface` (#041329) provides the "Dark Matter" background, ensuring zero eye strain.
- **Urgency Accents:** `primary` (#ffb77d / Warning Orange) and `secondary` (#ffb3b3 / Alert Red) are reserved strictly for active threats and high-priority interactions.
- **System Vitality:** `tertiary` (#00e471 / Safe Green) indicates stability and "All Clear" states.

### The "No-Line" Rule
Traditional 1px solid borders are strictly prohibited for sectioning. Boundaries must be defined through:
1.  **Background Shifts:** Distinguish the sidebar from the map view by moving from `surface` to `surface-container-low`.
2.  **Tonal Transitions:** Use `surface-container-highest` (#27354c) to pull a specific data module "forward" in space.

### Glass & Gradient Signature
To move beyond a flat "template" feel, floating telemetry panels must use **Glassmorphism**. Apply a backdrop-blur (12px–20px) to `surface-container-high` at 70% opacity. For main Action CTAs, use a subtle linear gradient from `primary` to `on-primary-container` at a 45-degree angle to provide a "machined" metallic finish.

---

## 3. Typography: The Editorial Command
Hierarchy is the difference between an organized evacuation and chaos. We pair the technical precision of **Space Grotesk** with the neutral clarity of **Inter**.

- **Display & Headlines (Space Grotesk):** Used for critical data readouts (e.g., Magnitude, Wind Speed, Time to Impact). Its wide apertures and geometric forms command authority.
- **Body & Labels (Inter):** Used for coordinates, descriptions, and system logs. It ensures maximum legibility even at the `label-sm` (0.6875rem) scale.

**Creative Rule:** Use `display-lg` for single, high-impact numbers. When a disaster is active, the headline should use `secondary` (Alert Red) to dominate the visual field, creating an immediate editorial focal point.

---

## 4. Elevation & Depth: Tonal Layering
We do not use shadows to create "pop"; we use light and transparency to create "meaning."

- **The Layering Principle:** Stack surfaces to denote importance. 
    - Base Level: `surface`
    - Navigation/Sidebars: `surface-container-low`
    - Content Cards: `surface-container-high`
    - Active Overlays: Glassmorphism (`surface-variant` with blur)
- **Ambient Shadows:** For floating modals, use a "Tinted Glow" instead of a drop shadow. Use the `surface-tint` (#ffb77d) at 4% opacity with a 32px blur to create a soft, atmospheric lift.
- **Ghost Borders:** If a boundary is required for accessibility on a map, use the `outline-variant` (#44474d) at 15% opacity. Never use 100% opaque lines.

---

## 5. Components

### Buttons: Tactical Trigger Points
- **Primary:** Sharp corners (`sm`: 0.125rem). Solid `primary` fill. Use `on-primary-fixed` for text to maintain a high-contrast, professional "indicator" look.
- **Secondary:** Transparent background with a `Ghost Border` (15% opacity). Text in `primary-fixed-dim`.
- **States:** On `hover`, the background should shift to a subtle 10% white overlay to simulate a "backlit" physical console button.

### Cards & Data Lists
- **The Zero-Divider Rule:** Forbid the use of horizontal rules (`<hr>`). Separate list items using vertical white space (`spacing-4` / 0.9rem) or by alternating background tones between `surface-container-low` and `surface-container-lowest`.
- **Data Chips:** Use `lg` (0.5rem) roundedness to contrast against the sharp-edged buttons. Chips should be monochromatic (`surface-bright` on `surface-container`) unless indicating an active warning status.

### Input Fields
- **Style:** Underline-only or subtle "Glass" wells. Use `surface-container-highest` for the input background. 
- **Focus State:** Instead of a thick border, use a 1px `primary` glow on the bottom edge only.

### Contextual Components: The "Pulse Indicator"
A unique component for this system: A small, breathing `tertiary` (Green) dot next to system time to indicate a live, heartbeat connection to satellite data.

---

## 6. Do’s and Don’ts

### Do:
- **Use Asymmetry:** Place critical simulation controls on the right (Spacing 12/16) and telemetry on the left (Spacing 8) to create a dynamic, non-centered "Command" layout.
- **Respect the Breathing Room:** Use `spacing-10` (2.25rem) between major functional blocks. High-stress environments require visual "oxygen."
- **Layer with Glass:** Use backdrop blurs for "Over-the-Map" controls to ensure the global monitoring visualization is never fully obscured.

### Don’t:
- **Don’t use "Pure Black":** Always use the specified `#041329`. Pure black (#000) kills the depth of the deep navy theme.
- **Don’t Use Rounded Corners for Primary Actions:** Keep buttons at `sm` (0.125rem) to maintain a high-tech, tactical aesthetic.
- **Don’t Clutter with Borders:** If you feel the need to add a border, try increasing the background contrast of the container instead.