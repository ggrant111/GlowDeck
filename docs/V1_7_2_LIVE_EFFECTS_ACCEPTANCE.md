# v1.7.2 Live Effects hardware acceptance checklist

- [ ] Install APK in-place on Fire tablet with same debug signing key; devices, zones, scenes and Favorites survive.
- [ ] Navigate Segment Studio → any physical segment → Live Effects. Select each of five effects; start, observe animated LEDs, stop and restore.
- [ ] Repeat on a multi-device zone (two controllers), ensuring traffic reaches only intended IPs and ranges where supported by WLED realtime mode.
- [ ] Edit speed, brightness, primary and secondary color while streaming; changes appear without switching to WLED's Solid effect.
- [ ] Confirm each session ends after 90s, and stops on Close, Back, Home, app background, or leaving Segment Studio.
- [ ] Confirm original JSON state resumes after the WLED realtime timeout; verify recovery after Wi-Fi loss and app restart.
- [ ] Confirm app refuses to take over controllers that already report `info.live=true`.
- [ ] Confirm configured realtime port from `info.udpport` works on a non-default WLED port.
- [ ] Confirm no UI crash on older Fire OS (SeekBar compatibility) and TV D-pad operation.
- [ ] Confirm warning that unselected LEDs on same device may go dark or pause in WLED realtime mode.
- [ ] Test WLED segment ID and boundary changes during the session (stop/recovery must not overwrite a new layout).
- [ ] Re-check v1.7.1 12s DNRGB identifier and DDP full-strip test.

UDP packets are unacknowledged. Tests on real devices are mandatory before calling pixel-level output verified.
