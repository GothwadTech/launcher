# The 100-Feature Customization Roadmap

Status key: ✅ shipped · 🟡 partial (foundation shipped, full version planned) · ⏳ planned.
Everything below is native Views + RecyclerView (AGENTS.md: no Compose), minSdk 21,
and must stay inside the 2 GB-TV performance budget in `docs/PERFORMANCE.md`.

## A. Themes & Colour Engine
| # | Feature | Status |
|---|---------|--------|
| 1 | 10 full preset themes | ✅ `ui/Theme.kt` THEMES + Theme & Style page |
| 2 | Custom accent colour (10 picks) | ✅ accent row; hex input ⏳ |
| 3 | Per-element colour control | 🟡 theme surface/accent/text ship; per-element editor ⏳ |
| 4 | Light/Dark/Auto modes | 🟡 dark themes ship; sunset auto ⏳ |
| 5 | Wallpaper-palette accent | ✅ sampled average colour → accent |
| 6 | Live theme preview | ✅ theme tiles render real colours; home re-renders on pick |
| 7 | Theme import/export | ✅ via settings backup JSON |
| 8 | Scheduled themes | ⏳ (config field ready in backup schema pattern) |
| 9 | Focus glow/outline/fill/scale | ✅ 4 effects + scale 100–135% |
| 10 | Transparency/blur controls | 🟡 status-bar glass toggle ships; blur slider ⏳ |

## B. Wallpaper System
| # | Feature | Status |
|---|---------|--------|
| 11 | 20+ built-in wallpapers | 🟡 10 shipped, registry-driven (add = one line) |
| 12 | Wallpaper categories | ⏳ |
| 13 | Blur + dim sliders | 🟡 5 scrim modes ship; blur ⏳ |
| 14 | Parallax wallpaper | ⏳ |
| 15 | Auto-change shuffle mode | 🟡 ordered rotation ✅; shuffle ⏳ |
| 16 | Auto-change triggers | ✅ interval (15m/1h/6h/daily); on-boot/unlock ⏳ |
| 17 | Photo folder rotation | 🟡 single custom photo + never auto-replaced; folder ⏳ |
| 18 | Online wallpaper gallery | ⏳ |
| 19 | Live/video wallpaper | ⏳ (auto-off on Lite profile when it lands) |
| 20 | Wallpaper effects (duotone/vignette) | ⏳ |

## C. Layout & Grid
| # | Feature | Status |
|---|---------|--------|
| 21 | Classic grid, 3–8 columns | ✅ |
| 22 | Horizontal pages | ⏳ |
| 23 | Category rows (Netflix style) | ⏳ |
| 24 | List/row mode | ⏳ |
| 25 | Compact grid | ✅ layout mode 1 |
| 26 | Large-tile mode | ✅ layout mode 2 |
| 27 | Focus-expand grid | ✅ scale effect 100–140% |
| 28 | Split layout (sidebar + grid) | ⏳ |
| 29 | Magazine mixed sizes | ⏳ |
| 30 | A–Z alphabet rail | ⏳ |

## D. Icons
| # | Feature | Status |
|---|---------|--------|
| 31 | Icon/app size slider | ✅ 60–160% (4 steps) |
| 32 | Icon shapes | 🟡 corner-radius system ships; shape set ⏳ |
| 33 | Icon-pack support | ⏳ |
| 34 | Per-app custom icon | ⏳ |
| 35 | Label show/size | ✅ show toggle + 3 sizes |
| 36 | Icon background plates | 🟡 theme surface fill ships; plate styles ⏳ |
| 37 | Notification badges on tiles | ⏳ (notification bridge exists) |
| 38 | Generated fallback icons | 🟡 deterministic tile colours ship; letter glyphs ⏳ |
| 39 | Icon focus animation | ✅ scale/glow/outline/fill |
| 40 | Monochrome icon mode | ⏳ |

## E. Header / Status Bar
| # | Feature | Status |
|---|---------|--------|
| 41 | Clock styles | ✅ date+time / time-only / greeting+time |
| 42 | Header glass control | ✅ |
| 43 | Time-of-day greeting | ✅ |
| 44 | Weather widget | ⏳ (needs an opt-in data source decision) |
| 45 | Date formats | ✅ 4 (incl. hidden) |
| 46 | Status-bar position | 🟡 show/hide ships; bottom/floating ⏳ |
| 47 | Status icon toggles | 🟡 via Quick Dashboard; per-icon ⏳ |
| 48 | Remote battery widget | 🟡 Bluetooth status flow ships; header chip ⏳ |
| 49 | Now-playing bar | 🟡 media tracking exists; header UI ⏳ |
| 50 | Quick-settings drop-down | ✅ Quick Dashboard (clock cluster) |

