# v1.6.2 physical device acceptance checklist

1. Start GlowDeck on an existing Fire tablet after in-place upgrade. Home screen must remain open (v1.6.1 fix remains).
2. Press Fireplace **On**. Banner must appear immediately (Queued), update to **Request sent – checking WLED state**, and then show **Confirmed** only after `GET /json` reports `on:true`.
3. Turn the light off and confirm it reports `on:false`. Change brightness and color: banner must cover each change.
4. In Favorites, activate explicit power, brightness, color and a saved preset. Confirm reported WLED state before success appears.
5. Activate a scene across 2+ WLED devices. Confirm each controller; unplug one to simulate offline condition and verify the banner displays a partial result with a failure.
6. Simulate a device that returns HTTP 200 but does not change state (or externally changes it back). GlowDeck must show **not confirmed**, not a success message. No automatic second POST.
7. Save a new WLED preset. Confirmation must depend on its presence and name in `/presets.json`.
8. Press actions rapidly. The newest action's progress must not be overwritten by older queued action results.
9. Repeat on Fire TV with the remote. Banner must not intercept or move D-pad focus. Repeat on phone and tablet in different orientations.
10. Confirm existing saved lights, groups, Favorites and scenes persist; and verify logs have no new `AndroidRuntime FATAL EXCEPTION`.

**Terminology:** **Confirmed** refers to reported WLED JSON state, not an optical sensor measuring the LEDs. WLED fade transitions may require more time than the confirmation window; in this situation GlowDeck displays **not confirmed**, which is safer than false success.
