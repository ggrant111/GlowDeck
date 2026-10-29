# GlowDeck v1.7 — Segment Studio

## What ships
- Discover per-controller WLED segment IDs, LED positions (shown 1-based inclusive), effects, color state, brightness, and availability.
- Native Segment Studio accessible from the TV and tablet navigation rail, or **More → Segment Studio** on phones.
- Create physical segments by splitting exactly one containing parent range without overwriting the adjacent LEDs; reject overlaps and insufficient `maxseg` capacity. Rename, resize, reverse or delete individual segments, with explicit confirmation and state readback.
- Create, rename and edit GlowDeck zones that collect physical segments from any number of WLED controllers. Zone commands target matching controller identities and segment IDs/ranges, not all segments.
- Independently assign power, brightness, effect, palette, speed, intensity and Fx/Bg/Cs colors to single segments or whole zones. Effect names map to each device's catalog; color edits keep existing effect.
- Temporary range identification shows amber LEDs on the selected physical ranges. Preview is nonpersistent, auto-stops after 12 seconds, and attempts to restore original WLED lighting state. Store recovery snapshots before applying preview; attempt recovery on next app launch when a preview is interrupted.
- Explicit WLED preset save (with segment bounds flag) for users who want to persist a layout; does **not** modify boot preset implicitly.

## Important behavior and limitations
- Use `GET /json` to read `state.seg`, `info.leds.count` and `info.leds.maxseg`, and `POST /json/state` with targeted `seg[].id` objects. WLED stores segment start as zero-based and stop exclusive.
- Identification uses WLED's `seg[].i` API, freezing affected WLED segments. The preview manager re-applies saved lighting settings and sends `frz:false` after preview. It cannot guarantee pixel-perfect animation-frame restoration.
- Physical LED ranges must lie within an existing active segment to be previewed using `seg[].i`; uncovered ranges require configuring the parent WLED segment first.
- Preview restoration is best effort if the controller disconnects, power is lost, or the app is killed. A backup is stored for retry the next time Segment Studio is opened.
- Cross-device zones dispatch commands per device, not synchronized animation frames.
- Zone members are intentionally rejected when segment ID/bounds or device identity changes, to avoid applying commands to the wrong LEDs. Re-select the member in the zone editor.
- On some firmware builds, deleting a WLED segment can renumber segment IDs, requiring zone memberships to be updated.
- WLED effect lists differ by firmware. An effect absent from any controller is reported unsupported for that segment.
- WLED presets/boot behavior: Saving a segment-layout preset does not automatically make it the boot state. Users manage boot preset separately through WLED.

## Fire tablet / Fire TV / phone smoke-test checklist
1. Install as an in-place update; verify previously saved devices, Favorites and GlowDeck scenes.
2. Open Segment Studio on each device and check live LED ranges.
3. Split a 400-LED parent into 1–100, 101–200 and 201–400; verify all reported ranges and effect continuity.
4. Preview Device 1 LEDs 101–200 and Device 2 LEDs 25–55; stop and verify prior effects resume.
5. Create one zone spanning those ranges; assign an effect and three color slots; verify all other segments remain unchanged.
6. Disconnect one controller and repeat; verify partial failure without incorrect control of other segments.
7. Change boundaries outside GlowDeck and verify the stale zone member is rejected.
8. Restart the app after interruption of Identify and verify best-effort preview recovery.
9. Reboot WLED after saving layout preset with bounds and explicitly restoring it; verify the saved segment ranges.
10. Verify D-pad focus on Fire TV, keyboard and step-button range input, and touch interactions on phone and tablet.
