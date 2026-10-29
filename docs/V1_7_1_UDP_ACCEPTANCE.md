# GlowDeck v1.7.1 UDP hardware acceptance

## Preconditions
- WLED devices reachable over LAN from the tablet/Fire TV/phone; normal `GET /json` working.
- WLED network settings accept realtime UDP / DDP; default realtime port 21324, DDP 4048. Check router client isolation.
- Use noncritical lighting and record WLED presets before testing.

## Test matrix
1. Select one segment LEDs 101–200 on WLED A and show Solid / Chase / Ends. Expect only the requested coordinates highlighted *within realtime takeover*; outside LEDs may appear black or pause. At the end, verify WLED returns to its prior effect.
2. Select a zone with WLED A 101–200 and WLED B 25–55. Check both show identification, then Stop. Verify both recover. Missing controllers must be reported.
3. Enter a temporary subrange inside a segment in the range editor. Select UDP preview and verify preview does not save or split the physical segment.
4. Test DDP on one controller with >480 LEDs (if available), check correct ordering and visible continuous gradient on entire strip. DDP will override all segments while active.
5. Lock the tablet or navigate Home during streaming. Confirm stream stops and original state is restored (can take several seconds).
6. Unplug WLED while streaming and then reconnect. Use UDP streaming status → Restore pending lighting; verify saved backup remains until successful recovery.
7. Start another realtime source before starting a GlowDeck test. Expected: GlowDeck refuses to steal the stream where WLED reports `info.live`.
8. Test controllers on different WLED firmware generations; test Fire OS D-pad navigation and touch UX, including portrait/landscape.
9. Check status language: requests say **packets sent**, not **confirmed**; visual confirmation remains manual because UDP has no ACK.

## Command reference
- WLED realtime DNRGB: UDP/21324, byte 0=4, byte 1=2, bytes 2/3=16-bit LED index, followed by RGB triplets.
- DDP RGB24: UDP/4048, 10 byte v1 header, 1440 byte maximum RGB payload, last packet PUSH.
- WLED JSON API: HTTP `GET /json` snapshots and POST `/json/state` to restore after realtime mode releases.

Passing Gradle CI and JVM packet tests is not a substitute for the physical multi-controller checks above.