## F. Animations
| # | Feature | Status |
|---|---------|--------|
| 51 | App-open animation | ✅ none/fade/zoom/slide |
| 52 | Grid scroll animation | 🟡 focus animations ship; scroll depth ⏳ |
| 53 | Wallpaper transition | 🟡 instant swap; crossfade ⏳ |
| 54 | Settings page transition | 🟡 focus hand-off ships; slide anim ⏳ |
| 55 | Animation speed control | ✅ slow/normal/fast |
| 56 | Boot animation styles | ⏳ |
| 57 | Page-turn effects | ⏳ (with #22) |
| 58 | Reduced-motion mode | ✅ |
| 59 | Ambient idle gradient shift | ⏳ |
| 60 | Click micro-interactions | 🟡 state-list pressed states ship; ripple styles ⏳ |

## G. Remote & Navigation
| # | Feature | Status |
|---|---------|--------|
| 61 | Key-map editor | ✅ (pre-existing) + per-app maps ⏳ |
| 62 | Focus memory | ⏳ |
| 63 | Long-press app menu | ✅ launch/info/uninstall/favourite/hide/lock |
| 64 | Quick-launch favourite row | ✅ favourites row |
| 65 | Colour-button shortcuts | 🟡 button mapping ships (pre-existing) |
| 66 | Double-press Home action | ⏳ |
| 67 | Mouse/keyboard polish | 🟡 hover states ship; right-click menu ⏳ |
| 68 | Voice launch | 🟡 voice search ships (pre-existing); direct launch ⏳ |
| 69 | Touch gestures | ⏳ |
| 70 | Focus sound (opt-in) | ⏳ |

## H. TV Experience
| # | Feature | Status |
|---|---------|--------|
| 71 | Favourites row | ✅ |
| 72 | Recents row | ✅ (launch tracker) |
| 73 | Auto app categories | ⏳ |
| 74 | Screensaver mode | ⏳ |
| 75 | Kids mode | ⏳ (app-lock + hidden vault are the foundation) |
| 76 | Guest mode | ⏳ |
| 77 | Usage stats page | 🟡 launch tracker data exists; page ⏳ |
| 78 | Multi-language UI | 🟡 all new UI text is in `strings.xml`; Hindi pass ⏳ |
| 79 | Deep-link shortcuts | ⏳ |
| 80 | Wizard theme/layout picker | ⏳ |

## I. Security & Privacy
| # | Feature | Status |
|---|---------|--------|
| 81 | Biometric unlock | ⏳ (PIN lock ships) |
| 82 | Pattern lock | ⏳ |
| 83 | Auto-lock timer | ⏳ |
| 84 | Break-in attempt log | ⏳ |
| 85 | Privacy dashboard | ⏳ |

## J. Smart / Power / System
| # | Feature | Status |
|---|---------|--------|
| 86 | Performance profiles | ✅ Lite/Balanced/Full (motion + transitions) |
| 87 | Memory widget | 🟡 trim engine ships; widget ⏳ |
| 88 | One-tap clean | 🟡 aggressive-trim toggle ships; button ⏳ |
| 89 | Network speed meter | ⏳ |
| 90 | Night light | ✅ warm overlay + strength |
| 91 | App sorting rules | ✅ name/install/updated/manual |
| 92 | Backup & restore | ✅ JSON export/import |
| 93 | Theme packs via releases | ⏳ (backup JSON is the pack format) |
| 94 | Widget slots | ⏳ |
| 95 | Crash/self-heal dashboard | 🟡 safe-mode guard ships; viewer ⏳ |
| 96 | Update channel stable/beta | ⏳ (self-updater ships) |
| 97 | Developer mode (FPS/bounds) | ⏳ |
| 98 | D-pad-first Settings | ✅ focus hand-off + visible focus everywhere |
| 99 | Zero-jank budget | ✅ CI builds/tests/lint; bitmap budget rules in PERFORMANCE.md |
| 100 | One-click Lite mode | ✅ Lite performance profile + reduced motion |
