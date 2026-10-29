# GlowDeck v1.6.3 effect colors: acceptance tests

1. On Fire Tablet/phone, select a multi-color WLED effect (e.g. an effect using two or three color slots). Confirm that Fx/Bg/Cs selection matches WLED's reported `/json/fxdata` metadata, where available.
2. Change Fx using quick swatches. Confirm effect remains selected, Bg/Cs stay unchanged, command status appears, and device readback confirms the update.
3. Open Bg picker and change custom RGB via sliders; verify Fx/Cs are unchanged.
4. Edit Cs where supported, including on a WLED RGBW strip: verify the white channel value of each unchanged slot is preserved.
5. For effects with zero exposed colors, controls should explain this and not misleadingly apply colors.
6. Test another device running WLED before 0.14 or lacking `/json/fxdata`: all three slots should remain available via fallback, with no app crash.
7. Test TV D-pad: choose slot with OK, adjust in the RGB dialog, and verify focus returns correctly.
8. For a saved GlowDeck scene, edit its Bg color and verify its stored `fx`/`fxName` and other two color slots are unchanged. Play the scene to see the color change live.
9. Test All Devices and groups with mixed effect capability: unsupported targets are skipped, others are updated with partial result feedback.
10. Test rapid edits to primary and secondary and verify both values persist after WLED state refresh.
11. Confirm v1.6.2 favorites, scene presets, and Fire Tablet startup fix still work.
12. Verify in-place upgrade preserves all saved devices, Favorites, and scenes; match signing certificate to retain configuration.

Note: WLED palette effects may not render manual segment colors; this is normal if the effect uses a palette for color generation.
